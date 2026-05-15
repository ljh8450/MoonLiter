# 웹 Supabase MVP 구현 계획

## 목표

React + Vite 웹앱에서 Supabase Auth, Postgres, Storage를 사용해 사진 포함 엽서 작성과 오늘의 엽서 수신 흐름을 검증한다.

## 맥락

- 현재 프로젝트의 우선 구현 경로는 웹/Supabase MVP다.
- Android/Spring Boot 기반 사진 업로드는 향후 구현 후보로 보류한다.
- 웹앱은 익명 로그인, 사진 선택, Supabase Storage 업로드, 엽서 작성, 오늘의 엽서 수신 흐름을 우선 검증한다.

## 범위

- Supabase Auth 익명 로그인.
- Supabase Postgres 기반 `profiles`, `postcards`, `postcard_deliveries`.
- Supabase Storage `postcard-images` 버킷을 통한 사진 업로드.
- 오늘의 엽서 배정 RPC.
- React + Vite 웹앱의 엽서 작성, 사진 미리보기, 수신 화면.
- Cloudflare Pages 배포에 필요한 환경 변수 정리.

## 제외 범위

- Android/iOS 앱 구현.
- Spring Boot API와 presigned URL 기반 업로드.
- 운영 인증 고도화.
- 관리자 검수 화면.
- 신고, 차단, 저장, 반응 기능.
- 이미지 리사이징, 썸네일, EXIF 제거.

## 현재 결정사항

- 초기 MVP는 Supabase Auth 익명 로그인을 사용한다.
- 사진은 Supabase Storage의 `postcard-images` private 버킷에 저장한다.
- DB에는 이미지 공개 URL이 아니라 Storage object path인 `image_path`를 저장한다.
- 수신 화면에서는 `image_path`로 signed URL을 생성해 표시한다.
- Android/Spring Boot용 `media_files`, `postcards.media_file_id` 설계는 향후 경로에서 다시 다룬다.

## 작업

- [ ] Supabase 마이그레이션을 최신 웹 MVP 스키마와 맞춘다 - 검증: `profiles`, `postcards`, `postcard_deliveries`, Storage 버킷, RLS 정책, `assign_today_postcard`가 포함된다.
- [ ] 웹앱 Supabase 환경 변수 문서를 정리한다 - 검증: 로컬과 Cloudflare Pages에서 필요한 `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`가 문서화된다.
- [ ] 웹앱 사진 포함 엽서 작성 흐름을 검증한다 - 검증: 사진 1장과 500자 이하 문장을 저장할 수 있다.
- [ ] 오늘의 엽서 수신 흐름을 검증한다 - 검증: 자기 엽서를 제외하고 하루 1장 배정되며 사진이 있으면 signed URL로 표시된다.
- [ ] 문서를 웹/Supabase 우선 방향으로 정리한다 - 검증: `/docs/api.md`, `/docs/PostgreSQL.sql`, `/plan/plan.md`가 Android/Spring Boot 우선 흐름으로 오해되지 않는다.
- [ ] 빌드 검증을 수행한다 - 검증: `frontend`에서 `npm run build`가 성공한다.

## 의존성

- Supabase 프로젝트가 있어야 한다.
- Supabase Auth에서 익명 로그인이 활성화되어야 한다.
- Supabase 마이그레이션이 적용되어야 한다.
- Cloudflare Pages 또는 로컬 `.env`에 Supabase URL과 anon key가 등록되어야 한다.

## 미정 질문

- Storage 읽기 정책을 MVP 기간 동안 authenticated 전체 읽기로 둘지, RPC 기반으로 수신자에게만 signed URL을 발급할지 결정해야 한다.
- 첫 MVP 이후 엽서 저장, 반응, 신고, 차단 중 어느 기능을 먼저 붙일지 결정해야 한다.
- Android/Spring Boot 구현을 유지할지, 별도 브랜치 또는 후속 마일스톤으로 분리할지 결정해야 한다.

## 완료 기준

- 웹앱이 빌드된다.
- 익명 사용자가 사진 1장과 문장을 포함한 엽서를 작성할 수 있다.
- 다른 익명 사용자가 오늘의 엽서로 사진과 문장을 받을 수 있다.
- Supabase 마이그레이션과 문서가 웹 MVP 구현 방향과 일치한다.
