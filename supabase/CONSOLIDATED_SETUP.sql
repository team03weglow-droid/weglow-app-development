-- WEGlow Phase 1-7 consolidated setup for hosted Supabase.
-- Paste this entire file into Dashboard > SQL Editor > New query > Run.
-- It is idempotent (safe to re-run).
-- SQL Editor runs statements in order; session_replication_role/txid tricks are avoided,
-- and every migration is additive / IF NOT EXISTS / drop-if-exists-first.

-- ============ 20260909000000_create_profiles.sql ============
-- Stores the answers collected during onboarding for each authenticated user.
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    full_name text,
    age_range text,
    skin_type text,
    gender text,
    is_skin_sensitive boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

drop policy if exists "Users can read their own profile" on public.profiles;
create policy "Users can read their own profile"
on public.profiles
for select
to authenticated
using ((select auth.uid()) = id);

drop policy if exists "Users can insert their own profile" on public.profiles;
create policy "Users can insert their own profile"
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = id);

drop policy if exists "Users can update their own profile" on public.profiles;
create policy "Users can update their own profile"
on public.profiles
for update
to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

create or replace function public.set_profiles_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_profiles_updated_at();

-- ============ 20260910000000_add_onboarding_completed.sql ============
-- Phase 5: explicit onboarding completion flag for public.profiles.
--
-- The initial schema (20260909000000_create_profiles.sql) treated "a profiles
-- row exists" as proof that onboarding finished. That is unsafe: any future
-- partial write (for example, storing a display name at signup, or a seed row
-- created by a trigger) would incorrectly look complete and skip the
-- questionnaire with missing data.
--
-- This migration adds an explicit, NOT NULL flag. The Android app sets it to
-- true only as part of the successful final onboarding profile upsert.

alter table public.profiles
    add column if not exists onboarding_completed boolean not null default false;

-- Backfill decision (deliberately NOT "mark every existing row completed"):
--
-- Only rows that already contain every REQUIRED onboarding answer are treated as
-- historically complete. In the shipped app the only writer of these columns is
-- the final onboarding save, which writes them together, so their combined
-- presence is reliable evidence that the questionnaire was finished.
--
-- skin_type is intentionally excluded from the condition because the onboarding
-- flow lets the user tap "Skip for now", leaving it legitimately null.
--
-- Any row missing a required answer keeps the safe default (false) and its owner
-- is asked the onboarding questions again rather than being silently advanced
-- past onboarding with incomplete data.

update public.profiles
set onboarding_completed = true
where onboarding_completed = false
  and age_range is not null
  and gender is not null
  and is_skin_sensitive is not null;

-- Row Level Security is unchanged. Adding a column does not alter existing
-- policies; the per-user SELECT/INSERT/UPDATE policies from the initial
-- migration continue to restrict every row to auth.uid() = id. No new policy is
-- required because onboarding_completed lives on the same row the user already
-- owns. The one-time UPDATE above runs with migration privileges, never from the
-- client and never with a service-role key embedded in the app.

-- ============ 20260910010000_reconcile_profiles_schema.sql ============
-- Reconcile the repository profiles schema with the existing live Supabase schema.
-- This migration is additive and non-destructive.
-- IF NOT EXISTS makes it safe for environments where these columns already exist.

alter table public.profiles
    add column if not exists date_of_birth date,
    add column if not exists profile_image_url text;

-- ============ 20260911000000_allow_authenticated_product_reads.sql ============
-- The Discover catalog is available to signed-in app users.
alter table public.products enable row level security;

grant select on table public.products to authenticated;

do $$
begin
    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'products'
          and policyname = 'Authenticated users can view products'
    ) then
        create policy "Authenticated users can view products"
            on public.products
            for select
            to authenticated
            using (true);
    end if;
end
$$;

-- ============ 20260911000000_profile_images_storage.sql ============
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

-- ============ 20260911010000_profiles_onboarding_integrity.sql ============
-- Phase 7: enforce the onboarding-completion invariant at the database level.
--
-- The Android app and the Phase 5 backfill migration
-- (20260910000000_add_onboarding_completed.sql) already implement this rule in
-- application logic: `onboarding_completed` is only ever set to true together
-- with age_range, gender, and is_skin_sensitive in the same write
-- (OnboardingViewModel.save()). skin_type is deliberately excluded because the
-- onboarding flow allows "Skip for now", leaving it legitimately null.
--
-- This migration turns that already-true invariant into a CHECK constraint so
-- it holds regardless of which client or script writes to the table in the
-- future, instead of relying solely on Kotlin call discipline.
--
-- Safe to run on the live table: the Phase 5 backfill only set
-- onboarding_completed = true for rows that already satisfied this exact
-- predicate, and every write path since (OnboardingViewModel.save()) writes
-- all three columns together. See section 13 of the Phase 7 report for a
-- verification query to run before applying this migration.

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.profiles'::regclass
          and conname = 'profiles_onboarding_requires_answers'
    ) then
        alter table public.profiles
            add constraint profiles_onboarding_requires_answers
            check (
                onboarding_completed = false
                or (
                    age_range is not null
                    and gender is not null
                    and is_skin_sensitive is not null
                )
            );
    end if;
