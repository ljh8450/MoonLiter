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

type Profile = {
  id: string
  suspended_at: string | null
  suspension_reason: string | null
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
    author_id: string
    content: string
    image_path: string | null
    status: string
  } | null
}

type AdminUser = {
  user_id: string
  email: string | null
  created_at: string
}

type SuspendedUser = {
  user_id: string
  email: string | null
  suspended_at: string
  suspension_reason: string | null
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
  const [profile, setProfile] = useState<Profile | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>('write')
  const [content, setContent] = useState('')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [today, setToday] = useState<TodayPostcard | null>(null)
  const [todayImageUrl, setTodayImageUrl] = useState<string | null>(null)
  const [authoredPostcards, setAuthoredPostcards] = useState<AuthoredPostcard[]>([])
  const [receivedDeliveries, setReceivedDeliveries] = useState<ReceivedDelivery[]>([])
  const [receivedImageUrls, setReceivedImageUrls] = useState<Record<string, string>>({})
  const [userActions, setUserActions] = useState<UserAction[]>([])
  const [isAdmin, setIsAdmin] = useState(false)
  const [pendingPostcards, setPendingPostcards] = useState<AdminPostcard[]>([])
  const [adminImageUrls, setAdminImageUrls] = useState<Record<string, string>>({})
  const [reportImageUrls, setReportImageUrls] = useState<Record<string, string>>({})
  const [reports, setReports] = useState<AdminReport[]>([])
  const [adminUsers, setAdminUsers] = useState<AdminUser[]>([])
  const [suspendedUsers, setSuspendedUsers] = useState<SuspendedUser[]>([])
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [adminInviteEmail, setAdminInviteEmail] = useState('')
  const [reportReason, setReportReason] = useState('')
  const [historyReportReasons, setHistoryReportReasons] = useState<Record<string, string>>({})
  const [moderationNotes, setModerationNotes] = useState<Record<string, string>>({})
  const [reportNotes, setReportNotes] = useState<Record<string, string>>({})
  const [suspensionReasons, setSuspensionReasons] = useState<Record<string, string>>({})
  const [path, setPath] = useState(() => window.location.pathname)
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

  const isAdminPath = path === '/admin'
  const isSuspended = Boolean(profile?.suspended_at)

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
      setProfile(null)
      setAuthoredPostcards([])
      setReceivedDeliveries([])
      setReceivedImageUrls({})
      setUserActions([])
      setIsAdmin(false)
      setPendingPostcards([])
      setAdminImageUrls({})
      setReportImageUrls({})
      setReports([])
      setAdminUsers([])
      setSuspendedUsers([])
      return
    }

    void refreshProfile()
    void refreshHistory()
    void refreshAdmin()
  }, [user])

  useEffect(() => {
    return () => {
      if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl)
    }
  }, [imagePreviewUrl])

  useEffect(() => {
    const handlePopState = () => setPath(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  function navigate(nextPath: string) {
    window.history.pushState({}, '', nextPath)
    setPath(nextPath)
  }

  async function signOut() {
    if (!supabase) return
    await supabase.auth.signOut()
    setToday(null)
    setTodayImageUrl(null)
    setState({ type: 'success', message: '조용히 문을 닫았습니다.' })
  }

  async function refreshProfile() {
    if (!supabase || !user) return

    const { data, error } = await supabase
      .from('profiles')
      .select('id, suspended_at, suspension_reason')
      .eq('id', user.id)
      .single()

    if (error) {
      setProfile(null)
      return
    }

    setProfile(data as Profile)
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
    if (isSuspended) {
      setState({ type: 'error', message: '정지된 계정은 엽서를 보낼 수 없습니다.' })
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
    if (isSuspended) {
      setState({ type: 'error', message: '정지된 계정은 엽서를 받을 수 없습니다.' })
      return
    }

    setState({ type: 'loading', message: '오늘 도착한 마음을 여는 중입니다.' })

    try {
      const { data, error } = await supabase.rpc('assign_today_postcard')
      if (error) throw error

      const postcard = Array.isArray(data) ? data[0] : data
      if (!postcard) {
        setToday(null)
        setTodayImageUrl(null)
        setState({ type: 'error', message: '이 엽서는 더 이상 열 수 없습니다.' })
        await refreshHistory()
        return
      }

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
    const deliveries = (deliveriesResult.data ?? []) as unknown as ReceivedDelivery[]
    setReceivedDeliveries(deliveries)
    setReceivedImageUrls(
      await loadImageUrlsByPostcardId(
        deliveries.map((delivery) => ({
          id: delivery.id,
          image_path: delivery.postcard?.image_path ?? null,
        })),
      ),
    )
    setUserActions((actionsResult.data ?? []) as UserAction[])
  }

  async function refreshAdmin() {
    if (!supabase || !user) return

    const { data: adminRows, error: adminError } = await supabase.from('admin_users').select('user_id').eq('user_id', user.id)
    if (adminError || !adminRows?.length) {
      setIsAdmin(false)
      setPendingPostcards([])
      setAdminImageUrls({})
      setReportImageUrls({})
      setReports([])
      setAdminUsers([])
      setSuspendedUsers([])
      return
    }

    setIsAdmin(true)
    const [pendingResult, reportsResult, adminUsersResult, suspendedUsersResult] = await Promise.all([
      supabase
        .from('postcards')
        .select('id, content, image_path, status, created_at, moderated_at, moderation_note')
        .eq('status', 'pending')
        .order('created_at', { ascending: true }),
      supabase
        .from('postcard_reports')
        .select('id, postcard_id, delivery_id, reason, status, created_at, handler_note, postcard:postcards(id, author_id, content, image_path, status)')
        .eq('status', 'open')
        .order('created_at', { ascending: true }),
      supabase.rpc('list_admin_users'),
      supabase.rpc('list_suspended_users'),
    ])

    if (pendingResult.error) throw pendingResult.error
    if (reportsResult.error) throw reportsResult.error
    if (adminUsersResult.error) throw adminUsersResult.error
    if (suspendedUsersResult.error) throw suspendedUsersResult.error

    const pending = (pendingResult.data ?? []) as AdminPostcard[]
    const openReports = (reportsResult.data ?? []) as unknown as AdminReport[]
    setPendingPostcards(pending)
    setAdminImageUrls(await loadImageUrlsByPostcardId(pending))
    setReports(openReports)
    setReportImageUrls(
      await loadImageUrlsByPostcardId(
        openReports.map((report) => ({
          id: report.id,
          image_path: report.postcard?.image_path ?? null,
        })),
      ),
    )
    setAdminUsers((adminUsersResult.data ?? []) as AdminUser[])
    setSuspendedUsers((suspendedUsersResult.data ?? []) as SuspendedUser[])
  }

  async function loadImageUrlsByPostcardId(postcards: Array<{ id: string; image_path: string | null }>) {
    if (!supabase) return {}
    const client = supabase

    const entries = await Promise.all(
      postcards
        .filter((postcard) => postcard.image_path)
        .map(async (postcard) => {
          const { data, error } = await client.storage
            .from('postcard-images')
            .createSignedUrl(postcard.image_path!, signedUrlExpiresInSeconds)

          if (error || !data?.signedUrl) return null
          return [postcard.id, data.signedUrl] as const
        }),
    )

    return Object.fromEntries(entries.filter((entry): entry is readonly [string, string] => entry !== null))
  }

  async function createUserAction(delivery: TodayPostcard | ReceivedDelivery, actionType: 'not_interested' | 'hidden') {
    if (!supabase || !user) return
    if (isSuspended) {
      setState({ type: 'error', message: '정지된 계정은 엽서 액션을 남길 수 없습니다.' })
      return
    }

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
    if (isSuspended) {
      setState({ type: 'error', message: '정지된 계정은 신고를 접수할 수 없습니다.' })
      return
    }

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

  async function moderatePostcard(postcardId: string, action: 'approve' | 'reject') {
    if (!supabase) return

    setState({ type: 'loading', message: '관리자 처리를 저장하는 중입니다.' })
    const rpcName = action === 'approve' ? 'approve_postcard' : 'reject_postcard'
    const params =
      action === 'approve'
        ? { target_postcard_id: postcardId }
        : { target_postcard_id: postcardId, rejection_note: moderationNotes[postcardId]?.trim() || null }

    const { error } = await supabase.rpc(rpcName, params)
    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    await refreshAdmin()
    setModerationNotes((previous) => ({ ...previous, [postcardId]: '' }))
    setState({ type: 'success', message: '처리했습니다.' })
  }

  async function resolveReport(reportId: string, shouldHidePostcard: boolean) {
    if (!supabase) return

    setState({ type: 'loading', message: '신고 처리 결과를 저장하는 중입니다.' })
    const { error } = await supabase.rpc('resolve_postcard_report', {
      target_report_id: reportId,
      next_status: shouldHidePostcard ? 'actioned' : 'dismissed',
      should_hide_postcard: shouldHidePostcard,
      note: reportNotes[reportId]?.trim() || null,
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    await refreshAdmin()
    setReportNotes((previous) => ({ ...previous, [reportId]: '' }))
    setState({ type: 'success', message: '신고를 처리했습니다.' })
  }

  async function inviteAdminUser() {
    if (!supabase || !adminInviteEmail.trim()) return

    setState({ type: 'loading', message: '관리자 권한을 부여하는 중입니다.' })
    const { error } = await supabase.rpc('invite_admin_user', {
      target_email: adminInviteEmail.trim(),
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    setAdminInviteEmail('')
    await refreshAdmin()
    setState({ type: 'success', message: '관리자 권한을 부여했습니다.' })
  }

  async function removeAdminUser(userId: string) {
    if (!supabase) return

    setState({ type: 'loading', message: '관리자 권한을 회수하는 중입니다.' })
    const { error } = await supabase.rpc('remove_admin_user', {
      target_user_id: userId,
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    await refreshAdmin()
    setState({ type: 'success', message: '관리자 권한을 회수했습니다.' })
  }

  async function suspendUser(userId: string, reportId: string) {
    if (!supabase) return

    setState({ type: 'loading', message: '사용자를 정지하는 중입니다.' })
    const { error } = await supabase.rpc('suspend_user', {
      target_user_id: userId,
      reason: suspensionReasons[reportId]?.trim() || null,
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    setSuspensionReasons((previous) => ({ ...previous, [reportId]: '' }))
    await refreshAdmin()
    setState({ type: 'success', message: '사용자를 정지했습니다.' })
  }

  async function unsuspendUser(userId: string) {
    if (!supabase) return

    setState({ type: 'loading', message: '사용자 정지를 해제하는 중입니다.' })
    const { error } = await supabase.rpc('unsuspend_user', {
      target_user_id: userId,
    })

    if (error) {
      setState({ type: 'error', message: error.message })
      return
    }

    await refreshAdmin()
    setState({ type: 'success', message: '사용자 정지를 해제했습니다.' })
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
        <p>{isAdminPath ? '조용히 도착한 엽서를 살피고 필요한 처리를 남깁니다.' : '사진 한 장과 짧은 문장을 조용히 우체통에 넣어보세요.'}</p>
        {!user && (
          <div className="hero-actions">
            <button onClick={() => signInWithProvider('google')}>구글로 시작</button>
            <button className="secondary-dark-button" onClick={() => signInWithProvider('kakao')}>
              카카오로 시작
            </button>
          </div>
        )}
      </section>

      {user && (
        <>
          <nav className="tabs" aria-label="주요 화면">
            <button className={!isAdminPath && activeTab === 'write' ? 'tab active' : 'tab'} onClick={() => {
              navigate('/')
              setActiveTab('write')
            }}>
              쓰기
            </button>
            <button className={!isAdminPath && activeTab === 'history' ? 'tab active' : 'tab'} onClick={() => {
              navigate('/')
              setActiveTab('history')
            }}>
              기록
            </button>
            <button className={!isAdminPath && activeTab === 'account' ? 'tab active' : 'tab'} onClick={() => {
              navigate('/')
              setActiveTab('account')
            }}>
              계정
            </button>
            {isAdmin && (
              <button className={isAdminPath ? 'tab active' : 'tab'} onClick={() => navigate('/admin')}>
                관리
              </button>
            )}
          </nav>

          {isSuspended && (
            <section className="status error account-warning">
              정지된 계정입니다. {profile?.suspension_reason ? `사유: ${profile.suspension_reason}` : '관리자에게 문의해주세요.'}
            </section>
          )}

          {!isAdminPath && activeTab === 'write' && (
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
                  <button disabled={!content.trim() || state.type === 'loading' || isSuspended} onClick={writePostcard}>
                    보내기
                  </button>
                </div>
              </section>

              <section className="panel night-panel">
                <h2>오늘의 엽서</h2>
                {todayImageUrl && <img className="received-image" src={todayImageUrl} alt="오늘 받은 엽서 사진" />}
                {today ? <p className="postcard-text">{today.content}</p> : <p>아직 열린 엽서가 없습니다.</p>}
                <button onClick={openTodayPostcard} disabled={state.type === 'loading' || isSuspended}>
                  오늘의 엽서 받기
                </button>
                {today && (
                  <div className="quiet-actions">
                    <button className="secondary-button" disabled={isSuspended} onClick={() => createUserAction(today, 'not_interested')}>
                      관심없음
                    </button>
                    <button className="secondary-button" disabled={isSuspended} onClick={() => createUserAction(today, 'hidden')}>
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
                    <button disabled={!reportReason.trim() || isSuspended} onClick={() => createReport(today, reportReason)}>
                      신고
                    </button>
                  </div>
                )}
              </section>
            </section>
          )}

          {!isAdminPath && activeTab === 'history' && (
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
                      {receivedImageUrls[delivery.id] && (
                        <img className="admin-image" src={receivedImageUrls[delivery.id]} alt="받은 엽서 사진" />
                      )}
                      <p>{delivery.postcard?.content ?? '열 수 없는 엽서입니다.'}</p>
                      <div className="row-actions">
                        <button className="secondary-button" disabled={isSuspended} onClick={() => createUserAction(delivery, 'not_interested')}>
                          관심없음
                        </button>
                        <button className="secondary-button" disabled={isSuspended} onClick={() => createUserAction(delivery, 'hidden')}>
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
                        disabled={!(historyReportReasons[delivery.id] ?? '').trim() || isSuspended}
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

          {!isAdminPath && activeTab === 'account' && (
            <section className="workspace">
              <section className="panel">
                <h2>로그인</h2>
                <div className="button-grid priority-grid">
                  <button onClick={() => signInWithProvider('google')}>구글로 계속</button>
                  <button onClick={() => signInWithProvider('kakao')}>카카오로 계속</button>
                </div>
                <div className="divider" />
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
                </div>
              </section>

              <section className="panel night-panel">
                <h2>현재 세션</h2>
                <p className="mono">{user.email ?? user.id}</p>
                <button onClick={signOut}>로그아웃</button>
              </section>
            </section>
          )}

          {isAdminPath && !isAdmin && (
            <section className="panel narrow-panel">
              <h2>관리자 권한이 필요합니다.</h2>
              <p>최초 관리자 1명은 Supabase Studio에서 수동 등록한 뒤 접근할 수 있습니다.</p>
              <button onClick={() => navigate('/')}>돌아가기</button>
            </section>
          )}

          {isAdminPath && isAdmin && (
            <section className="admin-layout">
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
                      {adminImageUrls[postcard.id] && (
                        <img className="admin-image" src={adminImageUrls[postcard.id]} alt="승인 대기 엽서 사진" />
                      )}
                      <p>{postcard.content}</p>
                      <label>
                        반려 사유
                        <input
                          value={moderationNotes[postcard.id] ?? ''}
                          maxLength={500}
                          onChange={(event) =>
                            setModerationNotes((previous) => ({ ...previous, [postcard.id]: event.target.value }))
                          }
                          placeholder="반려할 때 작성자 기록에 남길 메모"
                        />
                      </label>
                      <div className="row-actions">
                        <button onClick={() => moderatePostcard(postcard.id, 'approve')}>승인</button>
                        <button className="secondary-button" onClick={() => moderatePostcard(postcard.id, 'reject')}>
                          반려
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
                      {reportImageUrls[report.id] && (
                        <img className="admin-image" src={reportImageUrls[report.id]} alt="신고된 엽서 사진" />
                      )}
                      <p>{report.postcard?.content ?? '엽서를 불러올 수 없습니다.'}</p>
                      <p className="field-note">{report.reason}</p>
                      <p className="mono">작성자: {report.postcard?.author_id ?? '알 수 없음'}</p>
                      <p className="mono">상태: {report.postcard?.status ?? '알 수 없음'}</p>
                      <label>
                        처리 메모
                        <input
                          value={reportNotes[report.id] ?? ''}
                          maxLength={500}
                          onChange={(event) =>
                            setReportNotes((previous) => ({ ...previous, [report.id]: event.target.value }))
                          }
                          placeholder="신고 처리 메모"
                        />
                      </label>
                      <label>
                        사용자 정지 사유
                        <input
                          value={suspensionReasons[report.id] ?? ''}
                          maxLength={500}
                          onChange={(event) =>
                            setSuspensionReasons((previous) => ({ ...previous, [report.id]: event.target.value }))
                          }
                          placeholder="작성자를 정지할 때 남길 사유"
                        />
                      </label>
                      <div className="row-actions">
                        <button onClick={() => resolveReport(report.id, true)}>숨김 처리</button>
                        <button className="secondary-button" onClick={() => resolveReport(report.id, false)}>
                          유지
                        </button>
                        {report.postcard?.author_id && (
                          <button
                            className="secondary-button"
                            disabled={!suspensionReasons[report.id]?.trim()}
                            onClick={() => suspendUser(report.postcard!.author_id, report.id)}
                          >
                            작성자 정지
                          </button>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              </section>

              <section className="panel">
                <h2>정지 사용자</h2>
                <div className="list admin-users">
                  {suspendedUsers.length === 0 && <p>정지된 사용자가 없습니다.</p>}
                  {suspendedUsers.map((suspendedUser) => (
                    <article className="postcard-row" key={suspendedUser.user_id}>
                      <p className="mono">{suspendedUser.email ?? suspendedUser.user_id}</p>
                      <time>{new Date(suspendedUser.suspended_at).toLocaleString()}</time>
                      {suspendedUser.suspension_reason && <p>{suspendedUser.suspension_reason}</p>}
                      <button className="secondary-button" onClick={() => unsuspendUser(suspendedUser.user_id)}>
                        정지 해제
                      </button>
                    </article>
                  ))}
                </div>
              </section>

              <section className="panel">
                <h2>관리자 계정</h2>
                <label>
                  초대할 이메일
                  <input
                    value={adminInviteEmail}
                    onChange={(event) => setAdminInviteEmail(event.target.value)}
                    placeholder="admin@example.com"
                  />
                </label>
                <button disabled={!adminInviteEmail.trim()} onClick={inviteAdminUser}>
                  관리자 권한 부여
                </button>
                <div className="list admin-users">
                  {adminUsers.map((adminUser) => (
                    <article className="postcard-row" key={adminUser.user_id}>
                      <p className="mono">{adminUser.email ?? adminUser.user_id}</p>
                      <time>{new Date(adminUser.created_at).toLocaleString()}</time>
                      <button
                        className="secondary-button"
                        disabled={adminUser.user_id === user.id}
                        onClick={() => removeAdminUser(adminUser.user_id)}
                      >
                        권한 회수
                      </button>
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
