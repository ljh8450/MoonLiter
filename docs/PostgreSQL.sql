-- MoonLiter 웹/Supabase MVP 기준 PostgreSQL 스키마.
-- Supabase SQL Editor 또는 Supabase CLI 마이그레이션에 적용할 수 있는 형태로 기록한다.
-- 현재 적용 대상 마이그레이션:
-- - supabase/migrations/202605130001_initial_web_mvp.sql
-- - supabase/migrations/202605160001_postcard_image_storage_policy.sql
-- - supabase/migrations/202605160002_postcard_approval_and_path_policy.sql
-- - supabase/migrations/202605170001_admin_reports_actions.sql
-- - supabase/migrations/202605170002_report_insert_policy.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.admin_users (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.postcards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL CHECK (char_length(trim(content)) BETWEEN 1 AND 500),
    image_path TEXT,
    image_content_type TEXT CHECK (
        image_content_type IS NULL
        OR image_content_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    image_byte_size BIGINT CHECK (
        image_byte_size IS NULL
        OR (image_byte_size > 0 AND image_byte_size <= 10485760)
    ),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected', 'hidden')),
    moderated_by UUID REFERENCES public.admin_users(user_id) ON DELETE SET NULL,
    moderated_at TIMESTAMPTZ,
    moderation_note TEXT CHECK (
        moderation_note IS NULL
        OR char_length(trim(moderation_note)) BETWEEN 1 AND 500
    ),
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

CREATE TABLE IF NOT EXISTS public.postcard_user_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postcard_id UUID NOT NULL REFERENCES public.postcards(id) ON DELETE CASCADE,
    delivery_id UUID NOT NULL REFERENCES public.postcard_deliveries(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    action_type TEXT NOT NULL CHECK (action_type IN ('not_interested', 'hidden')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, postcard_id, action_type)
);

CREATE TABLE IF NOT EXISTS public.postcard_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postcard_id UUID NOT NULL REFERENCES public.postcards(id) ON DELETE CASCADE,
    delivery_id UUID NOT NULL REFERENCES public.postcard_deliveries(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (char_length(trim(reason)) BETWEEN 1 AND 500),
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'reviewed', 'dismissed', 'actioned')),
    handled_by UUID REFERENCES public.admin_users(user_id) ON DELETE SET NULL,
    handled_at TIMESTAMPTZ,
    handler_note TEXT CHECK (
        handler_note IS NULL
        OR char_length(trim(handler_note)) BETWEEN 1 AND 500
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (reporter_id, postcard_id)
);

CREATE INDEX IF NOT EXISTS idx_postcards_author_id
    ON public.postcards(author_id);

CREATE INDEX IF NOT EXISTS idx_postcards_status
    ON public.postcards(status);

CREATE INDEX IF NOT EXISTS idx_postcards_moderated_by
    ON public.postcards(moderated_by)
    WHERE moderated_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_postcards_image_path
    ON public.postcards(image_path)
    WHERE image_path IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_postcard_deliveries_receiver_date
    ON public.postcard_deliveries(receiver_id, delivered_date);

CREATE INDEX IF NOT EXISTS idx_postcard_user_actions_user
    ON public.postcard_user_actions(user_id, action_type);

CREATE INDEX IF NOT EXISTS idx_postcard_user_actions_delivery
    ON public.postcard_user_actions(delivery_id);

CREATE INDEX IF NOT EXISTS idx_postcard_reports_status
    ON public.postcard_reports(status, created_at);

CREATE INDEX IF NOT EXISTS idx_postcard_reports_reporter
    ON public.postcard_reports(reporter_id, created_at);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.postcards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.postcard_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.postcard_user_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.postcard_reports ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT auth.uid() IS NOT NULL
       AND EXISTS (
           SELECT 1
             FROM public.admin_users a
            WHERE a.user_id = auth.uid()
       );
$$;

CREATE POLICY "profiles can insert own row"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

CREATE POLICY "profiles can read own row"
    ON public.profiles FOR SELECT
    USING (auth.uid() = id);

CREATE POLICY "admins can read admin users"
    ON public.admin_users FOR SELECT
    USING (public.is_admin());

CREATE POLICY "users can create own postcards"
    ON public.postcards FOR INSERT
    WITH CHECK (
        auth.uid() = author_id
        AND status = 'pending'
    );

CREATE POLICY "users can read own postcards"
    ON public.postcards FOR SELECT
    USING (auth.uid() = author_id);

CREATE POLICY "users can read delivered postcards"
    ON public.postcards FOR SELECT
    USING (
        status = 'approved'
        AND
        EXISTS (
            SELECT 1
              FROM public.postcard_deliveries d
             WHERE d.postcard_id = public.postcards.id
               AND d.receiver_id = auth.uid()
        )
    );

CREATE POLICY "admins can read all postcards"
    ON public.postcards FOR SELECT
    USING (public.is_admin());

CREATE POLICY "users can read own deliveries"
    ON public.postcard_deliveries FOR SELECT
    USING (auth.uid() = receiver_id);

CREATE POLICY "admins can read all deliveries"
    ON public.postcard_deliveries FOR SELECT
    USING (public.is_admin());

CREATE POLICY "users can create own postcard actions"
    ON public.postcard_user_actions FOR INSERT
    WITH CHECK (
        auth.uid() = user_id
        AND EXISTS (
            SELECT 1
              FROM public.postcard_deliveries d
             WHERE d.id = delivery_id
               AND d.postcard_id = postcard_user_actions.postcard_id
               AND d.receiver_id = auth.uid()
        )
    );

CREATE POLICY "users can read own postcard actions"
    ON public.postcard_user_actions FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "users can delete own postcard actions"
    ON public.postcard_user_actions FOR DELETE
    USING (auth.uid() = user_id);

CREATE POLICY "admins can read all postcard actions"
    ON public.postcard_user_actions FOR SELECT
    USING (public.is_admin());

CREATE POLICY "users can create own reports"
    ON public.postcard_reports FOR INSERT
    WITH CHECK (
        auth.uid() = reporter_id
        AND status = 'open'
        AND handled_by IS NULL
        AND handled_at IS NULL
        AND handler_note IS NULL
        AND EXISTS (
            SELECT 1
              FROM public.postcard_deliveries d
             WHERE d.id = delivery_id
               AND d.postcard_id = postcard_reports.postcard_id
               AND d.receiver_id = auth.uid()
        )
    );

CREATE POLICY "users can read own reports"
    ON public.postcard_reports FOR SELECT
    USING (auth.uid() = reporter_id);

CREATE POLICY "admins can read all reports"
    ON public.postcard_reports FOR SELECT
    USING (public.is_admin());

INSERT INTO storage.buckets (id, name, public)
VALUES ('postcard-images', 'postcard-images', false)
ON CONFLICT (id) DO NOTHING;

UPDATE storage.buckets
   SET public = false,
       file_size_limit = 10485760,
       allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp']
 WHERE id = 'postcard-images';

CREATE POLICY "users can upload own postcard images"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'postcard-images'
        AND (storage.foldername(name))[1] = 'postcards'
        AND auth.uid()::text = (storage.foldername(name))[2]
    );

