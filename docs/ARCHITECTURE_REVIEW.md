# WeGlow — Architecture Review & Improvement Plan

Companion to `PHASE_1_2_AUDIT.md`. Focused on the architecture only.

---

## Rating: 6.5 / 10

| Dimension | Score | Note |
|---|---|---|
| Dependency direction & layering | 8/10 | Correct, consistent, actually followed in code |
| Boundary enforcement | 4/10 | Single module; substring-scan script, bypassable, untested |
| Seam design for the future | 5/10 | Auth/onboarding shaped for async; catalog/hairstyle are sync and will need rework; no scan seam |
| Domain purity | 6/10 | Pure Kotlin, but models carry `@DrawableRes` Android IDs |
| DI / composition | 6/10 | Container + constructor injection is right idea; won't scale, global `object` client remains |
| State management | 8/10 | Lifecycle-scoped `StateFlow`, no global holders, clean |
| Navigation | 8/10 | Centralized `Destination`, session-aware |

**Why 6.5:** fundamentals are right and the code honestly respects the
boundaries — better than most Phase 1 prototypes. It loses points because the
"enforcement" is a fragile script over one module (architecture is a convention,
not a constraint), and two of the five repository seams aren't shaped for the
async/remote reality Phase 3 introduces, so they'll be rewritten rather than
swapped. Fixing the async shape, moving image refs off `R.drawable` in domain,
and adding real boundary enforcement gets this to an 8.

---

## What's genuinely solid

- Dependency direction is correct and consistently followed:
  `screen -> feature VM -> domain contract -> data impl -> infra`. Grep confirms
  zero violations in the actual code.
- Repository seams exist with swappable in-memory implementations
  (`InMemoryCatalogRepository`, `InMemoryHairstyleRepository`).
- Composition root (`AppContainer`) + constructor injection; Supabase repos take
  `SupabaseClient` as a parameter.
- Lifecycle-scoped `StateFlow` state, no process-global holders.
- Centralized `Destination` routing; session-aware startup.

This is a real foundation, not just folders.

---

## Improvement plan (prioritized by rework saved if done before Phase 3)

### Do before Phase 3 — these get more expensive later

**1. Make the repository seams async-shaped.**
`DiscoverViewModel` and `HairstyleViewModel` read data synchronously (in a
constructor / a bare function). Real catalog + recommendations are network/ML
calls. Convert now, matching the Auth/Onboarding pattern:
- `CatalogRepository.products()` -> `suspend fun products(): Result<List<Product>>` (or `Flow`)
- `HairstyleRepository.recommendationsFor(...)` -> `suspend`
- Both VMs expose one `UiState` with `isLoading` / `error` / data, populated in
  `viewModelScope`.

**2. Get Android resource IDs out of the domain layer.**
`Product.imageRes: Int` and `HairstyleRecommendation.imageRes: Int` are
`@DrawableRes` values. Change to `imageKey: String` (asset key or URL). The
in-memory impl maps key -> `R.drawable`; when Supabase supplies URLs, only the
data/ui layer changes, not the domain model and every consumer.

**3. Add a `ScanRepository` / `ScanAnalysisRepository` contract now**, with a fake
implementation that returns the current canned result. Then the "real AI scan"
phase swaps an implementation instead of inventing the seam under deadline
pressure. `ScanViewModel` currently only holds a `Uri` — there is no analysis
seam at all.

### Structural — decide now, implement incrementally

**4. Real boundary enforcement.** The `architectureCheck` substring scan is
bypassable (it only blocks `data.remote`, not concrete `data.repository.*`
imports) and has no test proving it fails on a violation. Options, best first:
- Split modules: `:core` (pure Kotlin domain) + `:data` + `:app`. Compiler
  enforces it.
- Or add an ArchUnit/Konsist test **plus** a test that plants a violation and
  asserts the check fails.

**5. Proper DI.** `AppContainer` in a `remember {}`, `SupabaseClientProvider` as a
global `object`, and the deprecated `SupabaseService` alias all need to go. Move
to an `Application`-held container or Hilt; provide `SupabaseClient` through it
so nothing reaches a singleton.

### Cheap seams with compounding value

**6. Domain error type.** Replace `Result<Unit>` + `it.message ?: "…"` (raw
Supabase/Ktor text shown to users) with a `sealed interface AppError`, mapped in
the data layer.

**7. Single source of truth for "where should the user be."**
`AuthViewModel.resolveStartupDestination()` and `signIn()` both independently
query `hasCompletedOnboarding` and derive a destination. Extract a
`SessionRepository` or use-case.

**8. Uniform ViewModel contract.** Every feature VM exposes exactly one
`StateFlow<XUiState>`. `HairstyleViewModel` currently exposes a bare
`fun resultFor()` — the odd one out.

**9. Inject a dispatcher provider** into ViewModels/repos instead of relying on
`Dispatchers.setMain` in tests; use `SavedStateHandle` in `OnboardingViewModel`
so wizard answers survive process death.

### Housekeeping

**10.** Delete `data/SupabaseClient.kt` (dead alias) and the unused imports in
`WeGlowApp.kt`.

**11.** Add ViewModel tests for the four that currently have none.

---

Items 1–3 are the ones that will actually cause churn if Phase 3 starts without
them. None of these block *starting* Phase 3.
