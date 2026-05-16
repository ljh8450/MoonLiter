alter table public.postcards
  drop constraint if exists postcards_status_check;

update public.postcards
   set status = 'approved'
 where status = 'deliverable';

alter table public.postcards
  alter column status set default 'pending',
  add constraint postcards_status_check
    check (status in ('pending', 'approved', 'rejected', 'hidden'));

drop policy if exists "users can create own postcards"
  on public.postcards;

create policy "users can create own postcards"
  on public.postcards for insert
  with check (
    auth.uid() = author_id
    and status = 'pending'
  );

drop policy if exists "users can upload own postcard images"
  on storage.objects;

create policy "users can upload own postcard images"
  on storage.objects for insert
  with check (
    bucket_id = 'postcard-images'
    and (storage.foldername(name))[1] = 'postcards'
    and auth.uid()::text = (storage.foldername(name))[2]
  );

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
      )
    )
  );

drop policy if exists "users can delete own postcard images"
  on storage.objects;

create policy "users can delete own postcard images"
  on storage.objects for delete
  using (
    bucket_id = 'postcard-images'
    and (storage.foldername(name))[1] = 'postcards'
    and auth.uid()::text = (storage.foldername(name))[2]
  );

drop function if exists public.assign_today_postcard();

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
  where p.id = existing_delivery.postcard_id;
end;
$$;
