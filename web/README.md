# 문라이터 웹 MVP

React + Vite + Supabase + PWA 기반 초기 MVP 웹앱이다.

## 로컬 실행

```bash
npm install
npm run dev
```

## 환경 변수

`.env` 또는 Cloudflare Pages 환경 변수에 아래 값을 등록한다.

```text
VITE_SUPABASE_URL=
VITE_SUPABASE_ANON_KEY=
```

## Cloudflare Pages 설정

- Framework preset: Vite
- Build command: `npm run build`
- Build output directory: `dist`
- Root directory: `web`

## Supabase 설정

1. Supabase 프로젝트를 생성한다.
2. Auth에서 익명 로그인을 활성화한다.
3. `supabase/migrations/202605130001_initial_web_mvp.sql`을 적용한다.
4. 웹앱 환경 변수에 Supabase URL과 anon key를 등록한다.
