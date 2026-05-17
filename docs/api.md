# API 문서

이 문서는 현재 우선 구현 경로인 React + Vite + Supabase 웹 MVP의 클라이언트 연동 계약을 기록한다.

Android/Spring Boot REST API는 향후 구현 후보이며, 현재 웹 MVP의 우선 경로가 아니다.

## 현재 구현 상태

- 웹앱은 Supabase 환경 변수가 있을 때 소셜/이메일 로그인, 엽서 작성, 사진 1장 업로드, 오늘의 엽서 받기를 제공한다.
- 새 엽서는 `pending` 상태로 저장되고, 관리자 화면에서 승인 또는 반려할 수 있다.
- 오늘의 엽서 배정 RPC는 `approved` 상태 엽서만 반환한다.
- 사진은 private Storage bucket에 저장하고, 화면 표시 시 클라이언트가 `image_path`로 1시간짜리 signed URL을 생성한다.
- 작성 기록, 수신 기록 목록, 신고, 사용자별 관심없음/숨김, 관리자 페이지, 관리자 권한 관리, 사용자 정지는 구현되어 있다.
- 고아 파일 정기 정리는 아직 구현되어 있지 않다.

## 공통 규칙

- 인증: Supabase Auth 소셜 로그인과 이메일 로그인.
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
- `public.admin_users`
- `public.postcard_user_actions`
- `public.postcard_reports`
- Storage bucket: `postcard-images`
- RPC: `public.assign_today_postcard()`
- RPC: `public.approve_postcard(postcard_id)`
- RPC: `public.reject_postcard(postcard_id, note)`
- RPC: `public.hide_postcard(postcard_id, note)`
- RPC: `public.resolve_postcard_report(report_id, next_status, should_hide_postcard, note)`
- RPC: `public.list_admin_users()`
- RPC: `public.invite_admin_user(email)`
- RPC: `public.remove_admin_user(user_id)`
- RPC: `public.suspend_user(user_id, reason)`
- RPC: `public.unsuspend_user(user_id)`

## 로그인

웹앱은 Supabase 소셜 로그인과 이메일 로그인을 사용한다. 비로그인 익명 작성 기능은 제공하지 않는다.
제품의 익명성은 수신자에게 작성자 계정, 이름, 이메일, 소셜 프로필을 노출하지 않는 방식으로 유지한다.

```ts
await supabase.auth.signInWithOAuth({ provider: 'google' })
await supabase.auth.signInWithOAuth({ provider: 'kakao' })
await supabase.auth.signInWithOtp({ email })
await supabase.auth.signInWithPassword({ email, password })
```

### 동작 규칙

- 신규 사용자가 생성되면 `auth.users` 트리거가 `public.profiles` 행을 생성한다.
- RPC에서도 현재 사용자 프로필이 없으면 보정 생성한다.
- 정지된 사용자는 엽서 작성, 오늘의 엽서 수신, 신고, 사용자 사후 액션, 이미지 업로드가 제한된다.

## 사진 업로드

엽서 작성 전에 선택한 이미지 1장을 Supabase Storage에 업로드한다.

```ts
await supabase.storage
  .from('postcard-images')
  .upload(`postcards/${user.id}/${crypto.randomUUID()}.${extension}`, file, {
    contentType: file.type,
    upsert: false,
  })
```

### 저장 경로

```text
postcards/{auth.users.id}/{random_uuid}.{extension}
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
status: 기본값 pending
```

### 검증 규칙

