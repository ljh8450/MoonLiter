create extension if not exists "pgcrypto";

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.postcards (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id) on delete cascade,
  content text not null check (char_length(trim(content)) between 1 and 500),
  image_path text,
  status text not null default 'deliverable' check (status in ('deliverable', 'hidden')),
  created_at timestamptz not null default now()
);

create table if not exists public.postcard_deliveries (
  id uuid primary key default gen_random_uuid(),
  postcard_id uuid not null references public.postcards(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  delivered_date date not null default (timezone('utc', now())::date),
  created_at timestamptz not null default now(),
  unique (receiver_id, delivered_date)
);

create index if not exists idx_postcards_author_id on public.postcards(author_id);
create index if not exists idx_postcards_status on public.postcards(status);
create index if not exists idx_postcard_deliveries_receiver_date
  on public.postcard_deliveries(receiver_id, delivered_date);

alter table public.profiles enable row level security;
alter table public.postcards enable row level security;
alter table public.postcard_deliveries enable row level security;

create policy "profiles can insert own row"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "profiles can read own row"
  on public.profiles for select
  using (auth.uid() = id);

create policy "users can create own postcards"
  on public.postcards for insert
  with check (auth.uid() = author_id);

create policy "users can read own postcards"
  on public.postcards for select
  using (auth.uid() = author_id);

create policy "users can read own deliveries"
  on public.postcard_deliveries for select
  using (auth.uid() = receiver_id);

insert into storage.buckets (id, name, public)
values ('postcard-images', 'postcard-images', false)
on conflict (id) do nothing;

create policy "users can upload own postcard images"
  on storage.objects for insert
  with check (
    bucket_id = 'postcard-images'
    and auth.uid()::text = (storage.foldername(name))[1]
  );

create policy "authenticated users can read postcard images"
  on storage.objects for select
  using (
    bucket_id = 'postcard-images'
    and auth.role() = 'authenticated'
  );

create or replace function public.ensure_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id)
  values (new.id)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.ensure_profile();

create or replace function public.assign_today_postcard()
returns table (
  delivery_id uuid,
  postcard_id uuid,
  content text,
  image_path text,
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
    from public.postcard_deliveries
   where receiver_id = current_user_id
     and delivered_date = today
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
    existing_delivery.delivered_date,
    existing_delivery.created_at
  from public.postcards p
  where p.id = existing_delivery.postcard_id;
end;
$$;
