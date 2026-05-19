# 계정 신뢰성과 단계별 사용자 정지 구현 리뷰

## 결론

현재 구현은 컴파일과 프론트엔드 빌드는 통과하지만, `account_integrity_and_suspension.md`의 완료 기준을 충족했다고 보기 어렵다.

가장 큰 문제는 전화번호 인증과 단계별 정지 정책이 실제 웹/Supabase MVP의 주요 기능 권한 경로에 강제되지 않는다는 점이다. 프론트엔드 UI에서는 일부 버튼을 막지만, Supabase 테이블/RPC 직접 호출을 막는 RLS나 RPC 조건과 연결되어 있지 않아 정책 우회가 가능하다.

또한 Spring Boot 백엔드의 `app_users`와 Supabase 웹 MVP의 UUID 사용자 체계가 분리되어 있어 계정 신뢰성 상태가 이중화되어 있다. 이 구조를 유지하려면 주요 기능을 Spring Boot API로 옮기거나, 전화번호 인증/정지 상태를 Supabase DB와 RLS 기준으로도 반영해야 한다.

## 발견 사항

- [차단] 전화번호 인증이 실제 Supabase 주요 기능 권한에 강제되지 않는다.
  - 프론트엔드는 `canUseTrustedFeatures`로 버튼을 비활성화하지만, 엽서 작성, 오늘의 엽서 수신, 신고, 사용자 액션은 여전히 Supabase 테이블/RPC를 직접 호출한다.
  - 백엔드 `app_users.phoneVerifiedAt`와 Supabase RLS/RPC 사이 연결이 없어 API 직접 호출로 우회할 수 있다.
  - 근거: `frontend/src/App.tsx`, `backend/src/main/java/com/moonliter/user/domain/AppUser.java`

- [차단] 단계별 정지 백엔드가 현재 웹 관리자 화면에 연결되지 않았다.
  - 계획은 누적 횟수 기반 정지를 요구하지만, 웹앱은 기존 Supabase `suspend_user` RPC 흐름을 계속 사용한다.
  - Spring Boot의 `POST /api/v1/admin/users/{userId}/suspension`은 실제 웹 관리자 화면에서 사용되지 않는다.
  - 결과적으로 현재 웹 MVP 관리자 정지는 1일, 7일, 30일, 1년, 영구 정지 정책을 적용하지 못한다.
  - 근거: `backend/src/main/java/com/moonliter/admin/web/AdminUserController.java`, 계획 완료 기준

- [높음] Supabase 사용자와 Spring `app_users` 사용자가 분리되어 계정 상태가 이중화된다.
  - 프론트의 실제 데이터는 Supabase UUID 기반이고, 백엔드는 Long ID 기반 `app_users`에 전화번호와 정지 상태를 저장한다.
  - 이 상태에서는 “인증된 전화번호가 없는 신규 사용자는 가입을 완료할 수 없다”는 완료 기준을 Supabase 가입 흐름에서 보장하지 못한다.
  - 근거: `backend/src/main/java/com/moonliter/auth/web/CurrentAccountController.java`, `docs/api.md`

- [높음] 기존 백엔드 사용자가 이메일 인증 후에도 백엔드에서는 계속 미인증으로 남을 수 있다.
  - `CurrentUserService.requireCurrentUser()`는 기존 사용자를 찾으면 JWT의 `email_confirmed_at`을 다시 동기화하지 않는다.
  - 미인증 상태로 최초 생성된 사용자는 이후 이메일 인증을 완료해도 백엔드 기능에서 계속 차단될 수 있다.
  - 근거: `backend/src/main/java/com/moonliter/auth/application/CurrentUserService.java`

- [높음] Supabase JWT 매핑이 provider + subject 기준이라 같은 Supabase 계정이 다른 `app_users` 행으로 분리될 수 있다.
  - Supabase access token의 `sub`가 사용자 식별자라면 provider와 묶지 말고 `sub` 단독 또는 별도 `supabase_user_id`로 고정해야 한다.
  - provider claim 변화나 계정 연결 흐름에 따라 전화번호 인증 상태가 다른 행에 저장될 위험이 있다.
  - 근거: `backend/src/main/java/com/moonliter/auth/application/CurrentUserService.java`

- [보통] SMS 인증 요청에 서버 측 rate limit/cooldown이 없다.
  - 로그인한 사용자가 `/api/v1/account/phone/verification`을 반복 호출하면 SOLAPI 비용이 바로 발생할 수 있다.
  - OTP 만료와 시도 제한은 검증 단계에만 있고 발송 제한은 없다.
  - 근거: `backend/src/main/java/com/moonliter/auth/web/PhoneVerificationController.java`, `backend/src/main/java/com/moonliter/auth/application/PhoneVerificationService.java`

- [보통] SOLAPI 응답 성공 판정이 약하다.
  - 현재는 `failedMessageList`가 비어 있으면 성공으로 간주한다.
  - `response == null`, `groupInfo.count.registeredFailed`, `messageList.statusCode` 등 실패 신호를 더 명확히 확인해야 실제 발송 실패를 성공 처리하지 않는다.
  - 근거: `backend/src/main/java/com/moonliter/auth/application/SolapiSmsClient.java`

