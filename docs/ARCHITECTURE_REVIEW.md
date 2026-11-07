# WeGlow Architecture Review

**Scope:** Whole-app architecture pass — DI/composition root, navigation, ViewModel layer, domain/data repository layer, and the Scan feature UI.
**Reviewed:** `app/src/main/java/com/example/weglow/**` (app, core, data, domain, feature, navigation, ui packages) and the corresponding test suite.

The layering (domain interfaces / data implementations / feature ViewModels) is genuinely well-intentioned — most of what follows is about where that discipline breaks down in practice.

---

## Critical

### 1. `WeGlowApp()` is a god composable that owns the entire app's state
`navigation/WeGlowApp.kt:47-138`

Every ViewModel for every feature (auth, onboarding, scan, discover, hairstyle, profile, recommendation, routine, journal — 9 total) is constructed and `collectAsState()`'d at the single `NavHost` root, regardless of which screen is visible. Consequences:

- Every one of those `StateFlow`s recomposes this top-level function, which recomposes the whole `Scaffold`/`NavHost` tree on any unrelated state change (e.g. a routine-journal note edit can trigger recomposition machinery that touches the bottom nav, auth state, etc.).
- No ViewModel is scoped to its screen/nav-graph — they all live for the Activity's full lifetime. That may be intentional for tab-state retention, but it's not a documented decision, and it means scan/hairstyle in-memory results survive far longer than their screens do.
- There's no `SavedStateHandle` anywhere; a process death (very common on Android, e.g. backgrounding during a scan) silently drops all of this state and returns the user to onboarding-selection screens with no memory of where they were.

### 2. Sign-out navigation is only wired on the Profile screen
`navigation/WeGlowApp.kt:699-720`

`AuthEvent.SignedOut` is observed via `LaunchedEffect(authState.event)` **inside the `Destination.Profile.route` composable only**. `onLogout` is also only ever wired from `ProfileScreen`, so this happens to work today — but the moment sign-out is triggered from anywhere else (session expiry, a token-refresh failure detected by `AuthRepository`, a future "sign out from Home" affordance), the event fires into an `AuthUiState` that no composable is listening to, `consumeEvent()` never gets called by anyone, and the app is stuck showing authenticated screens with a dead session.

Auth-event handling (sign-in redirect, sign-out redirect) is exactly the kind of cross-cutting concern that belongs once at the `WeGlowApp` level, not duplicated/scattered per-screen the way it currently is between Login, Signup, and Profile.

---

## High

### 3. Inconsistent error-handling contract across the repository layer
`AuthRepository`, `ProfileRepository`, `CatalogRepository`, `ProfileImageRepository` all return `Result<T>` — good, deliberate, consistent. But `AcneScanRepository.analyze` and `HairstyleRepository.analyze` are plain `suspend fun ... : T` that **throw**, and worse, they throw `IllegalStateException` with hand-written, user-facing copy baked into the data layer:

- `data/repository/SupabaseHairstyleRepository.kt:28,30,33,34,37,46,48`
- `data/repository/LocalHairstyleRepository.kt:71,77,88,93`

`HairstyleViewModel` then does `is IllegalStateException -> error.message ?: ...` to surface it (`feature/hairstyle/HairstyleViewModel.kt:60`). That means:

