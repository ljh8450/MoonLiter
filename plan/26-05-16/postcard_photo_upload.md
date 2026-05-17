# 엽서 사진 업로드 MVP 구현 계획

## 목표

React + Vite 웹앱에서 사진 1장과 감성 문장을 함께 담아 엽서를 작성하고, 수신자가 오늘의 엽서에서 사진과 문장을 함께 확인할 수 있게 한다.

이 구현 단위는 초기 MVP의 우선 경로인 웹앱, Supabase Postgres, Supabase Storage 흐름을 기준으로 한다. Android, Spring Boot, MinIO, AWS S3 기반 presigned URL 발급 경로는 초기 MVP 이후 확장 후보로 보류한다.

## 현재 구현 상태

- React + Vite 웹앱에 사진 선택, 미리보기, 선택 취소, MIME type/10MB 검증이 구현되어 있다.
- 작성 시 Supabase Storage `postcard-images` private bucket에 `postcards/{userId}/{uuid}.{ext}` 경로로 업로드한 뒤 `postcards` row를 생성한다.
- DB insert 실패 시 방금 업로드한 object 삭제를 시도한다.
- `postcards`에는 `image_path`, `image_content_type`, `image_byte_size`, `status`가 반영되어 있다.
- 새 엽서는 `pending`으로 생성되고, `assign_today_postcard()`는 `approved` 엽서만 배정한다.
- 오늘의 엽서 화면은 RPC 응답의 `image_path`로 1시간짜리 signed URL을 생성해 이미지를 표시한다.
- 관리자 승인 UI가 구현되어 있어 실제 수신 테스트 전 `/admin` 화면에서 `approved` 상태로 변경할 수 있다.
- 이 MVP 구현 계획의 프론트엔드/데이터 작업과 reviewer 점검은 완료되었다.
- reviewer 점검에서 확인된 `image_path` 소유권 검증, 신고 상세 이미지 표시, 표시 불가 delivery 안내는 보완되었다.

## 맥락

- 상위 계획의 현재 방향은 React + Vite + PWA 웹앱, Supabase Auth, Supabase Postgres, Supabase Storage, Cloudflare Pages 배포다.
- 데이터, 인증, 이미지 저장은 Supabase를 우선 사용한다.
- 첫 사진 업로드 MVP는 운영 완성형 미디어 파이프라인이 아니라, 웹앱에서 단일 이미지 업로드와 엽서 연결이 실제로 되는지 검증하는 세로 흐름으로 제한한다.
- Spring Boot가 업로드 URL을 발급하고 Android가 presigned URL로 PUT 업로드하는 방식은 이 계획의 구현 경로가 아니다.

## 범위

- Supabase Storage
  - 엽서 이미지 저장용 private bucket을 사용한다.
  - 웹앱은 선택한 이미지 1장을 Supabase Storage에 직접 업로드한다.
  - storage path는 외부 공개 식별자가 아니라 DB에 저장하는 내부 참조값으로 관리한다.
- Supabase Postgres
  - MVP에서는 엽서당 이미지 1장만 허용한다.
  - 별도 `media_files` 테이블 없이 `postcards` row에 이미지 메타데이터를 nullable 컬럼으로 저장한다.
  - 추천 컬럼은 기존 Supabase 스키마 명명에 맞춰 `author_id`, `status`, `image_path`, `image_content_type`, `image_byte_size`다.
  - 새 엽서는 기본적으로 `pending` 상태로 생성하고, 승인된 엽서만 전달 대상에 포함할 수 있게 설계한다.
- 웹앱 작성 흐름
  - 엽서 작성 화면에서 사진 1장을 선택한다.
  - 선택 직후 content type과 byte size를 검증한다.
  - 작성 버튼 클릭 시 Storage 업로드 후 엽서 row를 생성한다.
  - Storage 업로드 성공 후 DB insert가 실패하면 방금 업로드한 object 삭제를 시도한다.
- 웹앱 조회 흐름
  - 오늘의 엽서에 이미지 path가 있으면 signed URL을 생성해 표시한다.
  - 이미지가 없는 엽서는 기존 텍스트 화면을 유지한다.
  - 수신자 화면에는 작성자 계정, 이메일, 소셜 프로필, `author_id`를 노출하지 않는다.
