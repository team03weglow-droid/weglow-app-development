# WeGlow Phase 1 / Phase 2 Audit

**Environment note:** the audit machine had no JDK and no network to the Gradle
services, so the project could not be compiled, linted, or tested. Findings below
are from full static inspection of every source file plus the Gradle setup. Both
phase reports themselves state the build was never run.

---

## Verdict

| Area | Result | Condition |
|---|---|---|
| **Phase 1 – architecture/foundation** | **PASS (static)** | Must survive one real `./gradlew` run (see Blocker 1) |
| **Phase 2 – design system/reusable UI** | **PASS (static)** | Same build gate; scope is "foundation only" and it stays in scope |
| **Build itself** | **UNVERIFIED** | Never compiled by anyone; see Blockers 1–2 |

Phase 1 and Phase 2 do **not** break each other. Navigation, ViewModels, theme,
and components are internally consistent.

---

## Phase 1 — what was verified

- **Layering is clean and enforced.** `domain/` is pure Kotlin (zero `android.*`,
  `androidx.*`, `supabase`, or `data.*` imports). `ui/` and `feature/` never
  import `io.github.jan.supabase` or `data.remote`. Grep across the tree confirms
  no cross-layer leak. The `architectureCheck` task is real, registered, and
  wired to `preBuild`.
- **Real vertical slice:** `LoginScreen -> AuthViewModel -> AuthRepository ->
  SupabaseAuthRepository -> Supabase`, and onboarding `-> OnboardingViewModel ->
  ProfileRepository -> SupabaseProfileRepository`. `StateFlow` UI state
  throughout.
- **Global mutable holders removed** — no `OnboardingAnswersHolder` /
  `ScanPhotoHolder` anywhere; onboarding answers live in a lifecycle-scoped
  ViewModel.
- **Centralized routes** — `Destination` sealed class; no raw `navigate("…")` /
  `composable("…")` literals remain. All 15 nav call-sites match their screen
  composable signatures (checked individually).
- **Constructor injection** via `AppContainer`; Supabase repos take
  `SupabaseClient` as a param, not a singleton reach-in.
- **CameraX error path fixed** — `onError` no longer emits a URI for an unwritten
  cache file.
- **Config kept out of source** — `local.properties` is git-ignored and
  untracked; only a Supabase *publishable* (anon) key is present, which is
  client-safe by design. Backup disabled in the manifest.
- Tests exist (`AuthViewModelTest` with fakes + coroutine dispatcher,
  `ArchitectureFoundationTest`) and are logically sound.

## Phase 2 — what was verified

- `Color.kt`, `Type.kt`, `Tokens.kt` (spacing/radius/size/elevation) exist and
  are coherent; `WeGlowTheme` installs the typography.
- **No `Color(0x…)` hex literals under `ui/screens`** — the report's central
  claim holds; all hex now lives in `Color.kt`.
- Reusable components (`WeGlowPrimaryButton`, `WeGlowSecondaryButton`,
  `WeGlowTextField`, `WeGlowPasswordField`, `WeGlowCard`, loading/error/empty/
  progress, `WeGlowBottomNavigation`) exist and are consumed by `LoginScreen`,
  `SignUpScreen`, and `WeGlowApp`'s bottom bar.
- Social login and forgot-password are now non-interactive text, not fake
  buttons.
- No duplicated field/button implementations in the two migrated auth screens.

---

## Real blockers before Phase 3

### 1. Run the verification build once — it has never been done

Neither phase was compiled, linted, or tested. On a machine with the real
Android toolchain, run and get a green result:

```
./gradlew clean architectureCheck testDebugUnitTest lintDebug assembleDebug
```

Then smoke-test on device: login/signup/session/logout, onboarding save, home
tabs, camera capture. Until this passes, "Phase 1/2 complete" is unproven.

### 2. No Kotlin Android Gradle plugin is applied

`app/build.gradle.kts` applies `com.android.application`,
`kotlin.plugin.compose`, and `kotlin.plugin.serialization`, but **not
`org.jetbrains.kotlin.android`**. On AGP 8.x this is a hard failure (`The Compose
Compiler Gradle plugin requires the Kotlin Gradle plugin`). It only works if the
resolved AGP is a 9.x that provides built-in Kotlin. Confirm which AGP actually
resolves and that Kotlin sources compile; if not, add
`org.jetbrains.kotlin.android` to the version catalog and `plugins {}` block.
This is the single most likely reason the first build fails.

### 3. Confirm the toolchain pins actually resolve

`agp = 9.3.2`, Gradle `9.5.0`, `compileSdk/targetSdk 37`, Compose BOM
`2026.02.01` are all bleeding-edge, and the new-DSL blocks
(`compileSdk { version = release(37) }`,
`buildTypes { release { optimization { enable = false } } }`) are AGP-9-only
syntax. If the dev machine resolves an older AGP, configuration fails
immediately. Also `navigation-compose 2.8.0` is a 2024 release paired with a
2026 Compose BOM — check for a runtime/binary mismatch during the smoke test.

Everything else is not a blocker.

---

## Non-blockers (backlog / notes)

- **Phase 2 only migrated 2 screens.** The other 13 screens still use raw
  `fontFamily = JungeFont`, hardcoded `fontSize = N.sp`, and named literals
  (`Color.White`, `DarkGreen`, `Color.Black`). `ScanScreen` defines a public
  `PillButton` that duplicates `WeGlowPrimaryButton` and is used by two scan
  screens. This is explicitly deferred in the Phase 2 report, so it's
  in-scope-remaining, not a regression — but it's the bulk of the design-system
  work still to do.
- Phase 2 only ever claimed *hex-literal* removal; the many `Color.White` /
  `Color.Black` / `Color.Transparent` usages in screens are consistent with that
  narrower claim.
- Scan analysis is a timed animation; routines/home/profile carry prototype
  data. Explicitly deferred to feature phases — expected, not fake-passed-off-as-
  real.
- Password rule mismatch: `SignUpScreen` gates on 8+ chars with a digit,
  `AuthViewModel.signUp` only requires >=6 and its error text says "at least 6
  characters." Harmless (stricter client rule wins) but should be unified.
- `AuthViewModel.resolveStartupDestination()` calls
  `authRepository.currentUserId()` outside any `runCatching`, inside
  `viewModelScope.launch`. If Supabase is misconfigured, the lazy client init
  throws and crashes on launch instead of degrading. Config is present here so
  the app will start, but there's no graceful path.
- Minor: `.idea/` files were committed in the initial commit even though
  `.gitignore` now excludes them; `WeGlowApp.kt` has unused imports
  (`JungeFont`, `sp`) — warnings only.
