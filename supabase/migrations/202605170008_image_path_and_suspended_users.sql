alter table public.postcards
  drop constraint if exists postcards_image_metadata_pair_check,
  add constraint postcards_image_metadata_pair_check
    check (
      (
        image_path is null
        and image_content_type is null
        and image_byte_size is null
      )
      or (
        image_path is not null
        and image_content_type is not null
        and image_byte_size is not null
      )
    ) not valid;

drop policy if exists "users can create own postcards"
  on public.postcards;

create policy "users can create own postcards"
  on public.postcards for insert
  with check (
    auth.uid() = author_id
    and status = 'pending'
    and not public.is_suspended()
    and (
      image_path is null
      or image_path like ('postcards/' || auth.uid()::text || '/%')
    )
  );

create or replace function public.list_suspended_users()
returns table (
  user_id uuid,
  email text,
  suspended_at timestamptz,
  suspension_reason text
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
    p.id,
    u.email::text,
    p.suspended_at,
    p.suspension_reason
  from public.profiles p
  left join auth.users u
    on u.id = p.id
  where p.suspended_at is not null
  order by p.suspended_at desc;
end;
$$;