CREATE POLICY "users can read related postcard images"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'postcard-images'
        AND auth.uid() IS NOT NULL
        AND (
            (
                (storage.foldername(name))[1] = 'postcards'
                AND auth.uid()::text = (storage.foldername(name))[2]
            )
            OR EXISTS (
                SELECT 1
                  FROM public.postcards p
                 WHERE p.image_path = storage.objects.name
                   AND p.author_id = auth.uid()
            )
            OR EXISTS (
                SELECT 1
                  FROM public.postcards p
                  JOIN public.postcard_deliveries d
                    ON d.postcard_id = p.id
                 WHERE p.image_path = storage.objects.name
                   AND d.receiver_id = auth.uid()
                   AND p.status = 'approved'
            )
            OR EXISTS (
                SELECT 1
                  FROM public.postcards p
                 WHERE p.image_path = storage.objects.name
                   AND public.is_admin()
            )
        )
    );

CREATE POLICY "users can delete own postcard images"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'postcard-images'
        AND (storage.foldername(name))[1] = 'postcards'
        AND auth.uid()::text = (storage.foldername(name))[2]
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

CREATE OR REPLACE FUNCTION public.approve_postcard(target_postcard_id UUID)
RETURNS public.postcards
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_user_id UUID := auth.uid();
    updated_postcard public.postcards%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION '관리자 권한이 필요합니다.';
    END IF;

    UPDATE public.postcards
       SET status = 'approved',
           moderated_by = current_user_id,
           moderated_at = NOW(),
           moderation_note = NULL
     WHERE id = target_postcard_id
       AND status = 'pending'
     RETURNING * INTO updated_postcard;

    IF updated_postcard.id IS NULL THEN
        RAISE EXCEPTION '승인할 대기 엽서를 찾을 수 없습니다.';
    END IF;

    RETURN updated_postcard;
