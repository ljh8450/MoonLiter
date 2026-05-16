alter table public.profiles
  add column if not exists suspended_at timestamptz,
  add column if not exists suspended_by uuid references public.admin_users(user_id) on delete set null,
  add column if not exists suspension_reason text;

alter table public.profiles
  drop constraint if exists profiles_suspension_reason_check,
  add constraint profiles_suspension_reason_check
    check (
      suspension_reason is null
      or char_length(trim(suspension_reason)) between 1 and 500
    );

create or replace function public.is_suspended(target_user_id uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select target_user_id is not null
     and exists (
       select 1
         from public.profiles p
        where p.id = target_user_id
          and p.suspended_at is not null
     );
$$;

drop policy if exists "users can create own postcards"
  on public.postcards;

create policy "users can create own postcards"
  on public.postcards for insert
  with check (
    auth.uid() = author_id
    and status = 'pending'
    and not public.is_suspended()
  );

drop policy if exists "users can create own postcard actions"
  on public.postcard_user_actions;

create policy "users can create own postcard actions"
  on public.postcard_user_actions for insert
  with check (
    auth.uid() = user_id
    and not public.is_suspended()
    and exists (
      select 1
        from public.postcard_deliveries d
       where d.id = delivery_id
         and d.postcard_id = postcard_user_actions.postcard_id
         and d.receiver_id = auth.uid()
    )
  );

drop policy if exists "users can create own reports"
  on public.postcard_reports;

create policy "users can create own reports"
  on public.postcard_reports for insert
  with check (
    auth.uid() = reporter_id
    and not public.is_suspended()
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

drop policy if exists "users can upload own postcard images"
  on storage.objects;

create policy "users can upload own postcard images"
  on storage.objects for insert
  with check (
    bucket_id = 'postcard-images'
    and (storage.foldername(name))[1] = 'postcards'
    and auth.uid()::text = (storage.foldername(name))[2]
    and not public.is_suspended()
  );

create or replace function public.suspend_user(
  target_user_id uuid,
  reason text default null
)
returns public.profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  updated_profile public.profiles%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  if target_user_id = current_user_id then
    raise exception '자기 자신은 정지할 수 없습니다.';
  end if;

  update public.profiles
     set suspended_at = now(),
         suspended_by = current_user_id,
         suspension_reason = nullif(trim(reason), '')
   where id = target_user_id
   returning * into updated_profile;

  if updated_profile.id is null then
    raise exception '정지할 사용자를 찾을 수 없습니다.';
  end if;

  return updated_profile;
end;
$$;

create or replace function public.unsuspend_user(target_user_id uuid)
returns public.profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  updated_profile public.profiles%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  update public.profiles
     set suspended_at = null,
         suspended_by = null,
         suspension_reason = null
   where id = target_user_id
   returning * into updated_profile;

  if updated_profile.id is null then
    raise exception '정지 해제할 사용자를 찾을 수 없습니다.';
  end if;

  return updated_profile;
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

  if public.is_suspended(current_user_id) then
    raise exception '정지된 사용자는 엽서를 받을 수 없습니다.';
  end if;

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
       and not public.is_suspended(author_id)
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
    and p.status = 'approved'
    and not public.is_suspended(p.author_id);
end;
$$;