- 문서
  - `/docs/ddl.sql`에 Supabase Postgres 스키마와 필요한 RLS 정책 방향을 반영한다.
  - `/docs/api.md` 또는 Supabase 데이터 흐름 문서에 Storage 업로드, 엽서 작성, 오늘의 엽서 조회 흐름을 반영한다.

## 제외 범위

- Android 사진 선택, 업로드, 조회 UI.
- Spring Boot 미디어 도메인, presigned URL 발급 API, 업로드 완료 등록 API.
- MinIO, AWS S3, LocalStack 개발 환경 구성.
- 별도 `media_files` 테이블과 `postcards.media_file_id` 연결 구조.
- 다중 이미지 업로드.
- 이미지 리사이징, 썸네일 생성, EXIF 제거.
- 자동 검수, 수동 관리자 검수 화면.
- CDN, 캐시 정책 고도화.
- 업로드 취소, 재시도 큐, 백그라운드 업로드.
- 사진 필터, 보정, 크롭 편집.
- 운영 인증 고도화.

## 현재 결정사항

- 초기 구현 경로는 웹/Supabase MVP다.
- 이미지 저장은 Supabase Storage를 사용한다.
- 엽서 이미지 bucket은 private bucket을 기본으로 한다.
- 조회 시 `image.url`은 Supabase Storage signed URL로 생성한다.
- DB에는 공개 URL이 아니라 `image_path`를 저장한다.
- MVP에서는 별도 `media_files` 테이블을 만들지 않고 `postcards`에 이미지 nullable 컬럼을 둔다.
- 엽서 row에는 작성자 식별을 위한 `author_id`를 둔다.
- 새 엽서는 기본적으로 `pending` 상태로 생성하고, `approved` 상태만 랜덤 전달 대상에 포함한다.
- 엽서당 이미지는 1장만 허용한다.
- 업로드 가능한 최대 byte size는 MVP에서 10MB로 제한한다.
- 허용 이미지 content type은 `image/jpeg`, `image/png`, `image/webp`로 제한한다.
- Storage path는 `postcards/{auth.uid()}/{uuid}.{ext}` 형식으로 만들어 충돌과 추적 정리를 피할 수 있게 한다.
- MVP 인증은 Supabase 소셜/이메일 로그인을 사용하며, 비로그인 익명 작성 기능은 제공하지 않는다.
- 작성/수신 기록과 signed URL 권한 확인을 위해 최소 수신 기록 테이블을 MVP에 포함한다.
- 사용자는 `author_id = auth.uid()`인 엽서만 직접 생성할 수 있다.
- 클라이언트는 엽서를 직접 `approved` 상태로 생성하거나 변경할 수 없다.
- 사용자는 `postcards/{auth.uid()}/...` 경로에만 이미지를 업로드할 수 있다.
- signed URL은 현재 클라이언트가 오늘의 엽서 RPC 응답으로 받은 `image_path`에 대해 직접 생성한다.
- Storage read policy는 작성자 본인 또는 해당 엽서를 배정받은 수신자만 관련 object를 읽을 수 있게 제한한다.
- 고아 파일 정기 정리는 MVP 이후 인증/관리자 확장 계획에서 구현하고, MVP에서는 DB insert 실패 시 즉시 cleanup만 시도한다.

## 작업

- [x] reviewer 담당: 계획 대비 구현과 리스크를 리뷰한다 - 검증: RLS, Storage policy, 고아 파일 처리, signed URL 만료, 기존 텍스트 엽서 회귀가 심각도별로 정리된다. 완료: `image_path` 소유권 검증, 신고 상세 이미지 표시, 표시 불가 delivery 안내를 보완했고 `npm run build`로 검증했다.

## 구현 순서

1. reviewer 점검을 진행한다 - 완료: RLS, Storage policy, 고아 파일 처리, signed URL 만료, 기존 텍스트 엽서 회귀를 심각도별로 정리했다.
2. reviewer 결과에서 구현 보완이 필요한 항목을 반영한다 - 완료: `image_path` 소유권 검증, 신고 상세 이미지 표시, 표시 불가 delivery 안내를 구현하고 문서를 갱신했다.

## 의존성