END;
$$;

CREATE OR REPLACE FUNCTION public.reject_postcard(
    target_postcard_id UUID,
    rejection_note TEXT DEFAULT NULL
)
RETURNS public.postcards
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_user_id UUID := auth.uid();
    updated_postcard public.postcards%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION '관리자 권한이 필요합니다.';
    END IF;

    UPDATE public.postcards
       SET status = 'rejected',
           moderated_by = current_user_id,
           moderated_at = NOW(),
           moderation_note = NULLIF(trim(rejection_note), '')
     WHERE id = target_postcard_id
       AND status = 'pending'
     RETURNING * INTO updated_postcard;

    IF updated_postcard.id IS NULL THEN
        RAISE EXCEPTION '반려할 대기 엽서를 찾을 수 없습니다.';
    END IF;

    RETURN updated_postcard;
END;
$$;

CREATE OR REPLACE FUNCTION public.hide_postcard(
    target_postcard_id UUID,
    hide_note TEXT DEFAULT NULL
)
RETURNS public.postcards
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_user_id UUID := auth.uid();
    updated_postcard public.postcards%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION '관리자 권한이 필요합니다.';
    END IF;

    UPDATE public.postcards
       SET status = 'hidden',
           moderated_by = current_user_id,
           moderated_at = NOW(),
           moderation_note = NULLIF(trim(hide_note), '')
     WHERE id = target_postcard_id
       AND status IN ('approved', 'pending')
     RETURNING * INTO updated_postcard;

    IF updated_postcard.id IS NULL THEN
        RAISE EXCEPTION '숨김 처리할 엽서를 찾을 수 없습니다.';
    END IF;

    RETURN updated_postcard;
END;
$$;

CREATE OR REPLACE FUNCTION public.resolve_postcard_report(
    target_report_id UUID,
    next_status TEXT,
    should_hide_postcard BOOLEAN DEFAULT FALSE,
    note TEXT DEFAULT NULL
)
RETURNS public.postcard_reports
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_user_id UUID := auth.uid();
    updated_report public.postcard_reports%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION '관리자 권한이 필요합니다.';
    END IF;

    IF next_status NOT IN ('reviewed', 'dismissed', 'actioned') THEN
        RAISE EXCEPTION '지원하지 않는 신고 처리 상태입니다.';
    END IF;

    UPDATE public.postcard_reports
       SET status = next_status,
           handled_by = current_user_id,
           handled_at = NOW(),
           handler_note = NULLIF(trim(note), '')
     WHERE id = target_report_id
       AND status = 'open'
     RETURNING * INTO updated_report;

    IF updated_report.id IS NULL THEN
        RAISE EXCEPTION '처리할 신고를 찾을 수 없습니다.';
    END IF;

    IF should_hide_postcard THEN
        UPDATE public.postcards
           SET status = 'hidden',
               moderated_by = current_user_id,
               moderated_at = NOW(),
               moderation_note = NULLIF(trim(note), '')
         WHERE id = updated_report.postcard_id;
    END IF;

    RETURN updated_report;
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
    image_content_type TEXT,
    image_byte_size BIGINT,
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
      FROM public.postcard_deliveries d
     WHERE d.receiver_id = current_user_id
       AND d.delivered_date = today
     LIMIT 1;

    IF existing_delivery.id IS NULL THEN
        SELECT *
          INTO selected_postcard
          FROM public.postcards
         WHERE status = 'approved'
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
        p.image_content_type,
        p.image_byte_size,
        existing_delivery.delivered_date,
        existing_delivery.created_at
      FROM public.postcards p
     WHERE p.id = existing_delivery.postcard_id
       AND p.status = 'approved';
END;
$$;