- Presentation copy lives in the data layer, split across two different implementation classes for the same interface — hard to localize, hard to keep consistent, and it silently couples the ViewModel to "whatever text the repository decided to throw."
- Two different error-handling idioms exist in the same repository layer for no architectural reason. Pick one (standardize on typed sealed errors + `Result`, since that's already the pattern everywhere else) and migrate these two.

### 4. `SupabaseHairstyleRepository` fetches the entire `hairstyles` table and filters client-side
`data/repository/SupabaseHairstyleRepository.kt:39-49`

```kotlin
client.postgrest["hairstyles"]
    .select()
    .decodeList<JsonObject>()
```

This pulls every row, every scan, then filters by face shape/gender in Kotlin. This should be a server-side filter (`select { filter { eq("face_shape", faceShape) } }`). As the catalog grows this is a real cost/latency problem, and it means the `check(rows.isNotEmpty())` guard only tells you the table isn't empty, not that this face shape has any matches — a legitimately-empty match silently renders an empty recommendation list with no message distinguishing "table down" from "no styles yet for this shape."

### 5. `SupabaseHairstyleRepository` conflates two responsibilities and wastes the work of one of them
It constructs a `LocalHairstyleRepository(context)` internally (`SupabaseHairstyleRepository.kt:23`) to run on-device TFLite classification, then throws away most of what that call produced: `LocalHairstyleRepository.analyze()` builds a full `HairstyleResult` including a **hardcoded, gender/face-shape-keyed static recommendation catalog** (`LocalHairstyleRepository.kt:143-211`, with bundled drawables and fixed match scores 97/94/91/88), and `SupabaseHairstyleRepository` immediately discards `classified.recommendations` and replaces it with the Supabase-sourced list (`SupabaseHairstyleRepository.kt:51`). That's:

- Dead computation on every scan (the static catalog lookup runs for nothing).
- A "split-brain" content model: `traits`/`description` per face shape live hardcoded in the APK (`FACE_SHAPE_PROFILES`), while the actual recommended styles live in Supabase. Changing hairstyle copy requires an app release; changing which styles show up requires a DB update. That's an inconsistent content-ownership story that a content/product person will trip over.
- A naming/composition smell: a class named for "Supabase" is also responsible for constructing and owning an on-device ML dependency by reaching into `Context` itself, rather than having that classifier injected. That directly undercuts the stated goal in `AppContainer`'s own doc comment ("repositories do not reach into global infrastructure... keeping domain and presentation layers... testable").

### 6. Business/navigation-timing logic lives in the View layer, not the ViewModel
`ui/screens/ScanScreen.kt` (915 lines)

This composable owns a hand-rolled state machine (`ScanFlowState`, `ScanMode`) plus hardcoded timing constants (5000ms, 750ms, 3100ms, 650ms — `ScanScreen.kt:132,135,147,159`) that gate *when navigation callbacks fire* (`onAcneScanComplete()`, `onHairstyleScanComplete()`). This is core flow-control logic, not presentation, and it's sitting in `LaunchedEffect` blocks inside a `@Composable`. Consequences:

- It's untestable the way the rest of the app is tested — there are thorough ViewModel unit tests for everything else (`AuthViewModelTest`, `HairstyleViewModelTest`, `ScanViewModelTest`, etc.), but there is no test anywhere for this flow-state machine; the only androidTest in the project is the default `ExampleInstrumentedTest` placeholder.
- State restoration is inconsistent: `flowState`/`scanMode` use `rememberSaveable` but `analysisStartedAt`/`displayedValidationError` use plain `remember` (`ScanScreen.kt:102-105`). A process death/recreation while `flowState == ANALYZING` restores "we're analyzing" without the timestamp the timing logic depends on — a latent bug (stuck screen or a timing calculation against a stale/zero `analysisStartedAt`).
- There are two parallel "analyzing" composables per mode (`FigmaAcneAnalyzingScreen` vs `AcneAnalyzingScreen`, `FigmaAnalyzingScreen` vs `AnalyzingScreen`) — looks like a redesign left the old versions in place; worth confirming whether the non-Figma variants are dead code.

---

## Medium

### 7. TFLite `Interpreter` is never closed
`data/repository/LocalHairstyleRepository.kt:31-47`

`Interpreter` implements `Closeable` and holds native memory; there's no `close()` call anywhere in this class or its owners. Bounded by app-process lifetime today since it's effectively a singleton, but it's a real leak if this object is ever recreated (e.g. if `AppContainer.hairstyleRepository(context)` — which, unlike every other repository in that container, is a plain function rather than a `by lazy val`, so nothing guarantees single-instance — is ever called more than once).

### 8. Inconsistent persistence strategy across features
Auth, profile, face shape, and hairstyle recommendations are all Supabase-backed. Routine journal (completion state + notes) is deliberately device-local `SharedPreferences` keyed by raw user id (`feature/routine/RoutineJournalViewModel.kt:27,44-46`), explicitly documented as "no cloud synchronization." That's a fine call to make deliberately, but it means a user who reinstalls or switches devices silently loses routine progress while everything else about their account persists — worth confirming this is actually the desired product behavior and not just how it happened to get built.

### 9. `AppContainer` mixes two different dependency lifecycles with no clear rule
`app/AppContainer.kt`

`authRepository`, `profileRepository`, `profileImageRepository`, `catalogRepository` are `by lazy val` singletons; `acneScanRepository(context)` and `hairstyleRepository(context)` are plain functions that build a new instance on every call. Nothing in the container signals which one you should expect for a given dependency, and it's this inconsistency that creates the interpreter-leak risk in #7.

---

## Lower priority / structural

### 10. No modularization
`settings.gradle.kts` only includes `:app` — everything is one Gradle module. Fine at current size (~9K LOC), but the domain/data/ui boundaries are enforced only by convention (all repository classes are `public`, not `internal`), not by the build graph. Worth a note now so it doesn't become a rewrite later if the team grows.

### 11. Documentation sprawl instead of ADRs
16+ untracked, ad hoc `docs/*.md` "investigation/implementation/verification" reports per bug fix, with no single living architecture doc. Useful as history, but there's no canonical place describing the intended layering, which is presumably why some of the drift above (error-handling inconsistency, DI lifecycle inconsistency) crept in unnoticed.

---

## What's actually good, for balance

- The `domain/repository` interfaces are clean and well-documented with real invariants stated in doc comments (e.g. `ProfileImageRepository` explicitly forbidding UI-supplied paths).
- No screen or feature package imports a concrete `Supabase*` class directly — everything goes through the interfaces.
- ViewModel-level test coverage is genuinely strong.
- The auth state machine in `AuthViewModel` (distinguishing sign-up vs sign-in vs Google to avoid double-firing events) is subtle and correctly reasoned, just not wired to a robust listener (see #2).

---

## Suggested priority order

1. Fix sign-out event handling scope (#2) — correctness bug, small fix, high blast radius if hit.
2. Move hairstyle filtering server-side (#4) — scaling/cost issue that only gets more expensive to fix later.
3. Unify repository error-handling contract (#3) and split `SupabaseHairstyleRepository`'s dual responsibility (#5).
4. Extract Scan's flow-state machine into a ViewModel (#6) — unlocks real test coverage for the riskiest screen in the app.
5. Address DI lifecycle inconsistency (#9) and the interpreter leak (#7) together.
