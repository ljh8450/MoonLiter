create table if not exists public.admin_users (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);

alter table public.postcards
  add column if not exists moderated_by uuid references public.admin_users(user_id) on delete set null,
  add column if not exists moderated_at timestamptz,
  add column if not exists moderation_note text;

alter table public.postcards
  drop constraint if exists postcards_moderation_note_check,
  add constraint postcards_moderation_note_check
    check (
      moderation_note is null
      or char_length(trim(moderation_note)) between 1 and 500
    );

create table if not exists public.postcard_user_actions (
  id uuid primary key default gen_random_uuid(),
  postcard_id uuid not null references public.postcards(id) on delete cascade,
  delivery_id uuid not null references public.postcard_deliveries(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  action_type text not null check (action_type in ('not_interested', 'hidden')),
  created_at timestamptz not null default now(),
  unique (user_id, postcard_id, action_type)
);

create table if not exists public.postcard_reports (
  id uuid primary key default gen_random_uuid(),
  postcard_id uuid not null references public.postcards(id) on delete cascade,
  delivery_id uuid not null references public.postcard_deliveries(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null check (char_length(trim(reason)) between 1 and 500),
  status text not null default 'open' check (status in ('open', 'reviewed', 'dismissed', 'actioned')),
  handled_by uuid references public.admin_users(user_id) on delete set null,
  handled_at timestamptz,
  handler_note text check (
    handler_note is null
    or char_length(trim(handler_note)) between 1 and 500
  ),
  created_at timestamptz not null default now(),
  unique (reporter_id, postcard_id)
);

create index if not exists idx_postcards_moderated_by
  on public.postcards(moderated_by)
  where moderated_by is not null;

create index if not exists idx_postcard_user_actions_user
  on public.postcard_user_actions(user_id, action_type);

create index if not exists idx_postcard_user_actions_delivery
  on public.postcard_user_actions(delivery_id);

create index if not exists idx_postcard_reports_status
  on public.postcard_reports(status, created_at);

create index if not exists idx_postcard_reports_reporter
  on public.postcard_reports(reporter_id, created_at);

alter table public.admin_users enable row level security;
alter table public.postcard_user_actions enable row level security;
alter table public.postcard_reports enable row level security;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null
     and exists (
       select 1
         from public.admin_users a
        where a.user_id = auth.uid()
     );
$$;

drop policy if exists "admins can read admin users"
  on public.admin_users;

create policy "admins can read admin users"
  on public.admin_users for select
  using (public.is_admin());

drop policy if exists "users can read delivered postcards"
  on public.postcards;

create policy "users can read delivered postcards"
  on public.postcards for select
  using (
    status = 'approved'
    and exists (
      select 1
        from public.postcard_deliveries d
       where d.postcard_id = public.postcards.id
         and d.receiver_id = auth.uid()
    )
  );

drop policy if exists "admins can read all postcards"
  on public.postcards;

create policy "admins can read all postcards"
  on public.postcards for select
  using (public.is_admin());

drop policy if exists "admins can read all deliveries"
  on public.postcard_deliveries;

create policy "admins can read all deliveries"
  on public.postcard_deliveries for select
  using (public.is_admin());

drop policy if exists "users can create own postcard actions"
  on public.postcard_user_actions;

create policy "users can create own postcard actions"
  on public.postcard_user_actions for insert
  with check (
    auth.uid() = user_id
    and exists (
      select 1
        from public.postcard_deliveries d
       where d.id = delivery_id
         and d.postcard_id = postcard_user_actions.postcard_id
         and d.receiver_id = auth.uid()
    )
  );

drop policy if exists "users can read own postcard actions"
  on public.postcard_user_actions;

create policy "users can read own postcard actions"
  on public.postcard_user_actions for select
  using (auth.uid() = user_id);

drop policy if exists "users can delete own postcard actions"
  on public.postcard_user_actions;

create policy "users can delete own postcard actions"
  on public.postcard_user_actions for delete
  using (auth.uid() = user_id);

drop policy if exists "admins can read all postcard actions"
  on public.postcard_user_actions;

create policy "admins can read all postcard actions"
  on public.postcard_user_actions for select
  using (public.is_admin());

drop policy if exists "users can create own reports"
  on public.postcard_reports;

create policy "users can create own reports"
  on public.postcard_reports for insert
  with check (
    auth.uid() = reporter_id
    and status = 'open'
    and handled_by is null
    and handled_at is null
    and handler_note is null
    and exists (
      select 1
        from public.postcard_deliveries d
       where d.id = delivery_id
         and d.postcard_id = postcard_reports.postcard_id
         and d.receiver_id = auth.uid()
    )
  );

drop policy if exists "users can read own reports"
  on public.postcard_reports;

create policy "users can read own reports"
  on public.postcard_reports for select
  using (auth.uid() = reporter_id);

drop policy if exists "admins can read all reports"
  on public.postcard_reports;

create policy "admins can read all reports"
  on public.postcard_reports for select
  using (public.is_admin());

drop policy if exists "users can read related postcard images"
  on storage.objects;

create policy "users can read related postcard images"
  on storage.objects for select
  using (
    bucket_id = 'postcard-images'
    and auth.uid() is not null
    and (
      (
        (storage.foldername(name))[1] = 'postcards'
        and auth.uid()::text = (storage.foldername(name))[2]
      )
      or exists (
        select 1
          from public.postcards p
         where p.image_path = storage.objects.name
           and p.author_id = auth.uid()
      )
      or exists (
        select 1
          from public.postcards p
          join public.postcard_deliveries d
            on d.postcard_id = p.id
         where p.image_path = storage.objects.name
           and d.receiver_id = auth.uid()
           and p.status = 'approved'
      )
      or exists (
        select 1
         from public.postcards p
        where p.image_path = storage.objects.name
          and public.is_admin()
      )
    )
  );

create or replace function public.approve_postcard(target_postcard_id uuid)
returns public.postcards
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  updated_postcard public.postcards%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  update public.postcards
     set status = 'approved',
         moderated_by = current_user_id,
         moderated_at = now(),
         moderation_note = null
   where id = target_postcard_id
     and status = 'pending'
   returning * into updated_postcard;

  if updated_postcard.id is null then
    raise exception '승인할 대기 엽서를 찾을 수 없습니다.';
  end if;

  return updated_postcard;
end;
$$;

create or replace function public.reject_postcard(
  target_postcard_id uuid,
  rejection_note text default null
)
returns public.postcards
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  updated_postcard public.postcards%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  update public.postcards
     set status = 'rejected',
         moderated_by = current_user_id,
         moderated_at = now(),
         moderation_note = nullif(trim(rejection_note), '')
   where id = target_postcard_id
     and status = 'pending'
   returning * into updated_postcard;

  if updated_postcard.id is null then
    raise exception '반려할 대기 엽서를 찾을 수 없습니다.';
  end if;

  return updated_postcard;
end;
$$;

create or replace function public.hide_postcard(
  target_postcard_id uuid,
  hide_note text default null
)
returns public.postcards
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  updated_postcard public.postcards%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  update public.postcards
     set status = 'hidden',
         moderated_by = current_user_id,
         moderated_at = now(),
         moderation_note = nullif(trim(hide_note), '')
   where id = target_postcard_id
     and status in ('approved', 'pending')
   returning * into updated_postcard;

  if updated_postcard.id is null then
    raise exception '숨김 처리할 엽서를 찾을 수 없습니다.';
  end if;

  return updated_postcard;
end;
$$;

create or replace function public.resolve_postcard_report(
  target_report_id uuid,
  next_status text,
  should_hide_postcard boolean default false,
  note text default null
)
returns public.postcard_reports
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  updated_report public.postcard_reports%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  if next_status not in ('reviewed', 'dismissed', 'actioned') then
    raise exception '지원하지 않는 신고 처리 상태입니다.';
  end if;

  update public.postcard_reports
     set status = next_status,
         handled_by = current_user_id,
         handled_at = now(),
         handler_note = nullif(trim(note), '')
   where id = target_report_id
     and status = 'open'
   returning * into updated_report;

  if updated_report.id is null then
    raise exception '처리할 신고를 찾을 수 없습니다.';
  end if;

  if should_hide_postcard then
    update public.postcards
       set status = 'hidden',
           moderated_by = current_user_id,
           moderated_at = now(),
           moderation_note = nullif(trim(note), '')
     where id = updated_report.postcard_id;
  end if;

  return updated_report;
end;
$$;

create or replace function public.assign_today_postcard()
returns table (
  delivery_id uuid,
  postcard_id uuid,
  content text,
  image_path text,
  image_content_type text,
  image_byte_size bigint,
  delivered_date date,
  delivered_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  today date := timezone('utc', now())::date;
  existing_delivery public.postcard_deliveries%rowtype;
  selected_postcard public.postcards%rowtype;
begin
  if current_user_id is null then
    raise exception '로그인이 필요합니다.';
  end if;

  insert into public.profiles (id)
  values (current_user_id)
  on conflict (id) do nothing;

  select *
    into existing_delivery
    from public.postcard_deliveries d
   where d.receiver_id = current_user_id
     and d.delivered_date = today
   limit 1;

  if existing_delivery.id is null then
    select *
      into selected_postcard
      from public.postcards
     where status = 'approved'
       and author_id <> current_user_id
       and not exists (
         select 1
           from public.postcard_deliveries d
          where d.receiver_id = current_user_id
            and d.postcard_id = public.postcards.id
       )
     order by random()
     limit 1;

    if selected_postcard.id is null then
      raise exception '전달 가능한 엽서가 없습니다.';
    end if;

    insert into public.postcard_deliveries (postcard_id, receiver_id, delivered_date)
    values (selected_postcard.id, current_user_id, today)
    returning * into existing_delivery;
  end if;

  return query
  select
    existing_delivery.id,
    p.id,
    p.content,
    p.image_path,
    p.image_content_type,
    p.image_byte_size,
    existing_delivery.delivered_date,
    existing_delivery.created_at
  from public.postcards p
  where p.id = existing_delivery.postcard_id
    and p.status = 'approved';
end;
$$;
