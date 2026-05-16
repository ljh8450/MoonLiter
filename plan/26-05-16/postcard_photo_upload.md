# 엽서 사진 업로드 MVP 구현 계획

## 목표

React + Vite 웹앱에서 사진 1장과 감성 문장을 함께 담아 엽서를 작성하고, 수신자가 오늘의 엽서에서 사진과 문장을 함께 확인할 수 있게 한다.

이 구현 단위는 초기 MVP의 우선 경로인 웹앱, Supabase Postgres, Supabase Storage 흐름을 기준으로 한다. Android, Spring Boot, MinIO, AWS S3 기반 presigned URL 발급 경로는 초기 MVP 이후 확장 후보로 보류한다.

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
  - 추천 컬럼은 `image_path`, `image_content_type`, `image_byte_size`다.
- 웹앱 작성 흐름
  - 엽서 작성 화면에서 사진 1장을 선택한다.
  - 선택 직후 content type과 byte size를 검증한다.
  - 작성 버튼 클릭 시 Storage 업로드 후 엽서 row를 생성한다.
  - Storage 업로드 성공 후 DB insert가 실패하면 방금 업로드한 object 삭제를 시도한다.
- 웹앱 조회 흐름
  - 오늘의 엽서에 이미지 path가 있으면 signed URL을 생성해 표시한다.
  - 이미지가 없는 엽서는 기존 텍스트 화면을 유지한다.
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
- 엽서당 이미지는 1장만 허용한다.
- 업로드 가능한 최대 byte size는 MVP에서 10MB로 제한한다.
- 허용 이미지 content type은 `image/jpeg`, `image/png`, `image/webp`로 제한한다.
- Storage path는 사용자 식별자와 UUID를 포함해 충돌과 추적 정리를 피할 수 있게 만든다.

## 작업

- [ ] 기획/문서 담당: 웹/Supabase 사진 업로드 데이터 흐름을 확정한다 - 검증: Android/Spring Boot 경로와 웹/Supabase 경로가 문서에서 분리되어 혼동되지 않는다.
- [ ] 백엔드/데이터 담당: Supabase Postgres 스키마를 확정한다 - 검증: `postcards`에 `image_path`, `image_content_type`, `image_byte_size` nullable 컬럼이 포함되고 이미지 없는 기존 엽서 row가 유지된다.
- [ ] 백엔드/데이터 담당: Supabase Storage bucket과 path 규칙을 확정한다 - 검증: `postcard-images` 같은 private bucket과 `postcards/{userId}/{uuid}.{ext}` 형식의 path 규칙이 문서화된다.
- [ ] 백엔드/데이터 담당: Supabase RLS와 Storage policy를 설계한다 - 검증: 사용자는 허용된 경로에만 업로드할 수 있고, 임의의 타인 파일을 직접 조회할 수 없다.
- [ ] 프론트엔드 담당: 엽서 작성 화면에 사진 선택 UI를 추가한다 - 검증: 이미지 1장을 선택하면 미리보기가 표시되고 선택 취소 또는 변경이 가능하다.
- [ ] 프론트엔드 담당: 선택한 파일의 content type과 byte size를 검증한다 - 검증: `image/jpeg`, `image/png`, `image/webp` 외 파일과 10MB 초과 파일은 업로드 전에 거부된다.
- [ ] 프론트엔드 담당: Supabase Storage 업로드 흐름을 구현한다 - 검증: 작성 버튼 클릭 시 파일이 private bucket에 업로드되고 storage path가 반환된다.
- [ ] 프론트엔드 담당: Storage 업로드 성공 후 엽서 row 생성을 연결한다 - 검증: 사진과 문장을 포함한 엽서 row에 `image_path`와 이미지 메타데이터가 저장된다.
- [ ] 프론트엔드 담당: DB insert 실패 시 Storage cleanup을 시도한다 - 검증: 업로드 성공 후 엽서 저장 실패 상황에서 방금 업로드한 object 삭제가 호출된다.
- [ ] 프론트엔드 담당: 오늘의 엽서 화면에 이미지 표시를 추가한다 - 검증: `image_path`가 있으면 signed URL로 사진과 문장이 함께 보이고, 이미지 없는 엽서는 기존 문장 화면이 유지된다.
- [ ] 문서 담당: `/docs/ddl.sql`과 API/데이터 흐름 문서를 최신화한다 - 검증: Supabase schema, RLS, Storage bucket, signed URL 생성, 오류 케이스가 문서에 포함된다.
- [ ] reviewer 담당: 계획 대비 구현과 리스크를 리뷰한다 - 검증: RLS, Storage policy, 고아 파일 처리, signed URL 만료, 기존 텍스트 엽서 회귀가 심각도별로 정리된다.

## 구현 순서

