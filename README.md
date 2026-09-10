# WEGLOW Android

Native Android application built with Kotlin and Jetpack Compose.

Acne scans run locally on the phone using the bundled YOLOv8s ONNX model. No
scan server or USB connection is required. See [offline scan setup](docs/ON_DEVICE_SCANS.md)
for installation, model export, and verification details.

## Current refactor status

**Phase 1 — Project + Architecture** is applied to the supplied prototype. Existing screens and feature behavior are intentionally preserved; subsequent phases should be implemented in order.

Read:
- `docs/phase-0/ARCHITECTURE_PLAN.md`
- `docs/phase-0/STATE_MANAGEMENT_STRATEGY.md`
- `docs/phase-1/PHASE_1_REPORT.md`

## Local setup

1. Open the project in Android Studio.
2. Keep your normal `sdk.dir=...` entry in `local.properties`.
3. Create a Supabase project, then run `supabase/migrations/20260909000000_create_profiles.sql` in its SQL Editor (or apply it with the Supabase CLI).
4. Copy the WEGLOW configuration lines from `local.properties.example` to `local.properties`. Set the URL and publishable key shown under the Supabase project's API settings. Never use a service-role key in the app.
5. In Supabase Authentication, enable the Email provider. If email confirmation is enabled, a new user must confirm their email and sign in before onboarding answers can be saved.
6. Sync Gradle.
7. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` (Windows: `gradlew.bat ...`).

After authentication, the onboarding answers are upserted into `public.profiles` using the authenticated user's ID. Row Level Security restricts each user to their own record.

`local.properties`, IDE files and build outputs are intentionally excluded from source control/distribution.

## Phase 2 — Theme + reusable UI
The project now includes the WEGLOW design-system tokens and reusable Compose components under `ui/theme` and `ui/components`. See `docs/phase-2/PHASE_2_REPORT.md` for scope and verification steps.
