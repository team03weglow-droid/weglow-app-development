# WeGlow — Architecture Improvements

Prioritized by how much rework each one saves if done before Phase 3.
Companion to `PHASE_1_2_AUDIT.md` and `ARCHITECTURE_REVIEW.md`.

---

## 1. Do before Phase 3 (these get more expensive later)

### 1.1 Make the repository seams async-shaped
`DiscoverViewModel` and `HairstyleViewModel` read data synchronously (in a
constructor / a bare function). Real catalog and recommendations are network/ML
calls. Convert now, matching the Auth/Onboarding pattern:

- `CatalogRepository.products()` -> `suspend fun products(): Result<List<Product>>` (or return `Flow`)
- `HairstyleRepository.recommendationsFor(...)` -> `suspend`
- Both ViewModels expose one `StateFlow<UiState>` with `isLoading` / `error` /
  data, populated inside `viewModelScope`.

**Why:** otherwise these two ViewModels are rewritten, not swapped, the moment
data comes from Supabase.

### 1.2 Get Android resource IDs out of the domain layer
`Product.imageRes: Int` and `HairstyleRecommendation.imageRes: Int` are
`@DrawableRes` values, even though the domain is meant to be backend-agnostic.

- Change to `imageKey: String` (asset key or URL).
- The in-memory implementation maps `imageKey` -> `R.drawable.*`.
- When Supabase supplies image URLs, only the data/ui layer changes.

### 1.3 Add a scan-analysis seam now
`ScanViewModel` only holds a `Uri`. There is no contract for analysis.

- Add `ScanRepository` / `ScanAnalysisRepository` with a fake implementation that
  returns the current canned result.
- The "real AI scan" phase then swaps an implementation instead of inventing the
  seam under deadline pressure.

---

## 2. Structural (decide now, implement incrementally)

### 2.1 Real boundary enforcement
`architectureCheck` is a raw substring scan for 5 tokens. It only blocks
`data.remote` (not concrete `data.repository.*` imports) and has no test proving
it fails on a violation. Everything is one Gradle module, so the boundary is a
convention, not a constraint. Options, best first:

- Split modules: `:core` (pure Kotlin domain) + `:data` + `:app`. The compiler
  enforces the boundary.
- Or add an ArchUnit/Konsist test **plus** a test that plants a violation and
  asserts the check fails.

### 2.2 Proper dependency injection
`AppContainer` is created in a `remember {}` inside a composable,
`SupabaseClientProvider` is a global `object`, and the deprecated
`SupabaseService` alias still exists.

- Move to an `Application`-held container or Hilt.
- Provide `SupabaseClient` through the container so nothing reaches a singleton.
- Enables a test container.

---

## 3. Cheap seams with compounding value

### 3.1 Domain error type
Replace `Result<Unit>` + `it.message ?: "…"` (raw Supabase/Ktor text shown to
users) with a `sealed interface AppError`, mapped in the data layer.

### 3.2 Single source of truth for session routing
`AuthViewModel.resolveStartupDestination()` and `AuthViewModel.signIn()` both
independently query `hasCompletedOnboarding` and derive a destination. Extract a
`SessionRepository` or a use-case.

### 3.3 Uniform ViewModel contract
Every feature ViewModel should expose exactly one `StateFlow<XUiState>`.
`HairstyleViewModel` currently exposes a bare `fun resultFor()` — the odd one
out.

### 3.4 Testability
- Inject a dispatcher provider into ViewModels/repos instead of relying on
  `Dispatchers.setMain` in tests.
- Use `SavedStateHandle` in `OnboardingViewModel` so wizard answers survive
  process death.

---

## 4. Housekeeping

- Delete `data/SupabaseClient.kt` (dead deprecated alias, nothing uses it).
- Remove unused imports in `WeGlowApp.kt` (`JungeFont`, `sp`).
- Add ViewModel tests for the four that currently have none
  (`Onboarding`, `Scan`, `Discover`, `Hairstyle`).

---

**Bottom line:** items 1.1–1.3 are the ones that cause real churn if Phase 3
starts without them. None of these block *starting* Phase 3.
