# 프로젝트 계획

## 현재 방향

- 초기 MVP는 React + Vite 기반 웹앱으로 출시한다.
- 배포 대상은 Cloudflare Pages다.
- 데이터, 인증, 이미지 저장은 Supabase를 우선 사용한다.
- 웹앱은 PWA로 설치 가능한 형태를 목표로 한다.
- Android, iOS, Spring Boot, AWS EC2 배포는 초기 MVP 이후 확장 후보로 보류한다.
- 익명 또는 최소 인증 기반으로 엽서 작성, 사진 업로드, 랜덤 전달, 오늘의 엽서 수신 흐름을 먼저 검증한다.

## 목표 아키텍처

```text
React + Vite + PWA 웹앱
  -> Supabase Auth
  -> Supabase Postgres
  -> Supabase Storage
  -> Cloudflare Pages 배포
```

## 남은 플랫폼 계획

- Supabase Auth 익명 로그인 또는 최소 로그인 방식을 결정한다.
- Supabase Postgres 스키마와 RLS 정책을 MVP 흐름에 맞게 정리한다.
- Supabase Storage 버킷과 업로드/조회 정책을 정리한다.
- Cloudflare Pages 빌드/환경 변수 설정을 정리한다.
- 기존 Android/Spring Boot 구현은 참고 구현으로 유지하되 초기 MVP 경로에서는 제외한다.

## 초기 제품 우선순위

- 익명 감성 엽서 작성.
- 사진 업로드.
- 랜덤 엽서 전달.
- 오늘의 엽서 수신.
- 엽서 저장.
- 계절 테마.
- 감성 태그.
- 신고, 차단, 숨김, 검수 상태 같은 기본 검수/관리 기능.

## 미정 질문

- Supabase Auth는 익명 로그인을 사용할지, 이메일 매직 링크를 사용할지.
- Supabase Storage 버킷을 private으로 두고 signed URL로 조회할지, 제한된 public bucket으로 시작할지.
- Cloudflare Pages 배포 환경에서 Supabase URL과 anon key를 어떤 환경 이름으로 관리할지.
- 기존 Android/Spring Boot 코드를 언제 정리하거나 별도 브랜치로 분리할지.

## 직후 구현 단위

- React + Vite 웹앱과 Supabase 기반 초기 MVP로 마이그레이션한다.
- 범위는 Vite 웹앱 스캐폴드, PWA 기본 파일, Supabase 클라이언트 연결, Supabase SQL 마이그레이션, Cloudflare Pages 배포 설정이다.
- 제외 범위는 운영 도메인 연결, 고급 인증, 관리자 검수, 푸시 알림, Android/iOS 앱 유지보수다.
- 완료 기준은 웹앱이 빌드되고, Supabase 마이그레이션 SQL로 엽서 작성/사진 저장/오늘의 엽서 수신 데이터 흐름을 구성할 수 있는 것이다.
- 세부 계획은 `plan/26-05-13/web_supabase_migration.md`에서 관리한다.

## 다음 계획 작업

- Supabase 프로젝트에 SQL 마이그레이션을 적용한다.
- Cloudflare Pages 프로젝트에 `frontend` 앱을 연결하고 환경 변수를 등록한다.
- 웹앱에서 사진 포함 엽서 작성, 오늘의 엽서 받기, 다시 보기 흐름을 검증한다.
- 첫 웹 MVP 완료 후 저장/반응, 검수, 인증 고도화 순서로 확장한다.