end
$$;

-- ============ 20260911020000_harden_products_privileges.sql ============
-- Phase 7: defense-in-depth on public.products table-level privileges.
--
-- 20260911000000_allow_authenticated_product_reads.sql already enabled RLS and
-- added a read-only SELECT policy for authenticated users. That migration did
-- not state what other table-level privileges exist on public.products,
-- because the table itself was not created by a repository migration (it was
-- populated from an external product dataset directly in the hosted project;
-- see the Phase 7 report for the recommended follow-up to capture its real
-- schema with `supabase db pull`).
--
-- This migration makes the intended privilege set explicit and idempotent
-- regardless of how the table was provisioned: authenticated users may only
-- SELECT from the catalog, never INSERT/UPDATE/DELETE it, and the anonymous
-- role has no access at all (the app requires sign-in before Discover loads).
-- REVOKE on a privilege that was never granted is a safe no-op.

revoke all on table public.products from anon;
revoke insert, update, delete, truncate, references, trigger
    on table public.products
    from authenticated;

grant select on table public.products to authenticated;


-- ============ 20260915000000_add_env_to_profiles.sql ============
-- UV/Environment data for personalized skincare advice in the chat AI.
alter table public.profiles
    add column if not exists uv_index double precision,
    add column if not exists uv_category text,
    add column if not exists humidity int,
    add column if not exists location_name text,
    add column if not exists last_env_at timestamptz;


-- Security hardening, 2026-10-31. Review docs/SECURITY_HARDENING.md before deployment.

-- ============ 20261031000000_user_data_security.sql ============
-- Capture the app-owned tables and enforce ownership even if older permissive policies exist.
-- Existing table shapes are preserved. Apply in staging first and inspect legacy constraints.
begin;
create table if not exists public.saved_products (
    profile_id uuid not null references public.profiles(id) on delete cascade,
    product_no integer not null references public.products("No") on delete cascade,
    created_at timestamptz not null default now(),
    primary key (profile_id, product_no)
);
create table if not exists public.cart_items (
    profile_id uuid not null references public.profiles(id) on delete cascade,
    product_no integer not null references public.products("No") on delete cascade,
    quantity integer not null default 1 check (quantity between 1 and 999),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (profile_id, product_no)
);
create table if not exists public."Scan-profiles" (
    id uuid primary key default gen_random_uuid(),
    user_prof_id uuid not null references public.profiles(id) on delete cascade,
    "Date_time" timestamptz not null default now(),
    dark_spots integer not null default 0,
    open_pores integer not null default 0,
    black_heads integer not null default 0,
    acne_scars integer not null default 0,
    papules integer not null default 0,
    whiteheads integer not null default 0,
    nodules integer not null default 0,
    cysts integer not null default 0,
    pustules integer not null default 0,
    freckles integer not null default 0
);

do $$
declare item record;
begin
    for item in select * from (values
        ('saved_products', 'profile_id'), ('cart_items', 'profile_id'), ('Scan-profiles', 'user_prof_id')
    ) as owned(table_name, owner_column)
    loop
        execute format('alter table public.%I enable row level security', item.table_name);
        execute format('revoke all on public.%I from anon, authenticated', item.table_name);
        execute format('grant select, insert, update, delete on public.%I to authenticated', item.table_name);
        execute format('drop policy if exists account_owner_guard on public.%I', item.table_name);
        execute format('create policy account_owner_guard on public.%I as restrictive for all to public using ((select auth.uid()) = %I) with check ((select auth.uid()) = %I)', item.table_name, item.owner_column, item.owner_column);
        execute format('drop policy if exists account_owner_access on public.%I', item.table_name);
        execute format('create policy account_owner_access on public.%I for all to authenticated using ((select auth.uid()) = %I) with check ((select auth.uid()) = %I)', item.table_name, item.owner_column, item.owner_column);
    end loop;
