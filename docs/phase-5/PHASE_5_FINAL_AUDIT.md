# PHASE 5 FINAL AUDIT

_Read-only audit. No Kotlin, SQL, Gradle, test, configuration, or migration file was modified. No commit, push, merge, reset, or branch switch. Branch: `phase-5-onboarding`. The only file created is this document._

## Final Verdict

**PASS**

Phase 5 onboarding is correctly and safely implemented. Every required behavior (1–14 below) is satisfied by code inspection and by the manual end-to-end test against the real Supabase project. The single failing unit test (`YoloPostProcessorTest`) is pre-existing teammate scan code and was not caused by Phase 5.

There is one **non-blocking** correctness gap outside the Phase 5 changeset: the repository migration history no longer matches the live Supabase `profiles` schema. This is inherited technical debt in a non-Phase-5 file and does not make the Phase 5 changeset unsafe to commit, but it must be reconciled as a follow-up.

---

## Blocking Issues

**None.**

Verification of the 18 required points:

| # | Requirement | Result | Evidence |
|---|---|---|---|
| 1 | New users cannot be marked onboarding-complete before persistence succeeds | PASS | `OnboardingViewModel.save()` puts `onboardingCompleted = true` only in the `UserProfile` payload passed to `profileRepository.saveProfile(...)`. The DB row is written by a single atomic `client.postgrest["profiles"].upsert(...)`. `_uiState … saveCompleted = true` is set **only** inside `.onSuccess`; `.onFailure` sets `errorMessage`, `isSaving = false`, and leaves `saveCompleted = false`, so navigation past onboarding never fires on a failed save. There is no code path that writes `onboarding_completed = true` independently of the full row. |
| 2 | All onboarding answers retained in `OnboardingViewModel` | PASS | Single `OnboardingViewModel` instance hoisted at `WeGlowApp` scope and shared by every onboarding `composable`. `OnboardingUiState` holds `fullName, ageRange, skinType, gender, isSkinSensitive`. Each screen callback (`setAge`, `setSkinType`, `setGender`, `setSensitivityAndSave`) updates state via `.copy()`; `save()` reads the whole `_uiState.value`. |
| 3 | Authenticated Supabase user ID used for persistence | PASS | `save()` reads `authRepository.currentUserId()` → `SupabaseAuthRepository.currentUserId()` = `client.auth.currentUserOrNull()?.id`, and uses it as `UserProfile.id` → `ProfileRow.id` → upsert primary key. `null` → error + early return, no save. |
| 4 | No user ID trusted from UI input | PASS | No onboarding screen callback carries an id (`onContinue(String)`, `onNext(String)`, `onAnswer(Boolean)`). `OnboardingViewModel.start(fullName: String?)` takes only a name. `ProfileViewModel.refresh()` and `getProfile(...)` are called only with `authRepository.currentUserId()`. Server-side RLS `with check ((select auth.uid()) = id)` is the second line of defense. |
| 5 | `onboarding_completed` is the actual source used by `hasCompletedOnboarding()` | PASS | `SupabaseProfileRepository.hasCompletedOnboarding` = `select … decodeList<ProfileRow>().isOnboardingCompleted()`, where `internal fun List<ProfileRow>.isOnboardingCompleted() = firstOrNull()?.onboarding_completed == true`. A row that exists with `onboarding_completed = false` returns `false`. Unit-tested directly (`SupabaseProfileRepositoryTest.rowExistsButFlagFalse_isNotCompleted`). |
| 6 | Returning completed users skip onboarding | PASS | `AuthViewModel.determineDestination()` → `if (hasCompletedOnboarding(userId).getOrDefault(false)) HOME else ONBOARDING`, applied at startup (`_startupDestination`) and post-auth (`AuthEvent.SignedIn(destination)`). `WeGlowApp` routes `HOME` straight to `Destination.Home`. Manually confirmed: re-login to a completed account lands on Home with no onboarding. |
| 7 | Incomplete authenticated users route to onboarding | PASS | Same `determineDestination()` returns `ONBOARDING` when the flag is not `true`. `WeGlowApp` Loading (`ONBOARDING` branch), Login (`SignedIn` → not-HOME branch), and Signup (`SignedUp`) all navigate to `Destination.AgeSelection`. |
| 8 | Manual signup full name preserved and persisted | PASS | `SignUpScreen` → `onCreateAccount(fullName.trim(), …)` → `AuthViewModel.signUp` (rejects blank) → `AuthEvent.SignedUp(fullName.trim())` → `WeGlowApp` calls `onboardingViewModel.start(event.fullName)` → `OnboardingUiState.fullName` → `save()` writes `full_name = state.fullName.ifBlank { null }`. Manually confirmed: real name displayed instead of "Team 03". |
| 9 | Google-authenticated onboarding / name handling is safe | PASS (by inspection) | Google sign-in emits `AuthEvent.SignedIn`; if incomplete, `WeGlowApp` calls `onboardingViewModel.start()` (no arg), which fully resets state and resolves the name via `authRepository.currentUserDisplayName()` → `SupabaseAuthRepository` reads `userMetadata` keys `full_name` → `name` → `user_name` → `preferred_username`, first non-blank trimmed, else `null`. No fabricated name; user id still comes from `currentUserId()`. Not exercised in the manual E2E (email only) — see non-blocking issues. |
| 10 | Home and Profile use persisted profile name, not hardcoded "Team 03" | PASS | `HomeScreen`: `displayName?.let { "Hello, $it" } ?: "Hello there"`. `ProfileScreen`: `displayName ?: "Your Profile"`. `WeGlowApp` passes `profileState.displayName` and calls `profileViewModel.refresh()` on entry. `ProfileViewModel.refresh()` → `profileRepository.getProfile(userId).getOrNull()?.fullName` ?: `authRepository.currentUserDisplayName()`. Manually confirmed. |
| 11 | Profile read-back stays behind `ProfileRepository` | PASS | `getProfile(userId): Result<UserProfile?>` is declared on the `ProfileRepository` domain interface and implemented only in `SupabaseProfileRepository`. `ProfileViewModel` depends on the interface. |
| 12 | No Supabase implementation leaked into UI / domain / feature | PASS | `io.github.jan.supabase` and `kotlinx.serialization.json` appear only under `data/`. `AuthRepository`, `ProfileRepository`, `UserProfile` (domain) and `OnboardingViewModel`, `ProfileViewModel` (feature) import only domain interfaces / androidx.lifecycle / coroutines. `HomeScreen` / `ProfileScreen` gained only a `String?` parameter. The `architectureCheck` Gradle task passes. |
| 13 | No unrelated Phase 1–4 or teammate scan / ONNX / backend work changed | PASS | `git status` shows zero changes under `data/scan/`, `assets/acne/`, `backend/`, no `*.onnx`, no `build.gradle*`, no `libs.versions.toml`, no `AndroidManifest.xml`, no `AppContainer.kt` / `ViewModelFactory.kt` / `Destination.kt`. `WeGlowApp.kt` diff is limited to onboarding entry + Home/Profile name wiring; all scan/hairstyle composables untouched. |
| 14 | Phase 5 tests adequately cover required behavior | PASS | See "Phase 5 Test Coverage" below. 22 Phase-5-specific assertions across `OnboardingViewModelTest` (7), `SupabaseProfileRepositoryTest` (6), `AuthViewModelTest` (+1), `ArchitectureFoundationTest` (+1). |
| 15 | Git diff review — accidental / unrelated Phase 5 modifications | PASS | Every hunk is Phase-5-purposeful. See "Git Diff Review". |
| 16 | Migration vs live schema | FAIL | Repo migration set does not represent live. See "Database Migration Consistency". |
| 17 | Mismatch to correct before committing | Identified | Migration reconciliation + RLS confirmation (follow-up, non-blocking for the code commit). See "Database Migration Consistency". |
| 18 | `YoloPostProcessorTest` failure attribution | Not caused by Phase 5 | See "Remaining Test Issue". |

