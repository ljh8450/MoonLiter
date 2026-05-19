alter table public.profiles
  add column if not exists phone_verified_at timestamptz,
  add column if not exists phone_verified_source text;

alter table public.profiles
  drop constraint if exists profiles_phone_verified_source_check,
  add constraint profiles_phone_verified_source_check
    check (
      phone_verified_source is null
      or phone_verified_source in ('supabase_auth', 'spring_boot_migration')
    );

create or replace function public.has_verified_phone(target_user_id uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select target_user_id is not null
     and exists (
       select 1
         from auth.users u
         left join public.profiles p
           on p.id = u.id
        where u.id = target_user_id
          and coalesce(p.phone_verified_at, u.phone_confirmed_at) is not null
     );
$$;

create or replace function public.can_use_trusted_features(target_user_id uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select target_user_id is not null
     and public.has_verified_phone(target_user_id)
     and not public.is_suspended(target_user_id);
$$;

create or replace function public.sync_my_phone_verification()
returns public.profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  confirmed_at timestamptz;
  updated_profile public.profiles%rowtype;
begin
  if current_user_id is null then
    raise exception '로그인이 필요합니다.';
  end if;

  select u.phone_confirmed_at
    into confirmed_at
    from auth.users u
   where u.id = current_user_id;

  if confirmed_at is null then
    raise exception 'Supabase Auth 전화번호 인증이 필요합니다.';
  end if;

  insert into public.profiles (id)
  values (current_user_id)
  on conflict (id) do nothing;

  update public.profiles
     set phone_verified_at = coalesce(phone_verified_at, confirmed_at),
         phone_verified_source = coalesce(phone_verified_source, 'supabase_auth')
   where id = current_user_id
   returning * into updated_profile;

  return updated_profile;
end;
$$;

drop policy if exists "users can create own postcards"
  on public.postcards;

create policy "users can create own postcards"
  on public.postcards for insert
  with check (
    auth.uid() = author_id
    and status = 'pending'
    and public.can_use_trusted_features()
    and (
      image_path is null
      or image_path like ('postcards/' || auth.uid()::text || '/%')
    )
  );

drop policy if exists "users can create own postcard actions"
  on public.postcard_user_actions;

create policy "users can create own postcard actions"
  on public.postcard_user_actions for insert
  with check (
    auth.uid() = user_id
    and public.can_use_trusted_features()
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
    and public.can_use_trusted_features()
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
    and public.can_use_trusted_features()
  );

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

  if not public.can_use_trusted_features(current_user_id) then
    raise exception '전화번호 인증이 완료된 정상 계정만 엽서를 받을 수 있습니다.';
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
       and public.can_use_trusted_features(author_id)
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
    and public.can_use_trusted_features(p.author_id);
end;
$$;
