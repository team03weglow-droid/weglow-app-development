# WeGlow Phase 5 — Onboarding Implementation Audit (branch `phase-5-onboarding`)

## 0. Branch state — read this first

`git diff --name-status main...HEAD` is **empty**. `phase-5-onboarding` currently points at the same tree as `main` (tip commit `7629663`, a merge of `origin/main`). No Phase 5 work has started on this branch.

**Most of the required Phase 5 behavior is already implemented on `main`** (commits `6ecb70c` "connecting the questions to the database" and `120ed4d` "preserving database onboarding work", carried through the Phase 4 merge). The onboarding questions already persist to Supabase, already associate with the Auth user ID, and returning users already skip onboarding. What is missing is narrower than a full Phase 5 build. Details below.

This is a Kotlin / Jetpack Compose **Android** app (package `com.example.weglow`), Supabase-Kotlin `3.0.0` (`postgrest-kt`, `auth-kt`), single-module `app/`.

---

## 1. Exact onboarding navigation order + every question/answer

Startup routing (`WeGlowApp.kt:128-173`): `LoadingScreen` → reads `authViewModel.startupDestination` → `LOGIN` / `AgeSelection` (`ONBOARDING`) / `Home`.

Entry into onboarding happens from three places:

- **Manual signup** (`WeGlowApp.kt:241-265`): on `AuthEvent.SignedUp(fullName)` → `onboardingViewModel.start(event.fullName)` → navigate `age_selection`, popping `login` inclusive.
- **Sign-in of a user with no profile row** (`WeGlowApp.kt:200-230`): on `AuthEvent.SignedIn`, if `destination != HOME` → navigate `age_selection`. **`start()` is NOT called here.**
- **Startup with restored session, onboarding incomplete** (`WeGlowApp.kt:152-162`): `StartupDestination.ONBOARDING` → `age_selection`. **`start()` is NOT called here.**

Screen order (routes from `Destination.kt`):

| # | Route / file | Question | Answers (exact literals) | VM call | Field |
|---|---|---|---|---|---|
| 1 | `age_selection` — `AgeSelectionScreen.kt` | "How old are you?" | `"Under 14"`, `"14 - 25"`, `"25 - 35"`, `"36 - 45"`, `"45 - 50"` — **default preselected `"14 - 25"`**, CONTINUE always returns a value | `setAge(String)` | `ageRange` |
| 2 | `skin_type` — `SkinTypeScreen.kt` | "What's a skin type?" | `"Dry & Tight"`, `"Combination"`, `"Oily"`, `"Sensitive"` (the **label string** is persisted). No default; "Next Step" disabled until selected. **"Skip for now"** → `setSkinType(null)` | `setSkinType(String?)` | `skinType` |
| 3 | `gender_selection` — `GenderSelectionScreen.kt` | "What's your gender?" | `"Female"`, `"Male"`, `"Other/Prefer not to say"`. No default; "Continue" disabled until selected. Has a **Back** button (`popBackStack`) | `setGender(String)` | `gender` |
| 4 | `skin_sensitivity` — `SkinSensitivityScreen.kt` | "Is your skin sensitive?" | **Yes** → `setSensitivityAndSave(true)`; **No** → `setSensitivityAndSave(false)`. This is the final question and **triggers the save**. Buttons disable while `isSaving` | `setSensitivityAndSave(Boolean)` | `isSkinSensitive` + `save()` |
| — | `welcome_intro` — `WelcomeIntroScreen.kt` | none | "Start your Glow" → navigate `home`, `popUpTo(age_selection, inclusive=true)` (clears whole wizard) | — | — |

After step 4 succeeds: `OnboardingUiState.saveCompleted = true` → `SkinSensitivity` composable's `LaunchedEffect` (`WeGlowApp.kt:345-357`) calls `consumeSaveCompleted()` and navigates to `welcome_intro` → then `home`.

`fullName` is **not** an onboarding question. It is collected only on `SignUpScreen.kt` ("Full name" field, line 45), trimmed, and passed `onCreateAccount(fullName, email, password)`.