---

## Non-Blocking Issues

1. **Repo migration history diverges from the live `profiles` schema.** Live contains `date_of_birth` and `profile_image_url`, which no repository migration creates. `20260909000000_create_profiles.sql` describes a table that does not match how the live table was actually built (live originally had `id, full_name, gender, date_of_birth, profile_image_url, created_at, updated_at` and lacked `age_range, skin_type, is_skin_sensitive`). The live additions of `age_range`, `skin_type`, `is_skin_sensitive` were applied ad-hoc and have no dedicated incremental migration. Running `supabase db reset` from the repo migrations would **not** reproduce production. Inherited debt — not introduced by Phase 5; `20260909000000` is not a Phase 5 file and must not be edited in this task.

2. **RLS status on the live `profiles` table is unverified from here.** `20260909000000_create_profiles.sql` defines correct per-user `SELECT` / `INSERT` / `UPDATE` policies and `enable row level security`, but because the live table's creation history differs from that migration, the team must confirm in the Supabase dashboard that RLS is enabled and those policies exist on the live table. The app relies on RLS as the server-side guarantee that a user can only read/write their own row (point 4).

3. **`determineDestination()` uses `hasCompletedOnboarding(...).getOrDefault(false)`.** A transient query failure routes an already-completed user back through onboarding; the re-answer performs an idempotent upsert (`onboarding_completed` stays `true`), so there is no data corruption, only a UX regression. Pre-existing Phase 4 pattern, retained unchanged by Phase 5.

