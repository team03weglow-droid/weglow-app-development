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