Cosmetic bugs in the existing screens (not persistence, listed because you asked for "exact"): step labels are inconsistent — screen 1 says "STEP 1 OF 4", screen 2 "STEP 2 OF 4", screen 3 "STEP 3 OF 5", and **`SkinSensitivityScreen.kt:30-31` is a copy‑paste of Gender's header** ("STEP 3 OF 5 / GENDER", progress bar `fillMaxWidth(0.6f)`). Only `GenderSelectionScreen` has a Back button.

---

## 2. All onboarding screen files

```
app/src/main/java/com/example/weglow/ui/screens/AgeSelectionScreen.kt      (onContinue: (String) -> Unit)
app/src/main/java/com/example/weglow/ui/screens/SkinTypeScreen.kt          (onNext: (String) -> Unit, onSkip: () -> Unit)
app/src/main/java/com/example/weglow/ui/screens/GenderSelectionScreen.kt   (onBack: () -> Unit, onContinue: (String) -> Unit)
app/src/main/java/com/example/weglow/ui/screens/SkinSensitivityScreen.kt   (onAnswer: (Boolean) -> Unit, isSaving: Boolean, errorMessage: String?)
app/src/main/java/com/example/weglow/ui/screens/WelcomeIntroScreen.kt      (onStartGlow: () -> Unit)
```

All are stateless-ish (`remember { mutableStateOf(...) }` local selection only). None touch the ViewModel directly — callbacks are wired in `WeGlowApp.kt`. Related: `SignUpScreen.kt` (name capture), `LoginScreen.kt`, `LoadingScreen.kt`.

---

## 3. Complete current `OnboardingViewModel`

`app/src/main/java/com/example/weglow/feature/onboarding/OnboardingViewModel.kt` (verbatim):

```kotlin
data class OnboardingUiState(
    val fullName: String = "",
    val ageRange: String? = null,
    val skinType: String? = null,
    val gender: String? = null,
    val isSkinSensitive: Boolean? = null,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val errorMessage: String? = null,
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun start(fullName: String) { _uiState.value = OnboardingUiState(fullName = fullName) }
    fun setAge(value: String) { _uiState.value = _uiState.value.copy(ageRange = value) }
    fun setSkinType(value: String?) { _uiState.value = _uiState.value.copy(skinType = value) }
    fun setGender(value: String) { _uiState.value = _uiState.value.copy(gender = value) }

    fun setSensitivityAndSave(value: Boolean) {
        _uiState.value = _uiState.value.copy(isSkinSensitive = value)
        save()
    }

    private fun save() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Your session is no longer active.")
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            profileRepository.saveProfile(
                UserProfile(
                    id = userId,
                    fullName = state.fullName.ifBlank { null },
                    ageRange = state.ageRange,
                    skinType = state.skinType,
                    gender = state.gender,
                    isSkinSensitive = state.isSkinSensitive,
                )
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, saveCompleted = true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = it.message ?: "Unable to save profile.")
            }
        }
    }

    fun consumeSaveCompleted() { _uiState.value = _uiState.value.copy(saveCompleted = false) }
}
```

Notes: all answers are held in one `OnboardingUiState`. The user ID is fetched at save time from `AuthRepository.currentUserId()` (Supabase `currentUserOrNull()?.id`). `saveCompleted` is set **only inside `onSuccess`**, so navigation past onboarding is already gated on persistence success. There is no explicit "completed" flag written — see §9/§11.

---

## 4. Complete current `UserProfile` model

`app/src/main/java/com/example/weglow/domain/model/UserProfile.kt` (verbatim):

```kotlin
/** Domain representation of profile information. It is intentionally backend-agnostic. */
data class UserProfile(
    val id: String,
    val fullName: String? = null,
    val ageRange: String? = null,
    val skinType: String? = null,
    val gender: String? = null,
    val isSkinSensitive: Boolean? = null,
)
```

No `onboardingCompleted`, no `email`, no timestamps, no avatar.

---

## 5. `ProfileRepository` (domain contract)

`app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt` (verbatim):

```kotlin
interface ProfileRepository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun hasCompletedOnboarding(userId: String): Result<Boolean>
}
```

**There is no read method.** Nothing can load a persisted profile back into the UI.

---

## 6. `SupabaseProfileRepository`