4. **Double-submit window on the final Yes/No.** `isSaving` is set inside the launched coroutine, so two very fast taps on the final answer can enqueue two `save()` calls before the buttons disable. Both perform the same idempotent upsert for the same authenticated id with the same data; no completion/persistence correctness impact.

5. **Google OAuth onboarding path not covered by the manual E2E.** The manual test used email signup only. The Google path is correct by inspection (point 9) but not exercised end-to-end.

6. **`ProfileViewModel.refresh()` runs on every Home/Profile entry** via `LaunchedEffect(Unit)`, i.e. one network read each time either tab is opened. Functionally fine; minor.

7. **`ProfileRow` does not model `date_of_birth` or `profile_image_url`.** Runtime-safe (Supabase's `Json` uses `ignoreUnknownKeys = true`, and `getProfile` / `hasCompletedOnboarding` decode via `decodeList<ProfileRow>()`), and `profile_image_url` is intentionally deferred to Phase 6, but the code model and the live table are not 1:1.

8. **Trailing newline absent** in `AuthRepository.kt`, `SupabaseAuthRepository.kt`, `AuthViewModelTest.kt`. Pre-existing style in those files; Phase 5 edits did not add or remove it. Cosmetic.

---

## Database Migration Consistency

**FAIL.**

Exact reasoning:

- **The Phase 5 migration itself is correct.** `supabase/migrations/20260910000000_add_onboarding_completed.sql` performs `alter table public.profiles add column if not exists onboarding_completed boolean not null default false;` followed by a conditional, idempotent backfill (`update … set onboarding_completed = true where onboarding_completed = false and age_range is not null and gender is not null and is_skin_sensitive is not null`). This is safe, non-destructive, and accurately represents the one column that was added to the live table for Phase 5. `onboarding_completed = true` was manually confirmed present in the live table.

- **The repository migration set as a whole does not represent the live schema.**
  - Live `profiles` contains `date_of_birth` and `profile_image_url`. **No repository migration creates either column.** A clean apply of the repo migrations yields a `profiles` table missing both.
  - `20260909000000_create_profiles.sql` creates `id, full_name, age_range, skin_type, gender, is_skin_sensitive, created_at, updated_at`. The live table, before Phase 5, was `id, full_name, gender, date_of_birth, profile_image_url, created_at, updated_at`. The column sets differ in both directions, so this migration was evidently **not** the source of the live table.
  - The live additions of `age_range`, `skin_type`, `is_skin_sensitive` (done "safely without deleting existing data") exist only inside the `create` migration that live did not use; there is **no incremental `ALTER TABLE … ADD COLUMN`** migration in the repo recording those changes as applied to the live table.

- **Consequence:** `supabase db reset` / applying the committed migrations to a fresh database produces a schema that is not production. Phase 6 (which needs `profile_image_url`) and any new environment would be built on a divergent baseline.

- **What must be corrected (follow-up, does not block the Phase 5 code commit):**
  1. Add a new forward migration that brings a clean database to parity with live — either an explicit `alter table public.profiles add column if not exists date_of_birth date; add column if not exists profile_image_url text;` (least disruptive, non-destructive), or regenerate a baseline with `supabase db pull` and reconcile the migration folder.
  2. Confirm (dashboard) that RLS is enabled on the live `profiles` table with per-user `SELECT` / `INSERT` / `UPDATE` policies equivalent to `20260909000000_create_profiles.sql`; add a migration if any are missing.
  3. Optionally add `date_of_birth` / `profile_image_url` to `ProfileRow` when Phase 6 needs them (out of Phase 5 scope).

This divergence pre-dates Phase 5 and lives in `20260909000000_create_profiles.sql`, which this task must not modify.

---

## Manual End-to-End Verification

Recorded from the manual test against the real Supabase project:

| Check | Result |
|---|---|
| New email account creation | Confirmed working. Supabase "Confirm email" disabled deliberately, because the required app flow is immediate signup → onboarding. |
| New authenticated user reaches the existing onboarding questions | Confirmed. |
| All onboarding questions can be completed | Confirmed. |
| Final onboarding save succeeds | Confirmed. |
| Answers persisted to the real `profiles` table | Confirmed — `age_range` (text), `skin_type` (text), `is_skin_sensitive` (boolean) present in the live row. |
| `onboarding_completed = true` after a successful save | Confirmed in the live table. |
| Real user name displayed instead of hardcoded "Team 03" | Confirmed on Home and Profile. |
| Logout | Confirmed working. |
| Login back into the same completed account | Confirmed — goes directly to Home, onboarding is **not** repeated. |
| Completed returning user skips onboarding | Confirmed. |
| Profile picture upload | Intentionally **not** part of Phase 5 — deferred to Phase 6. |

Live `profiles` columns after the safe, non-destructive additions:
`id`, `full_name`, `gender`, `date_of_birth`, `profile_image_url`, `age_range`, `skin_type`, `is_skin_sensitive`, `onboarding_completed`, `created_at`, `updated_at`.

---

## Architecture Verification

**Boundaries remain intact.**

- `architectureCheck` Gradle task **passes** (it runs as a `preBuild` dependency and scans `domain/`, `feature/`, `ui/` for forbidden tokens including `io.github.jan.supabase`, and `domain/` additionally for `import android.` / `import androidx.` / `com.example.weglow.data`).
- New/modified domain files (`UserProfile`, `AuthRepository`, `ProfileRepository`) import only `kotlinx.coroutines.flow.Flow` and the domain model — no Android, no Supabase, no data-layer types.
- New/modified feature files (`OnboardingViewModel`, `ProfileViewModel`) depend only on the `AuthRepository` / `ProfileRepository` domain interfaces plus `androidx.lifecycle` + coroutines. No `io.github.jan.supabase`, no `com.example.weglow.data.remote`.
- UI files (`HomeScreen`, `ProfileScreen`) received a `displayName: String?` parameter only.
- All Supabase / `kotlinx.serialization.json` usage is confined to `data/` (`SupabaseProfileRepository`, `SupabaseAuthRepository`, `ProfileRow`).
- Dependency injection is unchanged: `ProfileViewModel` is created in `WeGlowApp` with the same `viewModelFactory { … }` pattern as the existing five ViewModels, pulling `container.authRepository` / `container.profileRepository`. `AppContainer` and `ViewModelFactory` were not modified.

Data flow is `UI → ViewModel → ProfileRepository / AuthRepository → SupabaseProfileRepository / SupabaseAuthRepository → Supabase`, with no shortcut.

---

## Git Diff Review

Branch `phase-5-onboarding`, working tree vs `HEAD`. **14 modified, 2 code files created, 1 migration created, plus docs.** No staged changes, no commits.

### Modified (tracked)

| File | Phase 5 purpose | Unrelated change? |
|---|---|---|
| `app/.../domain/model/UserProfile.kt` | `+ val onboardingCompleted: Boolean = false` | No |
| `app/.../domain/repository/ProfileRepository.kt` | `+ suspend fun getProfile(userId): Result<UserProfile?>` + doc on `hasCompletedOnboarding` | No |
| `app/.../domain/repository/AuthRepository.kt` | `+ fun currentUserDisplayName(): String?` | No |
| `app/.../data/model/ProfileRow.kt` | `+ onboarding_completed` field; `toProfileRow()` maps it; `+ fun ProfileRow.toUserProfile()` | No |
| `app/.../data/repository/SupabaseProfileRepository.kt` | `+ getProfile`; `hasCompletedOnboarding` now uses `isOnboardingCompleted()`; `+ internal fun List<ProfileRow>.isOnboardingCompleted()` | No |
| `app/.../data/repository/SupabaseAuthRepository.kt` | `+ currentUserDisplayName()` reading `userMetadata`; `+ import JsonPrimitive, contentOrNull` | No |
| `app/.../feature/onboarding/OnboardingViewModel.kt` | `start(fullName: String? = null)` resets state + resolves identity; saved `UserProfile` sets `onboardingCompleted = true` | No |
| `app/.../navigation/WeGlowApp.kt` | `+ profileViewModel` + `profileState`; `onboardingViewModel.start()` on the restored-session `ONBOARDING` branch and the post-auth `SignedIn`→not-HOME branch; `LaunchedEffect { profileViewModel.refresh() }` + `displayName =` on Home and Profile destinations | No — scan/hairstyle/discover/routines composables untouched |
| `app/.../ui/screens/HomeScreen.kt` | `+ displayName: String? = null`; `"Hello, Team 03"` → `displayName?.let { "Hello, $it" } ?: "Hello there"` | No |
| `app/.../ui/screens/ProfileScreen.kt` | `+ displayName: String? = null`; `"Team 03"` → `displayName ?: "Your Profile"` | No |
| `app/.../test/ArchitectureFoundationTest.kt` | `+ onboardingCompleted = true` in fixture; `+ assertEquals(true, row.onboarding_completed)` | No |
| `app/.../test/feature/auth/AuthViewModelTest.kt` | Fakes implement `currentUserDisplayName()` / `getProfile()`; `FakeProfileRepository` gains optional `storedProfile`; `+ restoredSession_withProfileRowButIncompleteOnboarding_routesToOnboarding` | No — all 8 original tests retained |
| `app/.../test/feature/onboarding/OnboardingViewModelTest.kt` | Fakes rebuilt to the current Phase 4 `AuthRepository` contract + new members; `RecordingProfileRepository` supports injected failure + records attempted vs persisted; `+` 6 Phase 5 test cases. Fixes audit blocker B4 (unit-test source set previously did not compile) | No |
| `docs/phase-0/DATABASE_MODEL.md` | `+ 1` bullet documenting `onboarding_completed` and the new migration | No |

### Created (untracked)

| File | Phase 5 purpose |
|---|---|
| `supabase/migrations/20260910000000_add_onboarding_completed.sql` | Additive migration for `onboarding_completed` + conditional backfill; leaves `20260909000000_create_profiles.sql` untouched |
| `app/.../feature/profile/ProfileViewModel.kt` | Loads the signed-in user's persisted profile for Home/Profile via domain interfaces only |
| `app/.../test/data/repository/SupabaseProfileRepositoryTest.kt` | Pure tests for the completion-decision rule and `ProfileRow ⇄ UserProfile` mapping |
| `docs/phase-5/PHASE_5_AUDIT.md` | Pre-implementation audit (from an earlier step) |
| `docs/phase-5/PHASE_5_IMPLEMENTATION_REPORT.html` / `.pdf` | Implementation report (from an earlier step) — documents, not project code |
| `docs/phase-5/PHASE_5_FINAL_AUDIT.md` | This document |

### Accidental / unrelated modifications

**None found.** Every code hunk maps to a Phase 5 requirement. No formatting-only churn, no reordering of unrelated code, no dependency/version changes, no changes under `data/scan/`, `assets/`, `backend/`, or to Gradle/manifest/DI files.

---

## Remaining Test Issue

`com.example.weglow.data.scan.YoloPostProcessorTest > ignoresBelowThresholdInvalidAndPaddingOnlyDetections` — `java.lang.AssertionError` at `YoloPostProcessorTest.kt:37` (`assertTrue(detections.isEmpty())`). 1 of 6 in that suite; all other 33 unit tests pass.

**Was it caused by Phase 5? No.**

- `app/src/main/java/com/example/weglow/data/scan/YoloPostProcessor.kt` was last modified in commit `7b4b8bb` ("Show low-confidence detections without confidence labels"), a scan-feature change that deliberately altered low-confidence handling. `app/src/test/java/com/example/weglow/data/scan/YoloPostProcessorTest.kt` was last modified in the earlier commit `945ec34` and was never updated to match `7b4b8bb`'s behavior change.
- The failure was **masked** before Phase 5: on a clean checkout the unit-test source set did not compile, because `OnboardingViewModelTest`'s fake `AuthRepository` was missing the Phase 4 members `authenticationState` and `signInWithGoogle` (audit blocker B4). With no compilation, `testDebugUnitTest` never executed any test. Stashing the Phase 5 changes reproduces the pre-existing `compileDebugUnitTestKotlin FAILED`, confirming the scan test could not have been passing beforehand.
- Phase 5 changed no file under `data/scan/` and nothing `YoloPostProcessor` depends on. Phase 5 only made the pre-existing failure **visible** by repairing the test-module compilation.
- Per the task constraints (do not modify the YOLO/scan implementation), `YoloPostProcessor.kt` and `YoloPostProcessorTest.kt` were left untouched. The fix is one line and belongs to the scan-feature owner: either update the test's expectation to match the intended low-confidence behavior, or reinstate the sub-threshold filter in `YoloPostProcessor.decode`.

Effect on the aggregate Gradle command: `./gradlew clean architectureCheck testDebugUnitTest lintDebug assembleDebug` exits non-zero **solely** because of this one test. `architectureCheck`, `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `lintDebug` (0 errors), `assembleDebug` (APK produced), and every Phase 5 / onboarding / auth / profile / mapping test pass.

---

## Safe to Commit Phase 5

**YES.**

Exact reason: Every Phase 5 code path is verified — completion is gated on a successful `saveProfile` upsert; all answers are retained in a single shared `OnboardingViewModel`; the persisted id comes only from `authRepository.currentUserId()` and no id is accepted from the UI; `hasCompletedOnboarding()` reads the real `onboarding_completed` column (a row with the flag `false` is treated as incomplete); returning-complete vs incomplete routing works via `determineDestination()`; manual-signup and Google display names are handled without fabrication and persisted; Home/Profile read the name through `ProfileRepository`; and no Supabase type crosses into `domain/`, `feature/`, or `ui/` (`architectureCheck` passes). The full new-user flow was manually confirmed end-to-end against the real Supabase project, including `onboarding_completed = true`, the real name replacing "Team 03", and a completed user skipping onboarding on re-login. The Phase 5 migration `20260910000000_add_onboarding_completed.sql` is idempotent, non-destructive (`ADD COLUMN IF NOT EXISTS` + conditional backfill), and matches what was applied to live. The only failing test is pre-existing teammate scan code not caused by Phase 5.

The `Database Migration Consistency: FAIL` finding does **not** block committing the Phase 5 changeset: the divergence (missing `date_of_birth` / `profile_image_url` migrations; `20260909000000_create_profiles.sql` not matching live's creation history; ad-hoc live `ALTER`s without incremental migration files) is inherited technical debt in a file this task must not modify. It should be reconciled in a follow-up (a forward `ALTER TABLE … ADD COLUMN IF NOT EXISTS` migration or a `supabase db pull` baseline) and the team must confirm RLS is enabled on the live `profiles` table. Until that follow-up lands, `supabase db reset` from the repo migrations will not reproduce production.
