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
