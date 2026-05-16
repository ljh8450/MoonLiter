create or replace function public.list_admin_users()
returns table (
  user_id uuid,
  email text,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  return query
  select
    a.user_id,
    u.email::text,
    a.created_at
  from public.admin_users a
  join auth.users u
    on u.id = a.user_id
  order by a.created_at asc;
end;
$$;

create or replace function public.invite_admin_user(target_email text)
returns table (
  user_id uuid,
  email text,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
  target_user auth.users%rowtype;
  inserted_admin public.admin_users%rowtype;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  select *
    into target_user
    from auth.users u
   where lower(u.email) = lower(trim(target_email))
   limit 1;

  if target_user.id is null then
    raise exception '해당 이메일의 사용자를 찾을 수 없습니다.';
  end if;

  insert into public.profiles (id)
  values (target_user.id)
  on conflict (id) do nothing;

  insert into public.admin_users (user_id)
  values (target_user.id)
  on conflict on constraint admin_users_pkey do nothing;

  select *
    into inserted_admin
    from public.admin_users a
   where a.user_id = target_user.id;

  return query
  select
    inserted_admin.user_id,
    target_user.email::text,
    inserted_admin.created_at;
end;
$$;

create or replace function public.remove_admin_user(target_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  admin_count integer;
begin
  if not public.is_admin() then
    raise exception '관리자 권한이 필요합니다.';
  end if;

  if target_user_id = auth.uid() then
    raise exception '자기 자신의 관리자 권한은 회수할 수 없습니다.';
  end if;

  select count(*)
    into admin_count
    from public.admin_users;

  if admin_count <= 1 then
    raise exception '마지막 관리자 권한은 회수할 수 없습니다.';
  end if;

  delete from public.admin_users
   where user_id = target_user_id;
end;
$$;
