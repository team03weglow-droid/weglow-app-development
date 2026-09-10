-- Reconcile the repository profiles schema with the existing live Supabase schema.
-- This migration is additive and non-destructive.
-- IF NOT EXISTS makes it safe for environments where these columns already exist.

alter table public.profiles
    add column if not exists date_of_birth date,
    add column if not exists profile_image_url text;