- [보통] 프론트는 `VITE_API_BASE_URL`이 없거나 백엔드 계정 조회가 실패하면 전화번호 미인증으로 간주한다.
  - 기존 Supabase-only 배포 환경에서는 주요 기능이 갑자기 막힐 수 있다.
  - Spring Boot API를 필수로 전환할 것인지, Supabase-only 경로를 유지할 것인지 결정이 필요하다.
  - 근거: `frontend/src/App.tsx`

- [보통] API 문서가 현재 구현과 맞지 않는다.
  - `docs/api.md`는 여전히 Spring Boot REST API가 향후 후보라고 설명한다.
  - 새 `/api/v1/account/*`, JWT 요구사항, `VITE_API_BASE_URL`, SOLAPI 전화번호 인증 흐름이 문서화되어 있지 않다.
  - 근거: `docs/api.md`

## 계획 대비 상태

부분 충족 상태다.

구현된 항목:

- 백엔드 전화번호 OTP 발송/검증 모델
- SOLAPI 연동
- 전화번호 해시 저장
- 백엔드 단계별 정지 모델과 서비스
- 백엔드 인앱 알림 생성 모델
- 프론트엔드 이메일 인증 대기 UI
- 프론트엔드 전화번호 인증 UI
- 프론트엔드 정지/미인증 사용자 주요 기능 버튼 제한

미충족 항목:

- Supabase RLS/RPC 수준 전화번호 인증 강제
- 웹 관리자 화면의 단계별 정지 API 연동
- Supabase 웹 MVP 기준 누적 정지 기간 적용
- 인앱 알림 조회 UI
- API 문서 최신화
- 백엔드 테스트 정상 통과
- 전화번호 인증 기반 중복 가입 차단의 실제 가입 흐름 적용

## 리스크 평가

가장 큰 리스크는 “프론트엔드에서만 막는 정책”이다. 현재 사용자는 Supabase client나 REST 호출로 엽서 작성, 신고, 오늘의 엽서 RPC를 직접 호출할 수 있고, 백엔드의 전화번호 인증 상태는 그 호출 경로에서 참조되지 않는다.

두 번째 리스크는 사용자 ID 체계 분리다. Spring Boot `app_users`는 Long ID를 사용하고 Supabase 웹 MVP는 UUID를 사용한다. 전화번호 인증과 정지 상태가 Spring Boot에만 저장되면 Supabase 데이터 권한 정책과 불일치한다.

세 번째 리스크는 SMS 비용 통제다. SOLAPI 발송 API는 인증된 사용자라면 반복 호출할 수 있으므로 서버 측 rate limit과 재요청 간격 제한이 필요하다.

## 검증 결과

실행한 명령:

```bash
backend: .\gradlew compileJava compileTestJava
frontend: npm run build
backend: .\gradlew test --no-daemon --max-workers=1
```

결과:

- 백엔드 컴파일 성공
- 프론트엔드 빌드 성공
- 백엔드 테스트 실패

백엔드 테스트 실패 원인은 테스트 assertion 실패가 아니라 Gradle Test Executor가 `worker.org.gradle.process.internal.worker.GradleWorkerMain`을 로드하지 못하는 실행 환경 문제로 보인다.

## 역할별 인계

### 백엔드

- Supabase `sub`를 단일 외부 사용자 ID로 저장하도록 `app_users` 매핑을 정리한다.
- 기존 사용자 조회 시 이메일 인증 상태를 JWT에서 동기화한다.
- SMS 발송 rate limit과 cooldown을 DB 기준으로 추가한다.
- SOLAPI 응답 검증을 `messageList`, `failedMessageList`, group count까지 확인하도록 강화한다.
- 현재 정지 정책을 웹 관리자 화면이 사용하는 API 또는 Supabase RPC와 연결한다.

### 프론트엔드

- 전화번호 인증 완료 상태를 Supabase 기능 권한과 일관되게 반영할 방법을 정한다.
- 관리자 정지 UI를 백엔드 단계별 정지 API로 전환하거나, Supabase RPC 쪽에 같은 정책을 구현한 뒤 UI를 유지한다.
- `VITE_API_BASE_URL`이 필수인지 선택인지 명확히 하고, 설정 누락 시 사용자에게 구체적으로 안내한다.

### DB/RLS

- Supabase 우선 경로를 유지한다면 `profiles` 또는 별도 테이블에 전화번호 인증 상태와 해시를 저장하고 RLS/RPC에서 강제한다.
- Spring Boot 우선 경로로 전환한다면 엽서 작성, 오늘의 엽서, 신고, 액션을 모두 백엔드 API로 옮기고 Supabase 직접 쓰기 경로를 닫는다.
- 전화번호 해시 unique 제약은 실제 가입/인증 완료 트랜잭션에서 race condition 없이 보장되어야 한다.

### 문서

- `docs/api.md`에 새 Spring Boot 계정 API, JWT 인증, `VITE_API_BASE_URL`, SOLAPI 전화번호 인증 흐름을 반영한다.
- Supabase 웹 MVP 우선 경로와 Spring Boot API 전환 경로 중 현재 구현 기준을 명확히 정리한다.
