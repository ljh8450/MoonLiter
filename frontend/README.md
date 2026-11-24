# 문라이터 웹 MVP

React + TypeScript + Vite로 작성한 PWA입니다. 브라우저에서 Supabase Auth, PostgreSQL/RPC, 비공개 Storage를 사용합니다. Android/Spring Boot는 현재 웹 MVP의 실행에 필요하지 않습니다.

## 로컬 실행

```bash
npm ci
cp .env.example .env.local
npm run dev
```

`.env.local`에 테스트용 Supabase의 `VITE_SUPABASE_URL`과 공개 `VITE_SUPABASE_ANON_KEY`를 입력합니다. [마이그레이션](../supabase/migrations/)을 시간순으로 적용하고 이메일 로그인을 활성화합니다. 소셜 로그인이 필요하면 Google·Kakao와 로컬 리디렉션 URL을 별도 설정합니다. 시크릿 및 service-role 키는 프론트 환경에 넣지 않습니다.

## 검증 및 배포

- `npm run build`: TypeScript 타입 검사 후 Vite 프로덕션 빌드.
- `npm run preview`: 생성된 빌드 확인.
- Cloudflare Pages 기준: 루트 `frontend`, 명령 `npm run build`, 산출물 `dist`. [전체 운영 절차](../docs/operations.md).
- 제품 흐름과 시연은 [저장소 README](../README.md)를 참고하세요.
