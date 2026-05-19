# 운영 절차

이 문서는 React + Vite + Supabase + Cloudflare Pages 기준 운영 배포와 점검 절차를 기록한다.

## 운영 결정사항

- 고아 파일은 다른 엽서나 새 사용자에게 재연결하지 않고 삭제한다.
- 고아 파일 보존 기간은 기본 48시간이다.
- 고아 파일 정리는 Supabase Edge Function `cleanup-orphan-postcard-images`로 수행한다.
- 운영 배포 전에는 고아 파일 정리를 dry-run으로 먼저 확인한다.
- 최초 관리자 1명은 Supabase Studio 또는 SQL Editor에서 수동 등록한다.

## Supabase 준비

1. Supabase 프로젝트를 만든다.
2. `VITE_SUPABASE_URL`에 사용할 Project URL을 확인한다.
3. `VITE_SUPABASE_ANON_KEY`에 사용할 anon public key를 확인한다.
4. Edge Function 배포와 운영 정리에 사용할 service role key는 서버/운영 환경에만 보관한다.

## 마이그레이션 적용

`supabase/migrations`의 SQL을 시간순으로 적용한다.

현재 필수 마이그레이션:

```text
202605130001_initial_web_mvp.sql
202605160001_postcard_image_storage_policy.sql
202605160002_postcard_approval_and_path_policy.sql
202605170001_admin_reports_actions.sql
202605170002_report_insert_policy.sql
202605170003_admin_user_management.sql
202605170004_fix_invite_admin_user.sql
202605170005_fix_invite_admin_user_upsert.sql
202605170006_fix_invite_admin_user_conflict.sql
202605170007_user_suspensions_and_login_required.sql
202605170008_image_path_and_suspended_users.sql
202605190001_phone_verification_trust_policies.sql
```

적용 후 확인할 항목:

- `postcard-images` bucket이 private이다.
- `postcard-images` bucket의 허용 MIME type은 `image/jpeg`, `image/png`, `image/webp`다.
- `postcard-images` bucket의 파일 크기 제한은 10MB다.
- `assign_today_postcard()`는 `approved` 상태만 반환한다.
- `list_suspended_users()` RPC가 관리자에게만 동작한다.
- 엽서 생성 시 `image_path`는 `postcards/{현재 사용자 ID}/...` 경로만 허용된다.
- 전화번호 인증이 완료되지 않았거나 정지된 사용자는 엽서 생성, 이미지 업로드, 오늘의 엽서 수신, 신고, 사용자 액션이 제한된다.

## Auth 설정

### 이메일

- Email provider를 활성화한다.
- 매직 링크 로그인을 사용한다.
- 이메일/비밀번호 로그인을 사용한다.
- 이메일 인증 필수 여부는 운영 정책에 맞춰 결정한다. 현재 로컬 설정은 인증 필수가 아니다.

### Google

- Google Cloud Console에서 OAuth Client를 만든다.
- Supabase Auth Google provider에 client ID와 secret을 등록한다.
- 승인된 redirect URL에 Supabase callback URL을 등록한다.

### Kakao

- Kakao Developers에서 애플리케이션을 만든다.
- 카카오 로그인 제품 설정을 활성화한다.
- Supabase Auth Kakao provider에 REST API key와 secret을 등록한다.
- redirect URL에 Supabase callback URL을 등록한다.

### 전화번호

- Supabase Auth Phone provider를 활성화한다.
- 운영 SMS provider를 설정한다.
- 웹앱의 전화번호 인증은 로그인한 사용자의 전화번호 변경 OTP(`phone_change`)를 사용한다.
- 인증 완료 후 `sync_my_phone_verification()` RPC가 `profiles.phone_verified_at`을 보정한다.

### Redirect URL

운영 도메인과 로컬 개발 URL을 Supabase Auth redirect allow-list에 등록한다.

```text
https://{운영 도메인}
http://localhost:5173
http://127.0.0.1:5173
```

## Cloudflare Pages 설정

Cloudflare Pages 프로젝트의 환경 변수에 다음 값을 등록한다.

```text
VITE_SUPABASE_URL=
VITE_SUPABASE_ANON_KEY=
```

빌드 설정:

```text
Root directory: frontend
Build command: npm run build
Build output directory: dist
```

`frontend/public/_headers`가 배포에 포함되어야 한다.

## 최초 관리자 등록

운영자가 Supabase Auth에서 최초 관리자 사용자를 만든 뒤, 해당 사용자의 UUID를 `admin_users`에 등록한다.

```sql
insert into public.profiles (id)
values ('{관리자 auth.users.id}')
on conflict (id) do nothing;

insert into public.admin_users (user_id)
values ('{관리자 auth.users.id}')
on conflict (user_id) do nothing;
```

이후 관리자 추가와 회수는 웹 관리자 화면에서 수행한다.

## 고아 파일 정리

### 대상

삭제 후보는 모두 만족해야 한다.

- Storage bucket이 `postcard-images`다.
- 경로가 `postcards/{userId}/...` 형식이다.
- 생성 후 48시간 이상 지났다.
- `public.postcards.image_path`에 연결되어 있지 않다.

