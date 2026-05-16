import { useEffect, useMemo, useState } from 'react'
import type { User } from '@supabase/supabase-js'
import { hasSupabaseConfig, supabase } from './supabase'

type TodayPostcard = {
  delivery_id: string
  postcard_id: string
  content: string
  image_path: string | null
  image_content_type: string | null
  image_byte_size: number | null
  delivered_date: string
  delivered_at: string
}

type AuthoredPostcard = {
  id: string
  content: string
  image_path: string | null
  status: 'pending' | 'approved' | 'rejected' | 'hidden'
  created_at: string
}

type ReceivedDelivery = {
  id: string
  postcard_id: string
  delivered_date: string
  created_at: string
  postcard: {
    id: string
    content: string
    image_path: string | null
    status: 'approved'
  } | null
}

type UserAction = {
  postcard_id: string
  action_type: 'not_interested' | 'hidden'
}

type AdminPostcard = AuthoredPostcard & {
  moderated_at: string | null
  moderation_note: string | null
}

type AdminReport = {
  id: string
  postcard_id: string
  delivery_id: string
  reason: string
  status: 'open' | 'reviewed' | 'dismissed' | 'actioned'
  created_at: string
  handler_note: string | null
  postcard: {
    id: string
    content: string
    status: string
  } | null
}

type RequestState =
  | { type: 'idle' }
  | { type: 'loading'; message: string }
  | { type: 'success'; message: string }
  | { type: 'error'; message: string }

type Tab = 'write' | 'history' | 'account' | 'admin'

const emptyState: RequestState = { type: 'idle' }
const allowedImageTypes = ['image/jpeg', 'image/png', 'image/webp'] as const
const imageAccept = allowedImageTypes.join(',')
const maxImageByteSize = 10 * 1024 * 1024
const signedUrlExpiresInSeconds = 60 * 60

type UploadedImage = {
  path: string
  contentType: string
  byteSize: number
}