end $$;
-- NOT VALID avoids destroying or silently rewriting legacy data. New writes are checked.
alter table public.cart_items drop constraint if exists cart_quantity_bounds;
alter table public.cart_items add constraint cart_quantity_bounds check (quantity between 1 and 999) not valid;
alter table public.profiles drop constraint if exists profiles_allowed_age_range;
alter table public.profiles add constraint profiles_allowed_age_range
    check (age_range is null or age_range in ('14–24', '25–35', '36–45', '46–59', '60+')) not valid;
alter table public.profiles drop constraint if exists profiles_allowed_gender;
alter table public.profiles add constraint profiles_allowed_gender
    check (gender is null or gender in ('Female', 'Male', 'Other/Prefer not to say')) not valid;
-- Prevent pre-existing broad policies from defeating profile ownership.
drop policy if exists profiles_owner_guard on public.profiles;
create policy profiles_owner_guard on public.profiles as restrictive for all to public
    using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
revoke all on public.profiles from anon;
revoke truncate, references, trigger on public.profiles from authenticated;
revoke all on public.hairstyles from anon;
revoke insert, update, delete, truncate, references, trigger on public.hairstyles from authenticated;
commit;

-- These restrictive policies combine with the existing own-folder policies.
drop policy if exists "profile-images bounded paths" on storage.objects;
create policy "profile-images bounded paths" on storage.objects as restrictive for all to public
using (bucket_id <> 'profile-images' or (storage.foldername(name))[1] = (select auth.uid()::text))
with check (bucket_id <> 'profile-images' or (
    (storage.foldername(name))[1] = (select auth.uid()::text)
    and name ~ '^[^/]+/[^/]+\.(jpg|jpeg|png|webp)$'
    and octet_length(name) <= 256
));

-- ============ 20261031010000_api_quotas.sql ============
-- Quotas live in Postgres, shared by all Edge Function instances. No client can reserve/release.
begin;
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
create table if not exists private.api_requests (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    service text not null check (service in ('chat', 'weather', 'delete-account')),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    finished_at timestamptz
);
create index if not exists api_requests_user_service_time on private.api_requests(user_id, service, created_at);
alter table private.api_requests enable row level security;

create or replace function public.reserve_api_request(p_user_id uuid, p_service text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare minute_limit integer; day_limit integer; active_limit integer; reservation uuid;
begin
    if p_service = 'chat' then minute_limit := 10; day_limit := 100; active_limit := 2;
    elsif p_service = 'weather' then minute_limit := 30; day_limit := 500; active_limit := 4;
    elsif p_service = 'delete-account' then minute_limit := 2; day_limit := 10; active_limit := 1;
    else raise exception 'Invalid service'; end if;
    perform pg_advisory_xact_lock(hashtextextended(p_user_id::text || ':' || p_service, 0));
    delete from private.api_requests where user_id = p_user_id and service = p_service and created_at < now() - interval '2 days';
    if (select count(*) from private.api_requests where user_id = p_user_id and service = p_service and created_at >= now() - interval '1 minute') >= minute_limit
    or (select count(*) from private.api_requests where user_id = p_user_id and service = p_service and created_at >= date_trunc('day', now() at time zone 'UTC') at time zone 'UTC') >= day_limit
    or (select count(*) from private.api_requests where user_id = p_user_id and service = p_service and finished_at is null and expires_at > now()) >= active_limit then
        return null;
    end if;
    insert into private.api_requests(user_id, service, expires_at)
        values (p_user_id, p_service, now() + interval '90 seconds') returning id into reservation;
    return reservation;
end $$;
create or replace function public.release_api_request(p_request_id uuid)
returns void language sql security definer set search_path = '' as $$
    update private.api_requests set finished_at = now() where id = p_request_id;
$$;
revoke all on function public.reserve_api_request(uuid, text) from public, anon, authenticated;
revoke all on function public.release_api_request(uuid) from public, anon, authenticated;
grant execute on function public.reserve_api_request(uuid, text) to service_role;
grant execute on function public.release_api_request(uuid) to service_role;
commit;

-- ============ 20261031020000_account_deletion.sql ============
-- Called only by the verified delete-account Edge Function, after storage cleanup.
-- Database deletion is atomic; existing access tokens lose their backing profile/data.
create or replace function public.delete_account_data(p_user_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if exists (select 1 from storage.objects where bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = p_user_id::text) then
        raise exception 'Remove account photos before deleting account';
    end if;
    delete from public."Scan-profiles" where user_prof_id = p_user_id;
    delete from public.cart_items where profile_id = p_user_id;
    delete from public.saved_products where profile_id = p_user_id;
    delete from public.profiles where id = p_user_id;
    delete from auth.users where id = p_user_id;
end $$;
revoke all on function public.delete_account_data(uuid) from public, anon, authenticated;
grant execute on function public.delete_account_data(uuid) to service_role;
