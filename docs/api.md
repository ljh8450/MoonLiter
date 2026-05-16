# API 문서

이 문서는 현재 우선 구현 경로인 React + Vite + Supabase 웹 MVP의 클라이언트 연동 계약을 기록한다.

Android/Spring Boot REST API는 향후 구현 후보이며, 현재 웹 MVP의 우선 경로가 아니다.

## 공통 규칙

- 인증: Supabase Auth 익명 로그인.
- DB: Supabase Postgres.
- 파일 저장소: Supabase Storage `postcard-images` private bucket.
- 클라이언트 환경 변수:

```text
VITE_SUPABASE_URL=
VITE_SUPABASE_ANON_KEY=
```

## 관련 테이블과 저장소

- `public.profiles`
- `public.postcards`
- `public.postcard_deliveries`
- Storage bucket: `postcard-images`
- RPC: `public.assign_today_postcard()`

## 로그인

웹앱은 Supabase 익명 로그인을 사용한다.

```ts
await supabase.auth.signInAnonymously()
```

### 동작 규칙

- Supabase Auth에서 익명 로그인이 활성화되어 있어야 한다.
- 신규 사용자가 생성되면 `auth.users` 트리거가 `public.profiles` 행을 생성한다.
- RPC에서도 현재 사용자 프로필이 없으면 보정 생성한다.

## 사진 업로드

엽서 작성 전에 선택한 이미지 1장을 Supabase Storage에 업로드한다.

```ts
await supabase.storage
  .from('postcard-images')
  .upload(`${user.id}/${crypto.randomUUID()}.${extension}`, file, {
    contentType: file.type,
    upsert: false,
  })
```

### 저장 경로

```text
{auth.users.id}/{random_uuid}.{extension}
```

### 검증 규칙

- 사용자는 자기 사용자 ID 폴더 아래에만 업로드할 수 있다.
- 버킷은 private이다.
- MVP 클라이언트는 `image/jpeg`, `image/png`, `image/webp`만 허용한다.
- 선택한 파일은 10MB 이하여야 한다.
- 파일 선택 직후와 업로드 직전에 같은 검증을 수행한다.
- Storage bucket도 10MB와 허용 MIME type을 제한한다.
- 이미지 리사이징, 썸네일 생성, EXIF 제거는 현재 범위가 아니다.

## 엽서 작성

사진 업로드 후 `public.postcards`에 엽서를 저장한다.

```ts
await supabase.from('postcards').insert({
  author_id: user.id,
  content: content.trim(),
  image_path: uploadedImage?.path ?? null,
  image_content_type: uploadedImage?.contentType ?? null,
  image_byte_size: uploadedImage?.byteSize ?? null,
})
```

### 컬럼

```text
author_id: 현재 Supabase 사용자 ID
content: 1자 이상 500자 이하 문장
image_path: Storage object path, 사진이 없으면 null
image_content_type: image/jpeg, image/png, image/webp 중 하나, 사진이 없으면 null
image_byte_size: 1 이상 10MB 이하 byte 크기, 사진이 없으면 null
status: 기본값 deliverable
```

### 검증 규칙

- `author_id`는 현재 로그인 사용자와 같아야 한다.
- `content`는 공백 제거 후 1자 이상 500자 이하여야 한다.
- `image_path`에는 공개 URL이 아니라 Storage object path를 저장한다.
- `image_content_type`과 `image_byte_size`는 클라이언트 검증값이며, DB check constraint와 Storage bucket 제한으로 한 번 더 제한한다.
- Storage 업로드 성공 후 엽서 저장이 실패하면 클라이언트는 방금 업로드한 object 삭제를 시도한다.

## 오늘의 엽서 받기

오늘 받을 엽서는 Supabase RPC로 배정한다.

```ts
const { data, error } = await supabase.rpc('assign_today_postcard')
```

### 응답

```json
{
  "delivery_id": "6c5f7b8f-7b88-4b9a-81c3-9d68b7f0d781",
  "postcard_id": "51c8332d-fbaa-4fef-91f1-5f2ff8c8ad91",
  "content": "오늘 밤 공기가 조금 시원하네요.",
  "image_path": "a73f.../f71d....jpg",
  "image_content_type": "image/jpeg",
  "image_byte_size": 3481920,
  "delivered_date": "2026-05-15",
  "delivered_at": "2026-05-15T12:30:00Z"
}
```

### 동작 규칙

- 같은 사용자는 같은 UTC 날짜에 하나의 엽서만 배정받는다.
- 자기 자신이 작성한 엽서는 배정하지 않는다.
- 이미 오늘 배정받은 엽서가 있으면 기존 배정을 반환한다.
- 배정 가능한 엽서가 없으면 RPC 예외가 발생한다.

## 오늘의 엽서 이미지 표시

RPC 응답의 `image_path`가 있으면 signed URL을 생성해 표시한다.

```ts
await supabase.storage
  .from('postcard-images')
  .createSignedUrl(imagePath, 60 * 60)
```

### 동작 규칙

- DB에는 signed URL을 저장하지 않는다.
- 화면 표시 시점에 `image_path`로 1시간짜리 signed URL을 생성한다.
- `image_path`가 없으면 문장만 표시한다.
- Storage object는 작성자 또는 오늘의 엽서로 배정받은 수신자만 읽을 수 있다.

## 주요 오류 상황

- Supabase 환경 변수가 없으면 웹앱은 설정 필요 화면을 표시한다.
- 익명 로그인이 비활성화되어 있으면 로그인에 실패한다.
- Storage 버킷이나 정책이 없으면 사진 업로드에 실패한다.
- 배정 가능한 엽서가 없으면 오늘의 엽서 받기에 실패한다.
- signed URL 생성 권한이 없으면 이미지 표시가 실패한다.

## 미구현 범위

- Android/Spring Boot REST API.
- presigned URL 기반 외부 오브젝트 스토리지 직접 업로드.
- 운영 로그인 고도화.
- 엽서 저장, 반응, 신고, 차단.
- 관리자 검수 API.
- 푸시 알림.
