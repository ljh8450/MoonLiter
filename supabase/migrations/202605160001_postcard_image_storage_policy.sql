alter table public.postcards
  add column if not exists image_content_type text,
  add column if not exists image_byte_size bigint;

alter table public.postcards
  drop constraint if exists postcards_image_content_type_check,
  add constraint postcards_image_content_type_check
    check (
      image_content_type is null
      or image_content_type in ('image/jpeg', 'image/png', 'image/webp')
    );

alter table public.postcards
  drop constraint if exists postcards_image_byte_size_check,
  add constraint postcards_image_byte_size_check
    check (
      image_byte_size is null
      or (image_byte_size > 0 and image_byte_size <= 10485760)
    );

create index if not exists idx_postcards_image_path
  on public.postcards(image_path)
  where image_path is not null;

drop policy if exists "users can read delivered postcards"
  on public.postcards;

create policy "users can read delivered postcards"
  on public.postcards for select
  using (
    exists (
      select 1
        from public.postcard_deliveries d
       where d.postcard_id = public.postcards.id
         and d.receiver_id = auth.uid()
    )
  );

update storage.buckets
   set public = false,
       file_size_limit = 10485760,
       allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp']
 where id = 'postcard-images';

drop policy if exists "authenticated users can read postcard images"
  on storage.objects;

drop policy if exists "users can read related postcard images"
  on storage.objects;

create policy "users can read related postcard images"
  on storage.objects for select
  using (
    bucket_id = 'postcard-images'
    and auth.uid() is not null
    and (
      auth.uid()::text = (storage.foldername(name))[1]
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
    and auth.uid()::text = (storage.foldername(name))[1]
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
     where status = 'deliverable'
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