1. Supabase 스키마와 Storage 정책을 먼저 확정한다 - 검증: `postcards` 이미지 컬럼, private bucket, path 규칙, RLS/Storage policy 초안이 문서화된다.
2. 웹 엽서 작성 화면에 사진 선택과 파일 검증을 추가한다 - 검증: 선택, 미리보기, 선택 취소, 타입/크기 거부가 브라우저에서 동작한다.
3. Supabase Storage 업로드와 엽서 row 생성을 연결한다 - 검증: 작성 버튼 클릭으로 Storage 업로드 후 DB insert가 순서대로 성공한다.
4. 실패 처리와 cleanup을 추가한다 - 검증: Storage 업로드 후 DB insert 실패 시 업로드 object 삭제가 시도되고 사용자에게 실패 메시지가 표시된다.
5. 오늘의 엽서 조회 화면에 signed URL 이미지 표시를 붙인다 - 검증: 이미지 있는 엽서와 이미지 없는 엽서가 모두 정상 렌더링된다.
6. 문서와 리뷰를 완료한다 - 검증: `/docs/ddl.sql`, API/데이터 흐름 문서, reviewer 결과가 현재 구현과 일치한다.

## 의존성

- React + Vite 웹앱이 동작해야 한다.
- Supabase 프로젝트, URL, anon key가 준비되어야 한다.
- Supabase Auth의 MVP 사용자 식별 방식이 정해져야 한다.
- Supabase Postgres 스키마와 RLS 정책을 적용할 수 있어야 한다.
- Supabase Storage private bucket을 만들 수 있어야 한다.
- Cloudflare Pages 환경 변수에 Supabase URL과 anon key를 등록할 수 있어야 한다.

## 미정 질문

- Supabase Auth는 익명 로그인을 사용할지, 이메일 매직 링크를 사용할지 결정해야 한다.
- signed URL 만료 시간을 결정해야 한다. MVP에서는 1시간을 우선 후보로 둔다.
- Storage path의 사용자 식별자에 Supabase `auth.uid()`를 그대로 사용할지, 앱 내부 익명 사용자 ID를 사용할지 결정해야 한다.
- 이미지 없는 오늘의 엽서 응답 또는 view model에서 `image`를 생략할지 `null`로 둘지 결정해야 한다.
- Storage 업로드 성공 후 DB insert 실패로 남은 고아 파일을 정기적으로 정리할 방법을 결정해야 한다.

## 추가 구현 고민사항

- private bucket을 사용하므로 이미지를 표시할 때 public URL을 저장하지 말고, 조회 시점에 signed URL을 생성해야 한다.
- signed URL이 만료되면 오늘의 엽서 데이터를 다시 조회해 새 URL을 받는 흐름을 전제로 한다.
- 10MB 제한은 UI 선택 직후와 업로드 직전 모두에서 같은 상수로 검증한다.
- Supabase Storage upload에는 일반 DB insert와 다른 오류가 발생할 수 있으므로 실패 메시지를 업로드 실패와 엽서 저장 실패로 구분한다.
- Storage path는 원본 파일명을 그대로 쓰지 않고 UUID와 안전한 확장자를 사용한다.
- DB insert 실패 후 cleanup 삭제가 실패할 수 있으므로, path 생성 규칙과 생성 시각 기준으로 고아 파일을 추적할 수 있게 한다.
- `image_content_type`과 `image_byte_size`는 클라이언트 검증값이므로 보안 검증의 유일한 근거로 보지 않는다.
- RLS와 Storage policy는 MVP라도 누가 업로드하고 누가 조회할 수 있는지 명시해야 한다.

## 완료 기준

- 사용자가 웹앱에서 사진 1장과 문장을 함께 엽서로 작성할 수 있다.
- 이미지는 Supabase Storage private bucket에 저장된다.
- 엽서 row에는 이미지 공개 URL이 아니라 `image_path`와 필요한 메타데이터가 저장된다.
- 오늘의 엽서 화면에서 이미지가 있는 엽서는 signed URL로 사진과 문장이 함께 표시된다.
- 이미지 없는 기존 텍스트 엽서 작성/조회 흐름이 유지된다.
- `/docs/ddl.sql`과 API/데이터 흐름 문서가 구현 상태와 일치한다.

## 다음 인계

- 데이터/백엔드: `postcards` 이미지 컬럼, RLS, Storage bucket, Storage policy, signed URL 생성 방식을 확정한다.
- 프론트엔드: 사진 선택, 미리보기, 파일 검증, Supabase Storage upload, DB insert, cleanup, 이미지 표시 UI를 구현한다.
- 문서: Supabase schema, Storage bucket, private/signed URL 정책, 오류 케이스를 문서에 반영한다.
- reviewer: 구현 후 RLS/policy, 고아 파일 처리, signed URL 만료 UX, 기존 텍스트 엽서 회귀를 검토한다.
