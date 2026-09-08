# WEGLOW Android

Native Android application built with Kotlin and Jetpack Compose.

## Current refactor status

**Phase 1 — Project + Architecture** is applied to the supplied prototype. Existing screens and feature behavior are intentionally preserved; subsequent phases should be implemented in order.

Read:
- `docs/phase-0/ARCHITECTURE_PLAN.md`
- `docs/phase-0/STATE_MANAGEMENT_STRATEGY.md`
- `docs/phase-1/PHASE_1_REPORT.md`

## Local setup

1. Open the project in Android Studio.
2. Keep your normal `sdk.dir=...` entry in `local.properties`.
3. Copy the WEGLOW configuration lines from `local.properties.example` to `local.properties` and supply your Supabase project values if backend code will be exercised.
4. Sync Gradle.
5. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` (Windows: `gradlew.bat ...`).

`local.properties`, IDE files and build outputs are intentionally excluded from source control/distribution.

## Phase 2 — Theme + reusable UI
The project now includes the WEGLOW design-system tokens and reusable Compose components under `ui/theme` and `ui/components`. See `docs/phase-2/PHASE_2_REPORT.md` for scope and verification steps.