- `author_id`는 현재 로그인 사용자와 같아야 한다.
- 새 엽서는 기본적으로 `pending` 상태로 생성된다.
- 클라이언트는 `approved` 상태의 엽서를 직접 생성하거나 변경할 수 없다.
- `content`는 공백 제거 후 1자 이상 500자 이하여야 한다.
- `image_path`에는 공개 URL이 아니라 Storage object path를 저장한다.
- `image_content_type`과 `image_byte_size`는 클라이언트 검증값이며, DB check constraint와 Storage bucket 제한으로 한 번 더 제한한다.
- Storage 업로드 성공 후 엽서 저장이 실패하면 클라이언트는 방금 업로드한 object 삭제를 시도한다.
- DB insert 실패 후 즉시 삭제가 실패해 남은 고아 파일의 정기 정리는 후속 인증/관리자 확장 계획에서 구현한다.

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
  "image_path": "postcards/a73f.../f71d....jpg",
  "image_content_type": "image/jpeg",
  "image_byte_size": 3481920,
  "delivered_date": "2026-05-15",
  "delivered_at": "2026-05-15T12:30:00Z"
}
```

### 동작 규칙

- 같은 사용자는 같은 UTC 날짜에 하나의 엽서만 배정받는다.
- 자기 자신이 작성한 엽서는 배정하지 않는다.
- `approved` 상태의 엽서만 배정한다.
- 이미 오늘 배정받은 엽서가 있으면 기존 배정을 반환한다.
- 배정 가능한 엽서가 없으면 RPC 예외가 발생한다.

## 오늘의 엽서 이미지 표시

RPC 응답의 `image_path`가 있으면 권한 확인을 거쳐 signed URL을 생성해 표시한다.

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
- 현재 클라이언트는 오늘의 엽서 RPC 응답으로 받은 `image_path`에 대해서만 signed URL을 요청한다.
- Storage RLS는 작성자 본인 또는 해당 엽서를 배정받은 수신자만 관련 object를 읽을 수 있게 제한한다.
- 수신자 화면에는 작성자 계정, 이메일, 소셜 프로필, `author_id`를 노출하지 않는다.

## 사용자 사후 액션

받은 엽서에 대해 사용자는 관심없음, 개인 숨김, 신고를 생성할 수 있다.

### 관심없음/숨김

```ts
await supabase.from('postcard_user_actions').insert({
  postcard_id: postcardId,
  delivery_id: deliveryId,
  user_id: user.id,
  action_type: 'not_interested', // 또는 'hidden'
})
```

### 동작 규칙

- `action_type`은 `not_interested`, `hidden`만 허용한다.
- 사용자는 본인이 받은 delivery에 대해서만 액션을 생성할 수 있다.
- `hidden`은 해당 사용자 화면에서만 제외하기 위한 개인 액션이며, 엽서 자체 `status`를 바꾸지 않는다.
- 같은 사용자는 같은 엽서에 같은 액션을 한 번만 생성할 수 있다.

### 신고

```ts
await supabase.from('postcard_reports').insert({
  postcard_id: postcardId,
  delivery_id: deliveryId,
  reporter_id: user.id,
  reason: reason.trim(),
})
```

### 동작 규칙

- 사용자는 본인이 받은 delivery에 대해서만 신고할 수 있다.
- `reason`은 공백 제거 후 1자 이상 500자 이하여야 한다.
- 신고 상태 기본값은 `open`이다.
- 같은 사용자는 같은 엽서를 한 번만 신고할 수 있다.

## 관리자 처리

관리자 권한은 `public.admin_users` 테이블에 현재 사용자 ID가 있는지로 판별한다. 관리자 목록 등록은 운영자가 SQL 또는 Supabase Studio에서 수행한다.
최초 관리자 1명은 수동으로 등록하고, 이후에는 관리자 화면에서 다른 관리자 권한을 부여하거나 회수한다.

### 대기 엽서 승인

```ts
await supabase.rpc('approve_postcard', {
  target_postcard_id: postcardId,
})
```

### 대기 엽서 반려

```ts
await supabase.rpc('reject_postcard', {
  target_postcard_id: postcardId,
  rejection_note: note,
})
```

### 엽서 숨김

```ts
await supabase.rpc('hide_postcard', {
  target_postcard_id: postcardId,
  hide_note: note,
})
```

### 신고 처리

```ts
await supabase.rpc('resolve_postcard_report', {
  target_report_id: reportId,
  next_status: 'actioned',
  should_hide_postcard: true,
  note,
})
```

### 관리자 목록과 권한 관리

```ts
await supabase.rpc('list_admin_users')

await supabase.rpc('invite_admin_user', {
  target_email: 'admin@example.com',
})

await supabase.rpc('remove_admin_user', {
  target_user_id: userId,
})

await supabase.rpc('suspend_user', {
  target_user_id: userId,
  reason,
})

await supabase.rpc('unsuspend_user', {
  target_user_id: userId,
})
```

### 동작 규칙

- 일반 사용자는 관리자 조회 정책과 관리자 RPC를 사용할 수 없다.
- 관리자는 `pending` 엽서를 `approved` 또는 `rejected`로 처리할 수 있다.
- 관리자는 `approved` 또는 `pending` 엽서를 `hidden`으로 처리할 수 있다.
- 신고 처리 상태는 `reviewed`, `dismissed`, `actioned`만 허용한다.
- 관리자 처리 시 `moderated_by`, `moderated_at`, `handled_by`, `handled_at`이 기록된다.
- `hidden` 상태 엽서는 오늘의 엽서 RPC와 수신자 이미지 signed URL 정책에서 제외된다.
- 관리자는 자기 자신의 관리자 권한을 회수할 수 없다.
- 마지막 관리자 권한은 회수할 수 없다.
- 관리자는 올바르지 않은 사용자를 정지하거나 정지 해제할 수 있다.

## 주요 오류 상황

- Supabase 환경 변수가 없으면 웹앱은 설정 필요 화면을 표시한다.
- 소셜 provider 설정이 없거나 redirect URL이 맞지 않으면 로그인에 실패한다.
- 정지된 사용자가 쓰기/받기/신고/업로드를 시도하면 권한 오류가 발생한다.
- Storage 버킷이나 정책이 없으면 사진 업로드에 실패한다.
- 승인된 배정 가능 엽서가 없으면 오늘의 엽서 받기에 실패한다.
- signed URL 생성 권한이 없으면 이미지 표시가 실패한다.
- 일반 사용자가 관리자 RPC를 호출하면 권한 오류가 발생한다.
- 이미 처리된 신고를 다시 처리하거나, 대기 상태가 아닌 엽서를 승인/반려하면 오류가 발생한다.

## 미구현 또는 보류 범위

- Android/Spring Boot REST API.
- presigned URL 기반 외부 오브젝트 스토리지 직접 업로드.
- 별도 엽서 보관/북마크, 반응.
- 감성 태그는 당장 구현하지 않는다. 엽서 작성은 문장과 사진 중심으로 유지하고, 태그 기반 분류나 추천은 장기 후보로 보류한다.
- 고아 파일 정기 정리.
- 차단 기능은 우선 구현하지 않는다. 신고, 개인 숨김, 관리자 숨김, 사용자 정지로 기본 관리 흐름을 대응한다.
- 계절 테마는 MVP 우선순위에서 제외하고 장기 UI 확장 후보로 보류한다.
- 푸시 알림.
