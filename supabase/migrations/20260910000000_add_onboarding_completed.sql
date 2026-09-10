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
