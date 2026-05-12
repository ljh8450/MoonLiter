# React Vite Supabase 웹 MVP 마이그레이션 계획

## 목표

초기 MVP 방향을 Android/Spring Boot 중심에서 React + Vite 웹앱, Supabase, Cloudflare Pages, PWA 중심으로 전환한다.

## 맥락

- README의 핵심 경험은 사진과 짧은 감성 문장을 익명 엽서처럼 주고받는 것이다.
- 현재 저장소에는 Android 앱과 Spring Boot API가 있으나, 초기 배포 목표가 웹앱으로 변경되었다.
- 기존 관계형 도메인 구조를 살리기 위해 Firebase보다 Supabase를 우선 선택한다.
- Cloudflare Pages는 Vite 정적 빌드와 잘 맞고, PWA는 웹 MVP의 설치 경험을 제공한다.

## 범위

- `web/` 아래 React + Vite + TypeScript 앱을 추가한다.
- Supabase Auth, Postgres, Storage를 사용하는 MVP 데이터 흐름을 구성한다.
- Supabase SQL 마이그레이션을 추가한다.
- PWA manifest와 service worker 기본 구성을 추가한다.
- Cloudflare Pages 배포에 필요한 빌드 명령과 환경 변수 기준을 정리한다.

## 제외 범위

- 기존 Android 앱 삭제.
- 기존 Spring Boot API 삭제.
- 운영 도메인 연결.
- Cloudflare Functions 또는 Supabase Edge Functions 고도화.
- 관리자 검수, 신고/차단, 반응, 저장 기능.
- 푸시 알림과 백그라운드 동기화.

## 작업

- [ ] 프론트엔드 담당: `web/` React + Vite 앱을 생성한다 - 검증: `npm run build`가 성공한다.
- [ ] 프론트엔드 담당: PWA 기본 파일을 추가한다 - 검증: `manifest.webmanifest`와 service worker가 빌드 산출물에 포함된다.
- [ ] 백엔드/문서 담당: Supabase SQL 마이그레이션을 작성한다 - 검증: profiles, postcards, postcard_deliveries, storage bucket, RLS 정책이 포함된다.
- [ ] 프론트엔드 담당: Supabase 클라이언트와 환경 변수를 연결한다 - 검증: `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`가 없으면 화면에서 설정 필요 상태를 보여준다.
- [ ] 프론트엔드 담당: 엽서 작성과 사진 업로드 흐름을 구현한다 - 검증: 이미지 1장과 문장이 Supabase Storage/Postgres에 저장된다.
- [ ] 프론트엔드 담당: 오늘의 엽서 받기/다시 보기 흐름을 구현한다 - 검증: 자기 엽서를 제외하고 오늘의 엽서를 하나 받는다.
- [ ] 문서 담당: Cloudflare Pages 배포 설정을 문서화한다 - 검증: 빌드 명령, 출력 디렉터리, 환경 변수 이름이 명시된다.
- [ ] reviewer 담당: 계획 대비 구현과 배포 리스크를 리뷰한다 - 검증: Supabase RLS, Storage 정책, Cloudflare 환경 변수, PWA 동작 리스크가 정리된다.

## 의존성

- Supabase 프로젝트가 필요하다.
- Supabase Auth에서 익명 로그인을 활성화해야 한다.
- Cloudflare Pages 프로젝트가 필요하다.
- Cloudflare Pages 환경 변수에 `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`를 등록해야 한다.

## 미정 질문

- Supabase Auth는 익명 로그인으로 시작할지 이메일 매직 링크로 시작할지.
- Storage bucket은 private + signed URL로 둘지, MVP 속도를 위해 제한된 public bucket으로 둘지.
- 기존 Android/Spring Boot 구현은 보관할지 별도 디렉터리나 브랜치로 분리할지.

## 완료 기준

- `web/` 앱이 로컬에서 실행되고 프로덕션 빌드가 성공한다.
- Supabase SQL 마이그레이션으로 MVP 데이터 구조와 RLS 정책을 구성할 수 있다.
- 사용자가 사진 1장과 문장을 포함한 엽서를 작성할 수 있다.
- 다른 사용자가 오늘의 엽서를 받아 사진과 문장을 볼 수 있다.
- Cloudflare Pages 배포 설정값이 명확하다.
