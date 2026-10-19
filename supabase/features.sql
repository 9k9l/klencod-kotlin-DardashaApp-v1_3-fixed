-- شغّله مرة واحدة: Supabase ← SQL Editor
-- (آمن لإعادة التشغيل)

-- 1) تفضيلات المحادثة: تثبيت / أرشفة / كتم (خاصة بكل مستخدم)
create table if not exists public.chat_prefs (
  user_id  uuid not null default auth.uid() references auth.users(id) on delete cascade,
  peer     uuid not null,
  pinned   boolean not null default false,
  archived boolean not null default false,
  muted    boolean not null default false,
  primary key (user_id, peer)
);
alter table public.chat_prefs enable row level security;
drop policy if exists chat_prefs_own on public.chat_prefs;
create policy chat_prefs_own on public.chat_prefs
  for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- 2) الرسائل المميّزة بنجمة (message_id نص ليناسب bigint أو uuid)
create table if not exists public.message_stars (
  user_id    uuid not null default auth.uid() references auth.users(id) on delete cascade,
  message_id text not null,
  created_at timestamptz not null default now(),
  primary key (user_id, message_id)
);
alter table public.message_stars enable row level security;
drop policy if exists message_stars_own on public.message_stars;
create policy message_stars_own on public.message_stars
  for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- 3) bucket المستندات (خاص). المسار: <sender>/<receiver>/<file>
insert into storage.buckets (id, name, public)
values ('chat-files', 'chat-files', false)
on conflict (id) do nothing;

drop policy if exists chat_files_insert on storage.objects;
create policy chat_files_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'chat-files' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists chat_files_select on storage.objects;
create policy chat_files_select on storage.objects for select to authenticated
  using (bucket_id = 'chat-files'
         and (storage.foldername(name))[1] = auth.uid()::text
          or  bucket_id = 'chat-files'
         and (storage.foldername(name))[2] = auth.uid()::text);

drop policy if exists chat_files_delete on storage.objects;
create policy chat_files_delete on storage.objects for delete to authenticated
  using (bucket_id = 'chat-files' and (storage.foldername(name))[1] = auth.uid()::text);

-- ملاحظة: أعمدة file_path / file_name / file_type موجودة أصلاً في جدول messages.
