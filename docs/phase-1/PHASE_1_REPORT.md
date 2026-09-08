# Phase 1 — Project + Architecture (revised after technical-lead review)

## Status
Phase 1 now ships an executable architecture seam rather than only folders and documentation.

Implemented:
- lifecycle-scoped presentation state with `AuthViewModel`, `OnboardingViewModel`, `ScanViewModel`, `DiscoverViewModel`, and `HairstyleViewModel`;
- `StateFlow`-based UI state for authentication, onboarding, scan photo state, and catalog state;
- a real vertical authentication slice: Compose login/signup -> AuthViewModel -> domain AuthRepository -> SupabaseAuthRepository -> Supabase;
- session-aware startup routing and real sign-out;
- onboarding answers retained in a lifecycle-scoped ViewModel and persisted through `ProfileRepository` instead of global mutable holders;
- old `OnboardingAnswersHolder` and `ScanPhotoHolder` removed;
- constructor injection into Supabase repositories, with dependency wiring centralized in `AppContainer`;
- centralized `Destination` route definitions instead of route string literals spread across the navigation graph;
- catalog and hairstyle recommendation content moved behind domain repository seams (currently in-memory implementations so later Supabase/AI sources can replace them);
- a build-time `architectureCheck` task attached to `preBuild`, preventing domain -> Android/data/Supabase dependencies and presentation -> Supabase remote dependencies;
- ViewModel unit-test scaffolding with fake repositories;
- CameraX capture error path corrected so a failed capture no longer emits an unwritten cache-file URI;
- source filename normalization for the onboarding/auth screens;
- Supabase configuration remains outside Kotlin source through local properties/BuildConfig;
- Android backup disabled for this prototype because future profile/scan data is privacy-sensitive;
- Material typography is now actually installed in `WeGlowTheme`.

## Dependency direction
`Compose screen -> feature ViewModel/UiState -> domain repository contract -> data repository -> infrastructure (Supabase)`

The `app/navigation` layer is the composition root and owns dependency wiring and route transitions. Feature and UI packages do not import Supabase infrastructure.

## Verification performed in this environment
- pure Kotlin domain layer compiles with the available Kotlin compiler;
- architecture boundary scan returns zero violations;
- no global onboarding/photo holder remains;
- no raw `navigate("...")` / `composable("...")` route calls remain outside the centralized destination model;
- no Supabase URL or publishable key is embedded in Kotlin source.

## Verification that MUST be run on the developer machine
This environment cannot download the Gradle 9.5 distribution from `services.gradle.org`, so a real Android build cannot be truthfully certified here.

Run:

```bash
./gradlew clean architectureCheck testDebugUnitTest lintDebug assembleDebug
```

Then run the app on the emulator/device and verify login/signup/session/onboarding, home tabs, camera capture, and logout.

Phase 1 should only be marked fully green after that command and runtime smoke test pass on the project's real Android toolchain.

## Explicitly deferred to later phases
Phase 1 does not make fake acne analysis or hardcoded routine content into real backend/ML features. The large existing Compose screens are preserved to avoid an unnecessary visual rewrite. Their UI component extraction/design-system cleanup belongs to Phase 2, while real scan analysis and recommendation engines belong to later feature phases.