- React + Vite 웹앱이 동작해야 한다.
- Supabase 프로젝트, URL, anon key가 준비되어야 한다.
- Supabase Auth의 MVP 사용자 식별 방식이 정해져야 한다.
- Supabase Postgres 스키마와 RLS 정책을 적용할 수 있어야 한다.
- Supabase Storage private bucket을 만들 수 있어야 한다.
- 관리자 승인 계획과 정합성을 맞춘 `postcards.status` 값과 전이 규칙이 필요하다.
- Cloudflare Pages 환경 변수에 Supabase URL과 anon key를 등록할 수 있어야 한다.

## 미정 질문

- signed URL 만료 시간은 현재 1시간으로 구현되어 있다.
- 이미지 없는 오늘의 엽서는 `image_path`, `image_content_type`, `image_byte_size`를 `null`로 반환한다.
- 이메일 매직 링크와 자체 이메일 로그인은 `plan/26-05-16/auth_history_reports_admin.md` 범위에서 구현되어 있다.

## 추가 구현 고민사항

- private bucket을 사용하므로 이미지를 표시할 때 public URL을 저장하지 말고, 조회 시점에 signed URL을 생성해야 한다.
- signed URL이 만료되면 오늘의 엽서 데이터를 다시 조회해 새 URL을 받는 흐름을 전제로 한다.
- 10MB 제한은 UI 선택 직후와 업로드 직전 모두에서 같은 상수로 검증한다.
- Supabase Storage upload에는 일반 DB insert와 다른 오류가 발생할 수 있으므로 실패 메시지를 업로드 실패와 엽서 저장 실패로 구분한다.
- Storage path는 원본 파일명을 그대로 쓰지 않고 UUID와 안전한 확장자를 사용한다.
- DB insert 실패 후 cleanup 삭제가 실패할 수 있으므로, path 생성 규칙과 생성 시각 기준으로 고아 파일을 추적할 수 있게 하되 정기 정리 구현은 인증/관리자 확장 계획으로 미룬다.
- `image_content_type`과 `image_byte_size`는 클라이언트 검증값이므로 보안 검증의 유일한 근거로 보지 않는다.
- RLS와 Storage policy는 MVP라도 누가 업로드하고 누가 조회할 수 있는지 명시해야 한다.
- `postcards.status`는 DB 기본값을 `pending`으로 두고, 클라이언트 insert 값으로 `approved`를 받을 수 없게 한다.
- 랜덤 전달 조회는 `status = 'approved'` 조건을 반드시 포함한다.
- 수신자에게 전달되는 view model에는 작성자 식별 정보를 노출하지 않는다.
- 현재 RPC 응답에는 내부 `image_path`가 포함되며, 클라이언트는 이 값으로 signed URL을 생성한다.

## 완료 기준

- 사용자가 웹앱에서 사진 1장과 문장을 함께 엽서로 작성할 수 있다.
- 이미지는 Supabase Storage private bucket에 저장된다.
- 엽서 row에는 이미지 공개 URL이 아니라 `image_path`와 필요한 메타데이터가 저장된다.
- 새 엽서는 기본적으로 `pending` 상태로 생성되고, 클라이언트가 직접 `approved` 상태를 만들 수 없다.
- 랜덤 전달 대상은 `approved` 상태 엽서로 제한된다.
- 오늘의 엽서 화면에서 이미지가 있는 엽서는 signed URL로 사진과 문장이 함께 표시된다.
- signed URL 생성은 Storage read policy에 의해 작성자 본인과 배정받은 수신자로 제한된다.
- 수신자 화면에는 작성자 식별 정보가 노출되지 않는다.
- 이미지 없는 기존 텍스트 엽서 작성/조회 흐름이 유지된다.
- `/docs/ddl.sql`과 API/데이터 흐름 문서가 구현 상태와 일치한다.

## 다음 인계

- 운영/데이터: 실제 Supabase 프로젝트에 최신 마이그레이션을 적용하고 테스트용 엽서를 `approved` 상태로 준비한다.
- reviewer: 구현 후 RLS/policy, 고아 파일 처리, signed URL 만료 UX, 기존 텍스트 엽서 회귀를 검토한다.
- 완료 처리: reviewer 점검과 확인된 보완 구현은 완료되었다. 고아 파일 정기 정리는 `auth_history_reports_admin.md`에서 Edge Function과 운영 문서 기준으로 완료 처리되었다.
