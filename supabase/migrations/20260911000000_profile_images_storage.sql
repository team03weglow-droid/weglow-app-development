-- Phase 6: private Storage bucket + per-user RLS for profile pictures.
--
-- This migration is additive and non-destructive. It does NOT touch
-- public.profiles: the profile_image_url column already exists from
-- 20260910010000_reconcile_profiles_schema.sql and is reused as the stable
-- storage-object reference for the picture.
--
-- It provisions:
--   1. a PRIVATE bucket "profile-images" (public = false)
--   2. storage.objects RLS policies confining every authenticated user to their
--      own top-level folder: <auth.uid()>/<something>
--
-- Object path convention used by the Android app:
--     <auth.uid()>/<epochMillis>.jpg
--
-- NOTE ON HOSTED SUPABASE:
-- On Supabase Cloud, storage.buckets / storage.objects are owned by the
-- "supabase_storage_admin" role and the SQL editor / migration runner executes
-- as a role that is permitted to manage them. If your migration runner cannot
-- apply this file due to ownership, create the bucket and the four policies
-- from the Dashboard (Storage -> Buckets, Storage -> Policies) using the exact
-- predicates below. See docs/phase-6/PHASE_6_IMPLEMENTATION_REPORT.md.

-- 1. Private bucket. public = false so objects are never served without a token.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'profile-images',
    'profile-images',
    false,
    5242880, -- 5 MiB, mirrors ProfileImageValidator.MAX_BYTES
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- 2. Per-user object policies on storage.objects.
--    RLS is already enabled on storage.objects by Supabase; the guard below is
--    harmless if it is re-run.
alter table storage.objects enable row level security;

-- SELECT: a user may read only files inside their own folder in this bucket.
drop policy if exists "profile-images read own" on storage.objects;
create policy "profile-images read own"
on storage.objects
for select
to authenticated
using (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- INSERT: a user may create files only inside their own folder.
drop policy if exists "profile-images insert own" on storage.objects;
create policy "profile-images insert own"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- UPDATE: a user may overwrite only their own files (both the existing row and
-- the new row must be inside their folder).
drop policy if exists "profile-images update own" on storage.objects;
create policy "profile-images update own"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
)
with check (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- DELETE: a user may delete only their own files.
drop policy if exists "profile-images delete own" on storage.objects;
create policy "profile-images delete own"
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);