### Edge Function 배포

```bash
supabase functions deploy cleanup-orphan-postcard-images
```

운영 환경 secret:

```bash
supabase secrets set CLEANUP_ORPHAN_POSTCARD_IMAGES_SECRET="{긴 임의 문자열}"
supabase secrets set CLEANUP_ORPHAN_DRY_RUN="true"
```

`SUPABASE_URL`과 `SUPABASE_SERVICE_ROLE_KEY`는 Supabase Edge Runtime에서 제공되는 값을 사용한다.

### Dry-run 확인

```bash
curl -X POST \
  "https://{project-ref}.functions.supabase.co/cleanup-orphan-postcard-images?dryRun=true&retentionHours=48" \
  -H "Authorization: Bearer {SUPABASE_ANON_KEY 또는 SERVICE_ROLE_KEY}" \
  -H "x-cleanup-secret: {CLEANUP_ORPHAN_POSTCARD_IMAGES_SECRET}"
```

응답에서 `candidateCount`와 `candidates`를 확인한다.

### 실제 삭제

dry-run 결과가 기대와 일치할 때만 실행한다.

```bash
curl -X POST \
  "https://{project-ref}.functions.supabase.co/cleanup-orphan-postcard-images?dryRun=false&retentionHours=48" \
  -H "Authorization: Bearer {SUPABASE_ANON_KEY 또는 SERVICE_ROLE_KEY}" \
  -H "x-cleanup-secret: {CLEANUP_ORPHAN_POSTCARD_IMAGES_SECRET}"
```

응답에서 `deletedCount`와 `deleted`를 확인한다.

### 정기 실행

추천 주기는 하루 1회다.

Supabase Dashboard의 Scheduled Functions 또는 외부 스케줄러에서 위 실제 삭제 요청을 하루 1회 호출한다.

운영 초기는 `CLEANUP_ORPHAN_DRY_RUN="true"`로 두고 후보 목록을 확인한 뒤, 안정화 후 `false`로 전환한다.

## 배포 후 검증 체크리스트

### 마이그레이션

- [ ] 최신 마이그레이션이 모두 적용되었다.
- [ ] `202605170008_image_path_and_suspended_users.sql`이 적용되었다.
- [ ] `202605190001_phone_verification_trust_policies.sql`이 적용되었다.
- [ ] `list_suspended_users()` RPC가 존재한다.
- [ ] `sync_my_phone_verification()` RPC가 존재한다.
- [ ] `postcards_image_metadata_pair_check` constraint가 존재한다.

### 로그인

- [ ] 이메일 매직 링크 로그인이 동작한다.
- [ ] 이메일/비밀번호 가입과 로그인이 동작한다.
- [ ] Google 로그인이 동작한다.
- [ ] Kakao 로그인이 동작한다.

### 엽서 작성

- [ ] 텍스트만 있는 엽서를 작성할 수 있다.
- [ ] 이미지가 있는 엽서를 작성할 수 있다.
- [ ] 10MB 초과 이미지는 차단된다.
- [ ] 허용되지 않은 MIME type은 차단된다.
- [ ] 다른 사용자 `image_path`로 엽서 insert를 시도하면 실패한다.

### 관리자

- [ ] `/admin`은 관리자만 접근할 수 있다.
- [ ] `pending` 엽서를 승인할 수 있다.
- [ ] `pending` 엽서를 반려할 수 있다.
- [ ] 신고 목록에서 사진, 본문, 작성자, 상태를 확인할 수 있다.
- [ ] 신고 엽서를 유지 처리할 수 있다.
- [ ] 신고 엽서를 숨김 처리할 수 있다.
- [ ] 사용자를 정지할 수 있다.
- [ ] 정지 사용자를 목록에서 확인하고 정지 해제할 수 있다.

### 수신자

- [ ] `approved` 엽서만 오늘의 엽서로 배정된다.
- [ ] 작성자 이메일, 이름, 소셜 프로필, `author_id`가 수신자 화면에 노출되지 않는다.
- [ ] 이미지가 있는 엽서는 signed URL로 표시된다.
- [ ] 이미지 없는 텍스트 엽서도 정상 표시된다.

### 정지 사용자

- [ ] 정지 사용자는 엽서를 작성할 수 없다.
- [ ] 정지 사용자는 오늘의 엽서를 받을 수 없다.
- [ ] 정지 사용자는 관심없음, 숨김, 신고를 생성할 수 없다.
- [ ] 정지 사용자는 이미지를 업로드할 수 없다.

### 고아 파일 정리

- [ ] dry-run에서 삭제 후보가 예상 범위와 일치한다.
- [ ] 테스트 object만 실제 삭제된다.
- [ ] 연결된 `postcards.image_path` object는 삭제되지 않는다.

## 미정 운영 정책

- 이메일/비밀번호 로그인에서 이메일 인증을 필수로 둘지 결정해야 한다.
- 관리자 승인 SLA를 둘지 결정해야 한다.
- 반려된 엽서를 작성자에게 계속 보여줄지 결정해야 한다.
- 반려 사유를 작성자에게 노출할지 결정해야 한다.