`app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt` (verbatim):

```kotlin
class SupabaseProfileRepository(
    private val client: SupabaseClient,
) : ProfileRepository {
    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        client.postgrest["profiles"].upsert(profile.toProfileRow())
        Unit
    }

    override suspend fun hasCompletedOnboarding(userId: String): Result<Boolean> = runCatching {
        client.postgrest["profiles"]
            .select { filter { eq("id", userId) } }
            .decodeList<ProfileRow>()
            .isNotEmpty()
    }
}
```

Table: `"profiles"`. `upsert` conflict target = primary key (`id`). `hasCompletedOnboarding` = **"a row exists for this id"** — not a real completion flag.

Row DTO — `app/src/main/java/com/example/weglow/data/model/ProfileRow.kt` (verbatim):

```kotlin
@Serializable
data class ProfileRow(
    val id: String,
    val full_name: String? = null,
    val age_range: String? = null,
    val skin_type: String? = null,
    val gender: String? = null,
    val is_skin_sensitive: Boolean? = null,
)

fun UserProfile.toProfileRow() = ProfileRow(
    id = id,
    full_name = fullName,
    age_range = ageRange,
    skin_type = skinType,
    gender = gender,
    is_skin_sensitive = isSkinSensitive,
)
```

There is **no `toUserProfile()` reverse mapper.**

---

## 7. `AppContainer` / ViewModel creation

`app/src/main/java/com/example/weglow/app/AppContainer.kt`:

```kotlin
class AppContainer {
    fun acneScanRepository(context: Context): AcneScanRepository = LocalAcneScanRepository(context)
    private val supabaseClient by lazy { SupabaseClientProvider.client }

    val authRepository: AuthRepository by lazy { SupabaseAuthRepository(supabaseClient) }
    val profileRepository: ProfileRepository by lazy { SupabaseProfileRepository(supabaseClient) }
    val catalogRepository: CatalogRepository by lazy { InMemoryCatalogRepository() }
    val hairstyleRepository: HairstyleRepository by lazy { InMemoryHairstyleRepository() }
}
```

No DI framework. `ViewModelFactory.kt` is a one-liner `inline fun viewModelFactory(crossinline create)`.

In `WeGlowApp.kt` (`:44-83`): `val container = remember { AppContainer() }`; every ViewModel is created once at `WeGlowApp` composable scope and shared by closure into each `composable {}` block. `OnboardingViewModel` is therefore a **single instance for the whole nav graph** — answers survive screen-to-screen navigation. It is constructed as:

```kotlin
val onboardingViewModel: OnboardingViewModel = viewModel(
    factory = viewModelFactory {
        OnboardingViewModel(container.authRepository, container.profileRepository)
    }
)
```

`SupabaseClientProvider` (`data/remote/supabase/`) is the single lazy client: `install(Postgrest)`, `install(Auth) { scheme = "weglow"; host = "login-callback" }`. Config from `BuildConfig.SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY` via `AppConfig`, sourced from `local.properties` / Gradle props / env (`WEGLOW_SUPABASE_URL`, `WEGLOW_SUPABASE_PUBLISHABLE_KEY`).

---

## 8. Relevant `WeGlowApp` onboarding navigation

- `:157-158` startup: `StartupDestination.ONBOARDING -> Destination.AgeSelection.route`.
- `:200-230` post-login: `AuthEvent.SignedIn` → `if (event.destination == HOME) Home else AgeSelection`, pops `login` inclusive. **No `onboardingViewModel.start(...)`.**
- `:241-265` post-signup: `AuthEvent.SignedUp` → `onboardingViewModel.start(event.fullName)` → `AgeSelection`, pops `login` inclusive.
- `:272-286` `age_selection` → `setAge` → `skin_type`.
- `:288-314` `skin_type` → `setSkinType(it)` / `onSkip → setSkinType(null)` → `gender_selection`.
- `:316-335` `gender_selection` → `setGender` → `skin_sensitivity`; `onBack → popBackStack`.
- `:337-358` `skin_sensitivity` → `setSensitivityAndSave`; passes `onboardingState.isSaving` / `errorMessage` to the screen; `LaunchedEffect(saveCompleted)` → `consumeSaveCompleted()` + navigate `welcome_intro`.
- `:360-388` `welcome_intro` → `onStartGlow` → `home`, `popUpTo(age_selection, inclusive=true)`.

