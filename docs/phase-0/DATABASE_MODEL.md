# Initial Database / Domain Model

This is a planning model only. Phase 7 owns database implementation and migrations.

## Existing profile concept

`profiles`
- `id`: authenticated user id / primary identity reference
- `full_name`: nullable text
- `age_range`: nullable text
- `skin_type`: nullable text
- `gender`: nullable text
- `is_skin_sensitive`: nullable boolean

Phase 1 introduces the backend-agnostic `UserProfile` domain model and keeps `ProfileRow` in the data layer.

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
