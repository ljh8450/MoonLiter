-- MoonLiter 웹/Supabase MVP 기준 PostgreSQL 스키마.
-- Supabase SQL Editor 또는 Supabase CLI 마이그레이션에 적용할 수 있는 형태로 기록한다.
-- 현재 적용 대상 마이그레이션: supabase/migrations/202605130001_initial_web_mvp.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.postcards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL CHECK (char_length(trim(content)) BETWEEN 1 AND 500),
    image_path TEXT,
    status TEXT NOT NULL DEFAULT 'deliverable' CHECK (status IN ('deliverable', 'hidden')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.postcard_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postcard_id UUID NOT NULL REFERENCES public.postcards(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    delivered_date DATE NOT NULL DEFAULT (timezone('utc', now())::date),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (receiver_id, delivered_date)
);

CREATE INDEX IF NOT EXISTS idx_postcards_author_id
    ON public.postcards(author_id);

CREATE INDEX IF NOT EXISTS idx_postcards_status
    ON public.postcards(status);

CREATE INDEX IF NOT EXISTS idx_postcard_deliveries_receiver_date
    ON public.postcard_deliveries(receiver_id, delivered_date);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.postcards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.postcard_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles can insert own row"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

CREATE POLICY "profiles can read own row"
    ON public.profiles FOR SELECT
    USING (auth.uid() = id);

CREATE POLICY "users can create own postcards"
    ON public.postcards FOR INSERT
    WITH CHECK (auth.uid() = author_id);

CREATE POLICY "users can read own postcards"
    ON public.postcards FOR SELECT
    USING (auth.uid() = author_id);

CREATE POLICY "users can read own deliveries"
    ON public.postcard_deliveries FOR SELECT
    USING (auth.uid() = receiver_id);

INSERT INTO storage.buckets (id, name, public)
VALUES ('postcard-images', 'postcard-images', false)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "users can upload own postcard images"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'postcard-images'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

CREATE POLICY "authenticated users can read postcard images"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'postcard-images'
        AND auth.role() = 'authenticated'
    );

CREATE OR REPLACE FUNCTION public.ensure_profile()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id)
    VALUES (NEW.id)
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.ensure_profile();

CREATE OR REPLACE FUNCTION public.assign_today_postcard()
RETURNS TABLE (
    delivery_id UUID,
    postcard_id UUID,
    content TEXT,
    image_path TEXT,
    delivered_date DATE,
    delivered_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_user_id UUID := auth.uid();
    today DATE := timezone('utc', now())::date;
    existing_delivery public.postcard_deliveries%ROWTYPE;
    selected_postcard public.postcards%ROWTYPE;
BEGIN
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION '로그인이 필요합니다.';
    END IF;

    INSERT INTO public.profiles (id)
    VALUES (current_user_id)
    ON CONFLICT (id) DO NOTHING;

    SELECT *
      INTO existing_delivery
      FROM public.postcard_deliveries
     WHERE receiver_id = current_user_id
       AND delivered_date = today
     LIMIT 1;

    IF existing_delivery.id IS NULL THEN
        SELECT *
          INTO selected_postcard
          FROM public.postcards
         WHERE status = 'deliverable'
           AND author_id <> current_user_id
           AND NOT EXISTS (
             SELECT 1
               FROM public.postcard_deliveries d
              WHERE d.receiver_id = current_user_id
                AND d.postcard_id = public.postcards.id
           )
         ORDER BY random()
         LIMIT 1;

        IF selected_postcard.id IS NULL THEN
            RAISE EXCEPTION '전달 가능한 엽서가 없습니다.';
        END IF;

        INSERT INTO public.postcard_deliveries (postcard_id, receiver_id, delivered_date)
        VALUES (selected_postcard.id, current_user_id, today)
        RETURNING * INTO existing_delivery;
    END IF;

    RETURN QUERY
    SELECT
        existing_delivery.id,
        p.id,
        p.content,
        p.image_path,
        existing_delivery.delivered_date,
        existing_delivery.created_at
      FROM public.postcards p
     WHERE p.id = existing_delivery.postcard_id;
END;
$$;