`AuthViewModel.determineDestination()` (`AuthViewModel.kt:355-378`) is what decides ONBOARDING vs HOME:

```kotlin
val completed = profileRepository.hasCompletedOnboarding(userId).getOrDefault(false)
return if (completed) StartupDestination.HOME else StartupDestination.ONBOARDING
```

Called both at startup (sets `_startupDestination`) and after a fresh `AUTHENTICATED` emission (sets `AuthEvent.SignedIn(destination)`). SIGN_UP is explicitly suppressed from the `AUTHENTICATED` observer so it can emit `SignedUp(fullName)` instead (`AuthViewModel.kt:97-108`).

---

## 9. Current Supabase table names / column mappings referenced by code

Only one table is touched: **`public.profiles`**, via `client.postgrest["profiles"]` (upsert + select) in `SupabaseProfileRepository`.

Migration `supabase/migrations/20260909000000_create_profiles.sql`:

```
id                uuid  PK  references auth.users(id) on delete cascade
full_name         text  null
age_range         text  null
skin_type         text  null
gender            text  null
is_skin_sensitive boolean null
created_at        timestamptz not null default now()
updated_at        timestamptz not null default now()   -- bumped by trigger BEFORE UPDATE only
```

RLS enabled. Policies for `authenticated`: SELECT / INSERT / UPDATE each restricted to `auth.uid() = id`. No DELETE policy. The `profiles_set_updated_at` trigger fires `before update` only (not on insert).

