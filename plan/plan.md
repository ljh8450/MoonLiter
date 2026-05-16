# 프로젝트 계획

## 현재 방향

- 초기 MVP는 React + Vite 기반 웹앱으로 출시한다.
- 배포 대상은 Cloudflare Pages다.
- 데이터, 인증, 이미지 저장은 Supabase를 우선 사용한다.
- 웹앱은 PWA로 설치 가능한 형태를 목표로 한다.
- Android, iOS, Spring Boot, AWS EC2 배포는 초기 MVP 이후 확장 후보로 보류한다.
- 소셜 로그인과 자체 로그인 기반으로 엽서 작성, 사진 업로드, 랜덤 전달, 오늘의 엽서 수신 흐름을 검증한다.
- 엽서 수신자는 발신자를 알 수 없는 익명성을 유지하되, 로그인 사용자는 본인이 작성한 엽서와 받은 엽서 기록을 지속적으로 조회할 수 있게 한다.
- 현재 스키마는 관리자 승인 기반 전달을 전제로 하며, 새 엽서는 `pending`으로 생성되고 `approved` 상태만 전달된다.

## 현재 구현 상태

- `frontend` 웹앱은 Supabase 소셜/이메일 로그인, 사진 1장 선택/검증/미리보기, Storage 업로드, 엽서 row 생성, 오늘의 엽서 RPC 호출, signed URL 이미지 표시를 구현했다.
- Supabase 마이그레이션은 `profiles`, `postcards`, `postcard_deliveries`, private `postcard-images` bucket, RLS/Storage policy, `assign_today_postcard()` RPC를 포함한다.
- 사진 없는 텍스트 엽서와 사진 포함 엽서 모두 같은 `postcards` 테이블을 사용한다.
- 관리자 승인 상태, 관리자 화면, 승인/반려 조작 UI는 구현되어 있다.
- 작성/수신 기록 목록, 신고, 사용자별 숨김/관심없음, 관리자 권한 관리, 사용자 정지는 구현되어 있다.
- 차단, 반응, 고아 파일 정기 정리는 아직 후속 범위다.

## 목표 아키텍처

```text
React + Vite + PWA 웹앱
  -> Supabase Auth
  -> Supabase Postgres
  -> Supabase Storage
  -> Cloudflare Pages 배포
```

## 남은 플랫폼 계획

- 이메일 매직 링크, 이메일/비밀번호, 구글, 카카오 로그인 기반 Supabase Auth 연동을 유지한다.
- 작성/수신 기록, 관심없음, 사용자별 숨김, 신고, 관리자 승인 상태를 포함하는 데이터 모델을 유지한다.
- 관리자 페이지 접근 권한은 별도 관리자 테이블을 기준으로 관리한다.
- 관리자는 새 엽서를 1차 검수해 승인/반려하고, 신고된 엽서는 사후 검토해 유지/숨김 처리한다.
- 관리자는 올바르지 않은 사용자 계정을 정지하거나 정지 해제한다.
- Cloudflare Pages 빌드/환경 변수 설정을 정리한다.
- 기존 Android/Spring Boot 구현은 참고 구현으로 유지하되 초기 MVP 경로에서는 제외한다.
- Android/Spring Boot 사진 업로드 경로는 향후 구현 후보로 보류한다.

## 초기 제품 우선순위

- 로그인 기반 감성 엽서 작성.
- 사진 업로드.
- 랜덤 엽서 전달.
- 오늘의 엽서 수신.
- 별도 엽서 보관/북마크.
- 로그인 사용자의 작성한 엽서 기록.
- 로그인 사용자의 받은 엽서 기록.
- 신고.
- 관리자 승인 기반 전달.
- 관리자 페이지.
- 계절 테마.
- 감성 태그.
- 신고, 차단, 숨김, 검수 상태 같은 기본 검수/관리 기능.

## 미정 질문

- 이메일/비밀번호 로그인에서 이메일 인증을 필수로 둘지.
- 수신자에게 발신자 익명성을 유지하면서 작성자 본인만 작성 기록을 볼 수 있는 RLS 모델을 어떻게 둘지.
- Cloudflare Pages 배포 환경에서 Supabase URL과 anon key를 어떤 환경 이름으로 관리할지.
- 기존 Android/Spring Boot 코드를 언제 정리하거나 별도 브랜치로 분리할지.

## 직후 구현 단위

- React + Vite 웹앱과 Supabase 기반 사진 업로드 MVP는 구현되어 있다.
- 세부 구현 완료 상태는 `plan/26-05-16/postcard_photo_upload.md`에서 관리한다.
- 다음 구현 단위는 인증 고도화, 작성/수신 기록, 신고, 관리자 승인 화면이다.
- 세부 후속 계획은 `plan/26-05-16/auth_history_reports_admin.md`에서 관리한다.

## 다음 계획 작업

- Supabase 프로젝트에 SQL 마이그레이션을 적용한다.
- Cloudflare Pages 프로젝트에 `frontend` 앱을 연결하고 환경 변수를 등록한다.
- 운영 데이터에서 관리자 승인할 테스트 엽서를 준비하고 오늘의 엽서 받기 흐름을 검증한다.
- 관리자 승인 UI가 없으므로 MVP 검증 중에는 SQL 또는 Supabase Studio로 `postcards.status`를 `approved`로 변경해야 한다.
- 다음 구현 단위는 고아 파일 정기 정리, 차단/반응 같은 후속 운영 기능과 reviewer 점검이다.
