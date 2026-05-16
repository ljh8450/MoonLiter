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