Code ↔ column mapping (via `ProfileRow` `@Serializable`, field name = column name): `id`, `full_name`, `age_range`, `skin_type`, `gender`, `is_skin_sensitive`. `created_at` / `updated_at` are DB-managed and absent from `ProfileRow` (fine — supabase-kt's Json defaults to `ignoreUnknownKeys = true`, so `decodeList` tolerates the extra columns).

Auth: `SupabaseAuthRepository` uses `client.auth` — `signUpWith(Email) { email; password }`, `signInWith(Email)`, `signInWith(Google)`, `signOut()`, `currentUserOrNull()?.id`. **Manual signup does not write `full_name` (or any metadata) to `auth.users`** — the name is passed only in-app to `OnboardingViewModel.start()`.

---

## 10. Data currently lost / not persisted

1. **The real full name on two of three onboarding entry paths.** `onboardingViewModel.start(fullName)` is called **only** in the `Destination.Signup` → `AuthEvent.SignedUp` handler. For (a) a returning user who signs in but has no profile row (`SignedIn` → `AgeSelection`) and (b) a brand-new Google user (also `SignedIn` → `AgeSelection`), `start()` is never called, so `OnboardingUiState.fullName` stays `""` and `save()` writes `full_name = null` (`fullName.ifBlank { null }`). Because manual signup also never stores the name in `auth.users`, `profiles.full_name` is the *only* copy — if the save runs blank, the name is gone permanently.
2. **Google users' name is never read** from `auth.users` metadata (`userMetadata["full_name"]` / `name`) — not referenced anywhere.
3. **Nothing reads persisted answers back.** `ProfileScreen.kt:93` hardcodes `"Team 03"`; `HomeScreen.kt:103` hardcodes `"Hello, Team 03"`. There is no `getProfile` on the repository and no profile-read ViewModel, so `age_range` / `skin_type` / `gender` / `is_skin_sensitive` / `full_name` are write-only from the app's perspective.
4. **No explicit onboarding-completed marker** is persisted (no column, no flag written). Completion is inferred purely from row existence.
5. Not lost, but note: `skin_type = null` when the user taps "Skip for now" is intentional; `is_skin_sensitive` is only ever set as part of the final save.

---

## 11. Blockers that would make persistence incorrect

**B1 — `hasCompletedOnboarding` == "row exists", not "onboarding finished".** Today this is *coincidentally* correct because the row is only ever created by one full successful `saveProfile` upsert. It breaks the moment anything writes a `profiles` row earlier or partially (e.g. a future "save name at signup" step, a DB trigger that seeds a row on `auth.users` insert, an admin import). Then `determineDestination()` returns `HOME` for a user who never answered a question. The spec's "mark onboarding completed only after persistence succeeds" is not enforced by a real flag. **Decision required:** keep row-existence semantics (zero schema change), or add `onboarding_completed boolean not null default false` (+ optional `onboarding_completed_at timestamptz`), write it `true` in the onboarding upsert, and change the query to `eq("onboarding_completed", true)`. The latter is the only way to make completion correct and idempotent.

**B2 — `getOrDefault(false)` on a failed completion query re-runs onboarding.** In `determineDestination()`, if the `profiles` select fails (transient network, RLS/JWT hiccup), a fully-onboarded returning user is routed to `ONBOARDING` and re-asked every question. The re-answered data upserts over the good row (no data corruption, thanks to upsert), but it is a wrong UX and, combined with B1's row-existence check, can also mask a genuinely incomplete state. Consider surfacing the error / retrying instead of defaulting to "not completed".

**B3 — `OnboardingViewModel.start()` not invoked on the sign-in and Google onboarding paths** (`WeGlowApp.kt:200-230`, startup `:157`). Consequences: (a) real name persisted as `null` (§10.1); (b) the single app-scoped `OnboardingViewModel` instance is never reset, so stale answers from a previous onboarding attempt in the same process can carry into a new run. Fix is a one-liner per path (call `start(name)` when entering `age_selection`), but "name" needs a source for the non-manual-signup paths (auth metadata, or a fresh "" for a genuine returning user).

**B4 — `OnboardingViewModelTest.kt` does not compile against the current `AuthRepository`.** The interface now declares `val authenticationState: Flow<AuthenticationState>` and `suspend fun signInWithGoogle(): Result<Unit>` (added in Phase 4), but the test's `SignedInAuthRepository` fake (lines 57‑63) implements neither. `./gradlew testDebugUnitTest` is red at compile time right now. Any Phase 5 change to `OnboardingViewModel` also has to repair this fake (and `RecordingProfileRepository` if the `ProfileRepository` contract changes).

**B5 — architecture boundary check will fail the build if Supabase leaks upward.** `app/build.gradle.kts` registers `architectureCheck` as a `preBuild` dependency; it throws if any file under `feature/` or `ui/` contains `io.github.jan.supabase` (and `domain/` additionally forbids `androidx.*`). So the new profile-read code **must** live in `data/` (`SupabaseProfileRepository`) and reach the UI only through the `ProfileRepository` interface + a ViewModel. Putting a Supabase call in a `ProfileViewModel` will break `preBuild`.

**B6 — no `SavedStateHandle` / process-death handling.** `OnboardingViewModel` state is pure in-memory. If Android kills the process mid-wizard, all answers collected so far are lost and (because `start()` isn't re-run on restore) the user resumes mid-flow with empty state. Pre-existing; flag if Phase 5 scope cares.

---

## 12. Exact files Phase 5 needs to change

**To make onboarding-completion correct + replace hardcoded "Team 03" (the actual remaining work):**

| File | Change |
|---|---|
| `domain/repository/ProfileRepository.kt` | Add `suspend fun getProfile(userId: String): Result<UserProfile?>`. If doing the explicit flag: no signature change to `hasCompletedOnboarding` needed, only its impl. |
| `data/repository/SupabaseProfileRepository.kt` | Implement `getProfile` (`select { filter { eq("id", userId) } }.decodeSingleOrNull<ProfileRow>()?.toUserProfile()`). Explicit-flag route: `saveProfile` upsert includes `onboarding_completed = true`; `hasCompletedOnboarding` filters `eq("onboarding_completed", true)`. |
| `data/model/ProfileRow.kt` | Add `fun ProfileRow.toUserProfile()` reverse mapper. Explicit-flag route: add `val onboarding_completed: Boolean = false` and map it. |
| `domain/model/UserProfile.kt` | Explicit-flag route only: add `val onboardingCompleted: Boolean = false`. |
| `feature/onboarding/OnboardingViewModel.kt` | Explicit-flag route only: pass `onboardingCompleted = true` into the `UserProfile` it saves. (Happy-path persistence logic otherwise already meets the spec.) |
| `navigation/WeGlowApp.kt` | Call `onboardingViewModel.start(name)` on the sign-in (`:200-230`) and startup-`ONBOARDING` (`:152-162`) paths, not just after `SignedUp`. Pass a display name into `HomeScreen` and `ProfileScreen`. |
| `ui/screens/ProfileScreen.kt` | Add a `displayName: String` (or `UserProfile`) param; replace the literal `"Team 03"` at line 93. No layout change. |
| `ui/screens/HomeScreen.kt` | Add a `displayName: String` param; replace `"Hello, Team 03"` at line 103. No layout change. |
| **New** `feature/profile/ProfileViewModel.kt` | Loads `profileRepository.getProfile(authRepository.currentUserId())`, exposes name/answers as `StateFlow`. (No Supabase import — stays behind the interface, satisfies B5.) |
| `app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt` | Fix `SignedInAuthRepository` to implement `authenticationState` + `signInWithGoogle` (B4). Update `RecordingProfileRepository` / assertions if the `ProfileRepository` contract or saved `UserProfile` changes. |
| **New** `supabase/migrations/<timestamp>_add_onboarding_completed.sql` | Explicit-flag route only: `alter table public.profiles add column onboarding_completed boolean not null default false;` (+ optional `onboarding_completed_at timestamptz`). |

**No change needed:** `AppContainer.kt` (already exposes `profileRepository` for a new `ProfileViewModel`), `Destination.kt`, `AuthViewModel.kt` (unless you choose to move name-loading there), `SupabaseAuthRepository.kt`, `SupabaseClientProvider.kt`, the four onboarding question screens, `SignUpScreen.kt`.

**If you keep row-existence semantics for completion** (no schema/model change): the changed-file list collapses to `ProfileRepository.kt` + `SupabaseProfileRepository.kt` + `ProfileRow.kt` (add `getProfile` / `toUserProfile`), `WeGlowApp.kt` + `HomeScreen.kt` + `ProfileScreen.kt` (name wiring), new `ProfileViewModel.kt`, and the `OnboardingViewModelTest.kt` compile fix — and Phase 5 is essentially a name-readback + test-repair task, because the question persistence, user-ID association, success-gated navigation, and returning-user skip are already working on this branch.

---

## 13. Source references (for the implementer)

| Concern | File | Key lines |
|---|---|---|
| Nav graph, all onboarding wiring | `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` | 128-173, 200-266, 272-388 |
| Routes | `app/src/main/java/com/example/weglow/navigation/Destination.kt` | 7-11 |
| Onboarding state + save | `app/src/main/java/com/example/weglow/feature/onboarding/OnboardingViewModel.kt` | 13-68 |
| Startup / returning-user routing | `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt` | 77-142, 355-378 |
| Domain profile model | `app/src/main/java/com/example/weglow/domain/model/UserProfile.kt` | 4-11 |
| Repo contract | `app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt` | 6-9 |
| Supabase persistence | `app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt` | 13-24 |
| Row DTO + mapper | `app/src/main/java/com/example/weglow/data/model/ProfileRow.kt` | 6-23 |
| DI container | `app/src/main/java/com/example/weglow/app/AppContainer.kt` | 23-32 |
| Supabase client | `app/src/main/java/com/example/weglow/data/remote/supabase/SupabaseClientProvider.kt` | 15-32 |
| Auth repo | `app/src/main/java/com/example/weglow/data/repository/SupabaseAuthRepository.kt` | 34-71 |
| Hardcoded name (Home) | `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt` | 103 |
| Hardcoded name (Profile) | `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt` | 93 |
| Name capture | `app/src/main/java/com/example/weglow/ui/screens/SignUpScreen.kt` | 27, 45, 67 |
| DB schema + RLS | `supabase/migrations/20260909000000_create_profiles.sql` | 1-51 |
| Architecture boundary gate | `app/build.gradle.kts` | `ArchitectureCheckTask` / `architectureCheck` / `preBuild` |
| Broken test | `app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt` | 57-63 |
