# Database / Domain Model

The initial profile schema is implemented by `supabase/migrations/20260909000000_create_profiles.sql`.

## Existing profile concept

`profiles`
- `id`: authenticated user id / primary identity reference
- `full_name`: nullable text
- `age_range`: nullable text
- `skin_type`: nullable text
- `gender`: nullable text
- `is_skin_sensitive`: nullable boolean
- `created_at`: creation timestamp
- `updated_at`: last-update timestamp

The Android app upserts this row after the signed-in user answers the final onboarding question. Row Level Security permits authenticated users to select, insert, and update only the row whose `id` matches their authentication ID. The backend-agnostic `UserProfile` domain model remains separate from the `ProfileRow` data model.

## Future entities (not implemented in Phase 1)

- products
- routines / routine_steps
- skin_scans / scan_findings
- hairstyle_scans / face_shape_results
- recommendations
- progress_entries
- reminders / preferences
- user image metadata

Exact schema, constraints, indexes and Row Level Security policies are deferred to Phase 7/10.
