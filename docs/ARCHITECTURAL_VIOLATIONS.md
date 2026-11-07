# WeGlow Architectural Violations

**Framing:** This pass is specifically about violations of stated architectural principles (Clean Architecture's dependency rule, MVVM's view/viewmodel split, separation of concerns, single responsibility, interface consistency, single source of truth, composition-root discipline) — as distinct from general bugs, performance, or hygiene issues. See `docs/ARCHITECTURE_REVIEW.md` for the broader issue list.

The codebase clearly establishes good architectural patterns early (the domain/data repository split, DTO mapping for profiles). Most of what follows is that discipline eroding on features added after the pattern was set — worth fixing at the review-process level, not just the code level.

---

## 1. Dependency Rule violation: domain model depends on the Android framework

`domain/model/HairstyleRecommendation.kt:8`

```kotlin
data class HairstyleRecommendation(
    val matchPercent: Int,
    val category: String,
    val title: String,
    val description: String,
    val imageRes: Int = 0,      // an android.R drawable resource id
    val imageUrl: String? = null,
)
```

`imageRes` is populated with `R.drawable.hairstyle_male_fringe_up` (`data/repository/LocalHairstyleRepository.kt:200-210`). In Clean Architecture, the domain layer must be pure Kotlin — it's the layer everything else depends on, so it can never depend outward on Android. An `Int` that's secretly a resource ID is a textbook dependency-rule violation: this model can't be unit-tested or reused outside an Android `Resources` context, and any consumer of `HairstyleResult` in the domain/feature layer is implicitly coupled to the app module's resource table.

**What makes this notable:** the team clearly knows the correct pattern — `data/model/ProfileRow.kt` is a proper DTO with explicit `toUserProfile()`/`toProfileRow()` mappers, keeping the wire format out of the domain model entirely. That discipline just wasn't applied to `HairstyleRecommendation`.

---

## 2. MVVM violation: flow-control logic lives in the View, not the ViewModel

`ui/screens/ScanScreen.kt:102-165`

MVVM's core rule is that the View is a passive renderer of ViewModel state; decisions belong in the ViewModel. Here, `ScanScreen` owns its own state machine (`ScanFlowState`, `ScanMode`), and `LaunchedEffect` blocks inside the composable decide *when a scan is "done enough" to navigate* using hardcoded artificial delays (5000ms / 750ms / 3100ms / 650ms). The View is making a business decision ("has this analysis finished, should we move to the next screen") that every other feature in the app correctly delegates to a ViewModel.

This is also why this is the one part of the app with zero test coverage — you can't unit-test a `LaunchedEffect`, and indeed there is no test for this state machine anywhere in the suite, versus thorough ViewModel tests for every other feature.

---

## 3. Separation-of-concerns violation: presentation copy embedded in the data layer

`data/repository/SupabaseHairstyleRepository.kt:28-46`, `data/repository/LocalHairstyleRepository.kt:71-93`

Repositories throw exceptions like:

```kotlin
throw IllegalStateException("Could not save your face shape. Please retry the scan.", error)
```

Literal UI copy, authored inside the data layer, consumed by `HairstyleViewModel` via `error.message` (`feature/hairstyle/HairstyleViewModel.kt:60`). The data layer should never know what the user sees.

**Contrast:** `core/image/FaceImageValidator.kt` defines a `FaceValidationResult` sealed type with a *separate* `errorMessage` extension property that maps result → copy. That's the correct pattern (data layer returns a typed result, presentation layer owns the strings), sitting right next to the violation in the same feature area.

---

## 4. Single Responsibility violation: one repository, two data sources, one identity

`data/repository/SupabaseHairstyleRepository.kt:17-23`

`SupabaseHairstyleRepository` — a class named and typed for a remote data source — constructs an on-device TFLite classifier internally (`LocalHairstyleRepository(context)`) and takes a raw `Context` in its constructor to do it. A repository named after one data source shouldn't be silently orchestrating a second, unrelated one (on-device ML inference) as an implementation detail. That composition belongs at the `AppContainer` level, injected as a dependency — not constructed inside the class it's supposedly separate from.

---

## 5. Single Source of Truth violation

Following from #4: `LocalHairstyleRepository` computes a full static recommendation catalog (hardcoded `FACE_SHAPE_PROFILES`, drawables, fixed match scores), which `SupabaseHairstyleRepository` then **overwrites** with Supabase data (`SupabaseHairstyleRepository.kt:51`) — except for `traits`/`description`, which stay hardcoded. One feature's content is authoritative in two different places (APK-bundled Kotlin vs. a Postgres table) depending on which field you're looking at, with no single owner.

---

## 6. Interface-consistency violation across the repository contracts

`domain/repository/*.kt`

Every repository in this codebase agreed on one contract — `Result<T>` for anything fallible (`AuthRepository`, `ProfileRepository`, `CatalogRepository`, `ProfileImageRepository`) — except `AcneScanRepository` and `HairstyleRepository`, which throw instead. An architectural contract that half the implementers follow isn't really a contract; every caller of these two has to know, out of band, "these two are different."

---

## 7. Cross-cutting concern implemented as a local effect of one leaf screen

`navigation/WeGlowApp.kt:699-720`

Session/auth-state reaction (navigate to Login on sign-out, clear scan/hairstyle state) is global app behavior, but it's implemented inside `Destination.Profile.route`'s composable body as a `LaunchedEffect`, because that's currently the only place `signOut()` is invoked from. Architecturally, "what happens when the user's session ends" should be owned once, at the navigation root that owns the `NavController`, not implicitly by whichever screen happens to expose the button that triggers it today.

---

## 8. Composition-root inconsistency

`app/AppContainer.kt`

The container mixes two dependency-lifecycle rules with no stated policy: `authRepository` / `profileRepository` / `profileImageRepository` / `catalogRepository` are `by lazy val` singletons, while `acneScanRepository(context)` / `hairstyleRepository(context)` are factory functions that construct a new instance every call. A composition root's job is to be the one place lifecycle is decided deliberately — right now it's decided implicitly by which repository happened to need a `Context` parameter.

---

## Root cause

The domain/data interface split (#6, #1) was clearly a deliberate, well-executed design decision early on — and it visibly erodes on every feature added afterward (hairstyle end-to-end, then the Scan UI) that didn't route through the same review discipline. That's the thing worth fixing at the team-process level, not just the code level: bring these two features back in line with the pattern the rest of the app already proves the team knows how to do.
