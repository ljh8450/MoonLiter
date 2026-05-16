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

type RequestState =
  | { type: 'idle' }
  | { type: 'loading'; message: string }
  | { type: 'success'; message: string }
  | { type: 'error'; message: string }

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
  const [content, setContent] = useState('')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [today, setToday] = useState<TodayPostcard | null>(null)
  const [todayImageUrl, setTodayImageUrl] = useState<string | null>(null)
  const [state, setState] = useState<RequestState>(emptyState)

  const imagePreviewUrl = useMemo(() => {
    if (!imageFile) return null
    return URL.createObjectURL(imageFile)
  }, [imageFile])

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
    return () => {
      if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl)
    }
  }, [imagePreviewUrl])

  async function signIn() {
    if (!supabase) return

    setState({ type: 'loading', message: '익명 우체국 창구를 여는 중입니다.' })
    const { error } = await supabase.auth.signInAnonymously()
    setState(error ? { type: 'error', message: error.message } : emptyState)
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
        {!user && <button onClick={signIn}>익명으로 시작</button>}
      </section>

      {user && (
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
            {today ? (
              <p className="postcard-text">{today.content}</p>
            ) : (
              <p>아직 열린 엽서가 없습니다.</p>
            )}
            <button onClick={openTodayPostcard} disabled={state.type === 'loading'}>
              오늘의 엽서 받기
            </button>
          </section>
        </section>
      )}

      {state.type !== 'idle' && <p className={`status ${state.type}`}>{state.message}</p>}
    </main>
  )
}