export function App() {
  const [user, setUser] = useState<User | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>('write')
  const [content, setContent] = useState('')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [today, setToday] = useState<TodayPostcard | null>(null)
  const [todayImageUrl, setTodayImageUrl] = useState<string | null>(null)
  const [authoredPostcards, setAuthoredPostcards] = useState<AuthoredPostcard[]>([])
  const [receivedDeliveries, setReceivedDeliveries] = useState<ReceivedDelivery[]>([])
  const [userActions, setUserActions] = useState<UserAction[]>([])
  const [isAdmin, setIsAdmin] = useState(false)
  const [pendingPostcards, setPendingPostcards] = useState<AdminPostcard[]>([])
  const [reports, setReports] = useState<AdminReport[]>([])
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [reportReason, setReportReason] = useState('')
  const [historyReportReasons, setHistoryReportReasons] = useState<Record<string, string>>({})
  const [state, setState] = useState<RequestState>(emptyState)

  const imagePreviewUrl = useMemo(() => {
    if (!imageFile) return null
    return URL.createObjectURL(imageFile)
  }, [imageFile])

  const visibleReceivedDeliveries = useMemo(() => {
    const hiddenPostcards = new Set(
      userActions.filter((action) => action.action_type === 'hidden').map((action) => action.postcard_id),
    )
    return receivedDeliveries.filter((delivery) => !hiddenPostcards.has(delivery.postcard_id))
  }, [receivedDeliveries, userActions])

  useEffect(() => {
    if (!hasSupabaseConfig || !supabase) {
      return
    }

    supabase.auth.getUser().then(({ data }) => {
      setUser(data.user)
    })

    const { data: listener } = supabase.auth.onAuthStateChange((_event, session) => {
      setUser(session?.user ?? null)
    })

    return () => {
      listener.subscription.unsubscribe()
    }
  }, [])

  useEffect(() => {
    if (!user) {
      setAuthoredPostcards([])
      setReceivedDeliveries([])
      setUserActions([])
      setIsAdmin(false)
      setPendingPostcards([])
      setReports([])
      return
    }

    void refreshHistory()
    void refreshAdmin()
  }, [user])

  useEffect(() => {
    return () => {
      if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl)
    }
  }, [imagePreviewUrl])

  async function signInAnonymously() {
    if (!supabase) return

    setState({ type: 'loading', message: '익명 우체국 창구를 여는 중입니다.' })
    const { error } = await supabase.auth.signInAnonymously()
    setState(error ? { type: 'error', message: error.message } : emptyState)
  }

  async function signOut() {
    if (!supabase) return
    await supabase.auth.signOut()
    setToday(null)
    setTodayImageUrl(null)
    setState({ type: 'success', message: '조용히 문을 닫았습니다.' })
  }

  async function sendMagicLink() {
    if (!supabase || !email.trim()) return

    setState({ type: 'loading', message: '메일함으로 입장 편지를 보내는 중입니다.' })
    const { error } = await supabase.auth.signInWithOtp({
      email: email.trim(),
      options: { emailRedirectTo: window.location.origin },
    })
    setState(error ? { type: 'error', message: error.message } : { type: 'success', message: '메일함을 확인해주세요.' })
  }

  async function signInWithPassword() {
    if (!supabase || !email.trim() || password.length < 6) return

    setState({ type: 'loading', message: '계정을 확인하는 중입니다.' })
    const { error } = await supabase.auth.signInWithPassword({
      email: email.trim(),
      password,
    })
    setState(error ? { type: 'error', message: error.message } : emptyState)
  }

  async function signUpWithPassword() {
    if (!supabase || !email.trim() || password.length < 6) return

    setState({ type: 'loading', message: '새 우체국 계정을 만드는 중입니다.' })
    const { error } = await supabase.auth.signUp({
      email: email.trim(),
      password,
      options: { emailRedirectTo: window.location.origin },
    })
    setState(error ? { type: 'error', message: error.message } : { type: 'success', message: '가입 확인 메일을 보냈습니다.' })
  }

  async function signInWithProvider(provider: 'google' | 'kakao') {
    if (!supabase) return

    setState({ type: 'loading', message: '소셜 우체국 창구로 이동합니다.' })
    const { error } = await supabase.auth.signInWithOAuth({
      provider,
      options: { redirectTo: window.location.origin },
    })
    if (error) setState({ type: 'error', message: error.message })
  }

  function validateImageFile(file: File): string | null {
    if (!allowedImageTypes.includes(file.type as (typeof allowedImageTypes)[number])) {
      return 'JPEG, PNG, WebP 사진만 보낼 수 있습니다.'
    }

    if (file.size > maxImageByteSize) {
      return '사진은 10MB 이하만 보낼 수 있습니다.'
    }

    return null
  }

  function getImageExtension(contentType: string) {
    if (contentType === 'image/png') return 'png'
    if (contentType === 'image/webp') return 'webp'
    return 'jpg'
  }

  function handleImageChange(file: File | null) {
    if (!file) {
      setImageFile(null)
      return
    }

    const validationMessage = validateImageFile(file)
    if (validationMessage) {
      setImageFile(null)
      setState({ type: 'error', message: validationMessage })
      return
    }

    setImageFile(file)
    setState(emptyState)
  }

  async function uploadImage(): Promise<UploadedImage | null> {
    if (!supabase || !user || !imageFile) {
      return null
    }

    const validationMessage = validateImageFile(imageFile)
    if (validationMessage) {
      throw new Error(validationMessage)
    }

    const extension = getImageExtension(imageFile.type)
    const path = `postcards/${user.id}/${crypto.randomUUID()}.${extension}`
    const { error } = await supabase.storage.from('postcard-images').upload(path, imageFile, {
      contentType: imageFile.type,
      upsert: false,
    })

    if (error) {
      throw new Error(`사진 업로드에 실패했습니다. ${error.message}`)
    }

    return {
      path,
      contentType: imageFile.type,
      byteSize: imageFile.size,
    }
  }

  async function writePostcard() {
    if (!supabase || !user || !content.trim()) {
      return
    }

    setState({ type: 'loading', message: '엽서를 우체통에 넣는 중입니다.' })

    let uploadedImage: UploadedImage | null = null

    try {
      uploadedImage = await uploadImage()
      const { error } = await supabase.from('postcards').insert({
        author_id: user.id,
        content: content.trim(),
        image_path: uploadedImage?.path ?? null,
        image_content_type: uploadedImage?.contentType ?? null,
        image_byte_size: uploadedImage?.byteSize ?? null,
      })

      if (error) {
        if (uploadedImage) {
          await supabase.storage.from('postcard-images').remove([uploadedImage.path])
        }
        throw new Error(`엽서 저장에 실패했습니다. ${error.message}`)
      }

      setContent('')
      setImageFile(null)
      setState({ type: 'success', message: '엽서를 우체통에 넣었습니다. 승인 후 누군가에게 도착합니다.' })
      await refreshHistory()
    } catch (error) {
      setState({
        type: 'error',
        message: error instanceof Error ? error.message : '엽서를 보내지 못했습니다.',
      })
    }
  }

  async function openTodayPostcard() {
    if (!supabase || !user) {
      return
    }

    setState({ type: 'loading', message: '오늘 도착한 마음을 여는 중입니다.' })

    try {
      const { data, error } = await supabase.rpc('assign_today_postcard')
      if (error) throw error

      const postcard = Array.isArray(data) ? data[0] : data
      setToday(postcard)
      await loadSignedImageUrl(postcard?.image_path ?? null)
      await refreshHistory()
      setState({ type: 'success', message: '오늘의 엽서가 도착했습니다.' })
    } catch (error) {
      setState({
        type: 'error',
        message: error instanceof Error ? error.message : '오늘의 엽서를 열지 못했습니다.',
      })
    }
  }

  async function loadSignedImageUrl(path: string | null) {
    if (!supabase || !path) {
      setTodayImageUrl(null)
      return
    }

    const { data, error } = await supabase.storage
      .from('postcard-images')
      .createSignedUrl(path, signedUrlExpiresInSeconds)

    if (error) throw error
    setTodayImageUrl(data.signedUrl)
  }

  async function refreshHistory() {
    if (!supabase || !user) return

    const [authoredResult, deliveriesResult, actionsResult] = await Promise.all([
      supabase
        .from('postcards')
        .select('id, content, image_path, status, created_at')
        .eq('author_id', user.id)
        .order('created_at', { ascending: false }),
      supabase
        .from('postcard_deliveries')
        .select('id, postcard_id, delivered_date, created_at, postcard:postcards(id, content, image_path, status)')
        .eq('receiver_id', user.id)
        .order('created_at', { ascending: false }),
      supabase.from('postcard_user_actions').select('postcard_id, action_type').eq('user_id', user.id),
    ])

    if (authoredResult.error) throw authoredResult.error
    if (deliveriesResult.error) throw deliveriesResult.error
    if (actionsResult.error) throw actionsResult.error

    setAuthoredPostcards((authoredResult.data ?? []) as AuthoredPostcard[])
    setReceivedDeliveries((deliveriesResult.data ?? []) as unknown as ReceivedDelivery[])
    setUserActions((actionsResult.data ?? []) as UserAction[])
  }

  async function refreshAdmin() {
    if (!supabase || !user) return

    const { data: adminRows, error: adminError } = await supabase.from('admin_users').select('user_id').eq('user_id', user.id)
    if (adminError || !adminRows?.length) {
      setIsAdmin(false)
      setPendingPostcards([])
      setReports([])
      return
    }

    setIsAdmin(true)
    const [pendingResult, reportsResult] = await Promise.all([
      supabase
        .from('postcards')
        .select('id, content, image_path, status, created_at, moderated_at, moderation_note')
        .eq('status', 'pending')
        .order('created_at', { ascending: true }),
      supabase
        .from('postcard_reports')
        .select('id, postcard_id, delivery_id, reason, status, created_at, handler_note, postcard:postcards(id, content, status)')
        .eq('status', 'open')
        .order('created_at', { ascending: true }),
    ])

    if (pendingResult.error) throw pendingResult.error
    if (reportsResult.error) throw reportsResult.error

    setPendingPostcards((pendingResult.data ?? []) as AdminPostcard[])
    setReports((reportsResult.data ?? []) as unknown as AdminReport[])
  }

  async function createUserAction(delivery: TodayPostcard | ReceivedDelivery, actionType: 'not_interested' | 'hidden') {
    if (!supabase || !user) return

    const deliveryId = 'delivery_id' in delivery ? delivery.delivery_id : delivery.id
    const postcardId = delivery.postcard_id

    setState({ type: 'loading', message: '엽서에 조용한 표시를 남기는 중입니다.' })
    const { error } = await supabase.from('postcard_user_actions').insert({
      postcard_id: postcardId,
      delivery_id: deliveryId,
      user_id: user.id,
      action_type: actionType,
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    if (actionType === 'hidden' && today?.postcard_id === postcardId) {
      setToday(null)
      setTodayImageUrl(null)
    }
    await refreshHistory()
    setState({ type: 'success', message: actionType === 'hidden' ? '내 화면에서 숨겼습니다.' : '관심없음으로 남겼습니다.' })
  }

  async function createReport(delivery: TodayPostcard | ReceivedDelivery, reason: string) {
    if (!supabase || !user || !reason.trim()) return

    const deliveryId = 'delivery_id' in delivery ? delivery.delivery_id : delivery.id
    const postcardId = delivery.postcard_id

    setState({ type: 'loading', message: '신고를 접수하는 중입니다.' })
    const { error } = await supabase.from('postcard_reports').insert({
      postcard_id: postcardId,
      delivery_id: deliveryId,
      reporter_id: user.id,
      reason: reason.trim(),
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    setReportReason('')
    setHistoryReportReasons((previous) => ({ ...previous, [deliveryId]: '' }))
    setState({ type: 'success', message: '신고를 접수했습니다.' })
  }

  async function moderatePostcard(postcardId: string, action: 'approve' | 'reject' | 'hide') {
    if (!supabase) return

    setState({ type: 'loading', message: '관리자 처리를 저장하는 중입니다.' })
    const rpcName = action === 'approve' ? 'approve_postcard' : action === 'reject' ? 'reject_postcard' : 'hide_postcard'
    const params =
      action === 'approve'
        ? { target_postcard_id: postcardId }
        : action === 'reject'
          ? { target_postcard_id: postcardId, rejection_note: null }
          : { target_postcard_id: postcardId, hide_note: null }

    const { error } = await supabase.rpc(rpcName, params)
    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    await refreshAdmin()
    setState({ type: 'success', message: '처리했습니다.' })
  }

  async function resolveReport(reportId: string, shouldHidePostcard: boolean) {
    if (!supabase) return

    setState({ type: 'loading', message: '신고 처리 결과를 저장하는 중입니다.' })
    const { error } = await supabase.rpc('resolve_postcard_report', {
      target_report_id: reportId,
      next_status: shouldHidePostcard ? 'actioned' : 'dismissed',
      should_hide_postcard: shouldHidePostcard,
      note: null,
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    await refreshAdmin()
    setState({ type: 'success', message: '신고를 처리했습니다.' })
  }

  if (!hasSupabaseConfig) {
    return (
      <main className="app-shell">
        <section className="panel">
          <p className="eyebrow">설정 필요</p>
          <h1>Supabase 연결 정보가 필요합니다.</h1>
          <p>Cloudflare Pages 또는 로컬 `.env`에 `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`를 등록해주세요.</p>
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <section className="hero">
        <p className="eyebrow">문라이터</p>
        <h1>오늘의 마음을 누군가에게.</h1>
        <p>사진 한 장과 짧은 문장을 조용히 우체통에 넣어보세요.</p>
        {!user && <button onClick={signInAnonymously}>익명으로 시작</button>}
      </section>

      {user && (
        <>
          <nav className="tabs" aria-label="주요 화면">
            <button className={activeTab === 'write' ? 'tab active' : 'tab'} onClick={() => setActiveTab('write')}>
              쓰기
            </button>
            <button className={activeTab === 'history' ? 'tab active' : 'tab'} onClick={() => setActiveTab('history')}>
              기록
            </button>
            <button className={activeTab === 'account' ? 'tab active' : 'tab'} onClick={() => setActiveTab('account')}>
              계정
            </button>
            {isAdmin && (
              <button className={activeTab === 'admin' ? 'tab active' : 'tab'} onClick={() => setActiveTab('admin')}>
                관리
              </button>
            )}
          </nav>

          {activeTab === 'write' && (
            <section className="workspace">
              <section className="panel">
                <h2>엽서 쓰기</h2>
                <label>
                  사진
                  <input
                    type="file"
                    accept={imageAccept}
                    onChange={(event) => {
                      handleImageChange(event.target.files?.[0] ?? null)
                      event.currentTarget.value = ''
                    }}
                  />
                </label>
                <p className="field-note">JPEG, PNG, WebP 사진 1장만 보낼 수 있습니다. 최대 10MB.</p>
                {imagePreviewUrl && (
                  <div className="image-preview">
                    <img className="preview" src={imagePreviewUrl} alt="선택한 사진 미리보기" />
                    <button className="secondary-button" type="button" onClick={() => handleImageChange(null)}>
                      사진 선택 취소
                    </button>
                  </div>
                )}
                <label>
                  문장
                  <textarea
                    value={content}
                    maxLength={500}
                    onChange={(event) => setContent(event.target.value)}
                    placeholder="오늘 밤 공기가 조금 시원하네요."
                  />
                </label>
                <div className="actions">
                  <span>{content.length}/500</span>
                  <button disabled={!content.trim() || state.type === 'loading'} onClick={writePostcard}>
                    보내기
                  </button>
                </div>
              </section>

              <section className="panel night-panel">
                <h2>오늘의 엽서</h2>
                {todayImageUrl && <img className="received-image" src={todayImageUrl} alt="오늘 받은 엽서 사진" />}
                {today ? <p className="postcard-text">{today.content}</p> : <p>아직 열린 엽서가 없습니다.</p>}
                <button onClick={openTodayPostcard} disabled={state.type === 'loading'}>
                  오늘의 엽서 받기
                </button>
                {today && (
                  <div className="quiet-actions">
                    <button className="secondary-button" onClick={() => createUserAction(today, 'not_interested')}>
                      관심없음
                    </button>
                    <button className="secondary-button" onClick={() => createUserAction(today, 'hidden')}>
                      숨김
                    </button>
                    <label>
                      신고 사유
                      <input
                        value={reportReason}
                        maxLength={500}
                        onChange={(event) => setReportReason(event.target.value)}
                        placeholder="관리자에게 전할 내용을 적어주세요."
                      />
                    </label>
                    <button disabled={!reportReason.trim()} onClick={() => createReport(today, reportReason)}>
                      신고
                    </button>
                  </div>
                )}
              </section>
            </section>
          )}

          {activeTab === 'history' && (
            <section className="workspace">
              <section className="panel">
                <div className="panel-header">
                  <h2>보낸 기록</h2>
                  <button className="secondary-button" onClick={refreshHistory}>
                    새로고침
                  </button>
                </div>
                <div className="list">
                  {authoredPostcards.length === 0 && <p>아직 보낸 엽서가 없습니다.</p>}
                  {authoredPostcards.map((postcard) => (
                    <article className="postcard-row" key={postcard.id}>
                      <span className={`badge ${postcard.status}`}>{postcard.status}</span>
                      <p>{postcard.content}</p>
                      <time>{new Date(postcard.created_at).toLocaleString()}</time>
                    </article>
                  ))}
                </div>
              </section>

              <section className="panel">
                <h2>받은 기록</h2>
                <div className="list">
                  {visibleReceivedDeliveries.length === 0 && <p>아직 받은 엽서 기록이 없습니다.</p>}
                  {visibleReceivedDeliveries.map((delivery) => (
                    <article className="postcard-row" key={delivery.id}>
                      <time>{delivery.delivered_date}</time>
                      <p>{delivery.postcard?.content ?? '열 수 없는 엽서입니다.'}</p>
                      <div className="row-actions">
                        <button className="secondary-button" onClick={() => createUserAction(delivery, 'not_interested')}>
                          관심없음
                        </button>
                        <button className="secondary-button" onClick={() => createUserAction(delivery, 'hidden')}>
                          숨김
                        </button>
                      </div>
                      <label>
                        신고 사유
                        <input
                          value={historyReportReasons[delivery.id] ?? ''}
                          maxLength={500}
                          onChange={(event) =>
                            setHistoryReportReasons((previous) => ({ ...previous, [delivery.id]: event.target.value }))
                          }
                          placeholder="관리자에게 전할 내용을 적어주세요."
                        />
                      </label>
                      <button
                        disabled={!(historyReportReasons[delivery.id] ?? '').trim()}
                        onClick={() => createReport(delivery, historyReportReasons[delivery.id] ?? '')}
                      >
                        신고
                      </button>
                    </article>
                  ))}
                </div>
              </section>
            </section>
          )}

          {activeTab === 'account' && (
            <section className="workspace">
              <section className="panel">
                <h2>로그인</h2>
                <p className="field-note">이메일 매직 링크, 이메일/비밀번호, 구글, 카카오 로그인을 순서대로 사용할 수 있습니다.</p>
                <label>
                  이메일
                  <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" />
                </label>
                <label>
                  비밀번호
                  <input
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="6자 이상"
                  />
                </label>
                <div className="button-grid">
                  <button onClick={sendMagicLink} disabled={!email.trim()}>
                    매직 링크
                  </button>
                  <button onClick={signInWithPassword} disabled={!email.trim() || password.length < 6}>
                    비밀번호 로그인
                  </button>
                  <button onClick={signUpWithPassword} disabled={!email.trim() || password.length < 6}>
                    가입
                  </button>
                  <button className="secondary-button" onClick={() => signInWithProvider('google')}>
                    구글
                  </button>
                  <button className="secondary-button" onClick={() => signInWithProvider('kakao')}>
                    카카오
                  </button>
                </div>
              </section>

              <section className="panel night-panel">
                <h2>현재 세션</h2>
                <p className="mono">{user.email ?? user.id}</p>
                <button onClick={signOut}>로그아웃</button>
              </section>
            </section>
          )}

          {activeTab === 'admin' && isAdmin && (
            <section className="workspace">
              <section className="panel">
                <div className="panel-header">
                  <h2>승인 대기</h2>
                  <button className="secondary-button" onClick={refreshAdmin}>
                    새로고침
                  </button>
                </div>
                <div className="list">
                  {pendingPostcards.length === 0 && <p>대기 중인 엽서가 없습니다.</p>}
                  {pendingPostcards.map((postcard) => (
                    <article className="postcard-row" key={postcard.id}>
                      <p>{postcard.content}</p>
                      <div className="row-actions">
                        <button onClick={() => moderatePostcard(postcard.id, 'approve')}>승인</button>
                        <button className="secondary-button" onClick={() => moderatePostcard(postcard.id, 'reject')}>
                          반려
                        </button>
                        <button className="secondary-button" onClick={() => moderatePostcard(postcard.id, 'hide')}>
                          숨김
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              </section>

              <section className="panel">
                <h2>신고</h2>
                <div className="list">
                  {reports.length === 0 && <p>열린 신고가 없습니다.</p>}
                  {reports.map((report) => (
                    <article className="postcard-row" key={report.id}>
                      <span className="badge open">{report.status}</span>
                      <p>{report.postcard?.content ?? '엽서를 불러올 수 없습니다.'}</p>
                      <p className="field-note">{report.reason}</p>
                      <div className="row-actions">
                        <button onClick={() => resolveReport(report.id, true)}>숨김 처리</button>
                        <button className="secondary-button" onClick={() => resolveReport(report.id, false)}>
                          유지
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            </section>
          )}
        </>
      )}

      {state.type !== 'idle' && <p className={`status ${state.type}`}>{state.message}</p>}
    </main>
  )
}
