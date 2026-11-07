# WeGlow Premium Home Redesign — Investigation Report

**Status:** Investigation only. No files modified, no git operations performed as part of this investigation.
**Scope:** Visual-only redesign of the existing Home screen using a premium Home screenshot as visual inspiration. Not a new architecture, not permission to replace business logic, not permission to hardcode fake data.

---

## 1. Current Home Architecture

- `HomeScreen.kt` (`app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`, 438 lines) is a **stateless composable** — no `HomeViewModel`/`HomeState` exists anywhere in the codebase (confirmed by full-tree search).
- Signature: `HomeScreen(onScanClick, onDiscoverClick, onRecommendationsClick, displayName: String? = null, profileImage: ByteArray? = null, morningRoutine: List<RoutineStep> = emptyList())`.
- It is wired inside the single shared `NavHost` in `navigation/WeGlowApp.kt`, which instantiates one `AppContainer` and every feature ViewModel (Auth, Profile, Routine, Recommendation, Scan, Hairstyle, Discover) at app scope via `app/ViewModelFactory.kt`'s tiny `viewModelFactory {}` helper. `WeGlowApp.kt` collects each ViewModel's `uiState` and hands the relevant plain values into `HomeScreen(...)` as parameters — Home itself never touches `AppContainer` or any repository.
- Home's own local state is Compose `remember` UI-only state: `skinScore/hydration/clarity/texture` (all hardcoded `null`, with an explicit in-code comment that no real scoring pipeline exists), and `doneSteps` (routine checkmarks, ephemeral, reset whenever `morningRoutine` changes).
- `AppContainer.kt` builds real Supabase-backed repositories (auth, profile, profile image, catalog) plus context-scoped local repos (acne scan ONNX, hairstyle, face validator). `ViewModelFactory.kt` is just a generic `ViewModelProvider.Factory` wrapper, not a DI framework.

## 2. Current Real Home Data Sources

| Element | Source | Real? |
|---|---|---|
| Greeting name | `ProfileViewModel` → Supabase `profiles.full_name` (falls back to OAuth identity name) | Real, persists across restart |
| Avatar image | `ProfileViewModel` → `SupabaseProfileImageRepository.downloadProfileImage()` (authenticated Storage download, raw bytes, not a signed URL) | Real, persists across restart |
| Morning routine steps/products | `RoutineViewModel.uiState.plan.morning` → `RoutineBuilder` matching real Supabase catalog products against the user's profile | Real, but the plan itself doesn't persist "done" checkmarks |
| Routine "done" checkmarks/progress | Local Compose `remember` state (`doneSteps`) | Real steps, but completion state is ephemeral — lost on restart, not shared with the dedicated Routines screen either |
| Skin score, hydration, clarity, texture | Hardcoded `null` literals with an explicit "do not fabricate" comment | No real data source anywhere in the codebase |
| "AI Insights" cards (Hydration Low / SPF Reminder / Sleep & Skin) | Fully static hardcoded list | Fake — presented as if real, not connected to any measurement |
| "For You" article card | Fully static hardcoded content/image | Fake placeholder content |
| "See All" links (Morning Routine, For You) | Plain `Text`, no `onClick` | Dead UI, not wired to navigation |

## 3. Current Navigation/Actions

Home exposes exactly three callback parameters, all wired in `WeGlowApp.kt`:

- `onScanClick` → `Destination.Scan` ("scan")
- `onDiscoverClick` → `Destination.Discover` ("discover") — note: the "Find hairstyles" card is wired to Discover, not a dedicated hairstyle route
- `onRecommendationsClick` → `Destination.Recommendations.routeFor(fromScan = false)`

Bottom nav (`WeGlowBottomNavigation`, 5 fixed tabs: Home/Discover/Scan/Routines/Profile) provides the rest of the app's reachability from Home, independent of HomeScreen's own params. Home is the `popUpTo` anchor for tab navigation. These three routes plus the bottom nav **must be preserved unchanged**.

## 4. Routine Integration

- `RoutineViewModel.uiState.plan: RoutinePlan?` (`morning`/`evening` lists of `RoutineStep{label, product?, reasons}`) is real, built from real profile + real Supabase catalog via `RoutineBuilder` (keyword-matching catalog categories into Cleanser/Moisturizer/Sunscreen/Treatment slots). `load()` is idempotent.
- `RoutinesScreen.kt` (the dedicated screen) has real, non-hardcoded calendar navigation (`CalendarDay.kt`, based on `LocalDate` math and locale, per recent commits) and a morning/evening toggle defaulted from device clock — neither of these is exposed by the ViewModel, both are screen-local.
- **No persistence exists anywhere** for routine completion state — confirmed by explicit in-code comments (`RoutineCompletionKey.kt`) and a full-tree grep finding zero Room/DataStore/SharedPreferences usage in the whole app. Both `RoutinesScreen` and `HomeScreen` independently hold ephemeral, non-synced "done" state.
- **Reusable as-is for a premium card**: `plan.morning`, the existing `doneCount/totalCount` + `LinearProgressIndicator` pattern already in `HomeScreen.kt`, and per-step product name/label.
- **Would require new (but small, honest) logic** if wanted: persisted/shared completion state, a ViewModel-exposed "which period is active now" helper, or "next incomplete step." None of this is fake data — it's legitimate small refactors, not fabrication — but it goes beyond a pure visual reskin, so it should be treated as optional/out-of-scope unless explicitly requested.

## 5. Scan/ML Data Actually Available

- `AcneScanResult{detections: List<AcneDetection>, imageWidth, imageHeight, modelVersion, confidenceThreshold}` — real, on-device ONNX output. Each `AcneDetection` has `label, confidence, left/top/right/bottom`. **No aggregate score field exists.**
- `HairstyleResult{faceShape, confidencePercent, traits, description, recommendations}` — real, from the face-shape LiteRT pipeline.
- **No scan history persistence** anywhere (confirmed by explicit code comments and grep) — no "compare to last scan" is derivable.
- **NO REAL DATA SOURCE** exists for: skin wellness score, hydration %, clarity %, texture % — confirmed by a full-tree grep with zero real matches; the only hits are the intentional `null` placeholders in `HomeScreen.kt` and a matching "--" placeholder already used on `ProfileScreen.kt`'s own Skin Score card. Both carry in-code comments explicitly warning against fabricating these values.

## 6. Profile Data Available

`ProfileViewModel.uiState`: `displayName` (persisted full name, fallback to OAuth name), `profileImage: ByteArray?` (real, authenticated Supabase Storage download — not a signed URL), `profileImagePath`, `isLoading`, `imageError`. `UserProfile` domain model also carries `skinType`, `ageRange`, `gender`, `isSkinSensitive`, `onboardingCompleted` — not currently surfaced by `ProfileViewModel` but available at the repository layer if a small connection is added. All persists across restart via Supabase Postgres.

## 7. Recommendation/Catalog Data Available

- `RecommendationViewModel.uiState.result.recommendations: List<ProductRecommendation>` — real, rule-based (not ML) matching of real Supabase `products` table rows against profile skin type + optional scan concerns. Already instantiated in `WeGlowApp.kt` with the same shared `AppContainer`.
- `RecommendationCard` (`RecommendationsScreen.kt:127`) is a self-contained, reusable composable that only needs a `ProductRecommendation` — can be dropped into a `LazyRow` on Home for "Curated For You" with **zero new business logic**: just add `recommendationViewModel.load()` to Home's existing `LaunchedEffect`, and pass `recommendationState.result?.recommendations?.take(n)` into `HomeScreen`.

## 8. Environmental Data Availability

**NO REAL DATA SOURCE.** Confirmed by exhaustive grep across all Kotlin sources, `AndroidManifest.xml`, and all Gradle files: no location permissions declared, no Play Services Location/weather/AQI dependency, no repository/domain model for location, UV, humidity, or air quality anywhere. The only "SPF" text in the app is a hardcoded static insight-card string with no sensor or data backing whatsoever. Requesting location permission solely to reproduce this screenshot section is explicitly out of scope.

## 9. Reference-Section Feasibility Table

| # | Screenshot section | Classification | Notes |
|---|---|---|---|
| 1 | Premium header (logo, avatar, date, greeting) | REAL DATA AVAILABLE for logo/avatar/greeting/date (device `LocalDate`); notification icon and "skin-sync/status chip" → NO REAL DATA SOURCE (no notifications system, no "sync" concept exists) — omit or make purely cosmetic/non-claiming |
| 2 | Skin Wellness Score card (score, hydration, clarity, texture, vs-last-scan) | NO REAL DATA SOURCE — must show honest neutral/pending state, no numbers |
| 3 | AI Face Analysis CTA | EXISTING FUNCTION — reuse existing Scan CTA, real `onScanClick` route |
| 4 | Today's Skin Environment (location/UV/humidity/AQI/SPF) | NO REAL DATA SOURCE — recommend omission or a clearly-labeled "Environmental data unavailable" shell, no permission request |
| 5 | Three shortcuts (Hairstyle, Routines, Progress) | EXISTING FUNCTION partially — Routines route exists; a direct Hairstyle route does not currently come from Home (only via Discover); "Progress" has no backing concept (ties to scan history, which doesn't persist) → NO REAL DATA SOURCE for Progress unless simplified to "view your last analysis" tied to in-session scan result only |
| 6 | Morning/Evening routine card | REAL DATA AVAILABLE — reuse `RoutineViewModel.plan`, existing doneCount/progress pattern; completion persistence is a known limitation, not new fakery |
| 7 | WeGlow Insights | NO REAL DATA SOURCE currently (existing cards are 100% hardcoded) — either omit, or clearly reframe as generic educational/static content not implying personalization |
| 8 | Curated For You | REAL DATA AVAILABLE — reuse `RecommendationViewModel` + `RecommendationCard` |
| 9 | Bottom navigation | EXISTING FUNCTION — preserve exactly |

## 10. Fake-Data Risks

Must NOT implement as real/dynamic-looking: numeric skin score, hydration/clarity/texture percentages, "+N vs last scan," any UV/humidity/AQI/location value, fake SPF timestamps, "Realtime AI" claims, fabricated scan/analysis history, fake dermatologist approval language, or a "skin-sync status" chip implying live sync that doesn't exist. The existing "AI Insights" and "For You" article are already borderline-fake today (static content presented as personalized-looking cards) — the redesign is a good opportunity to either honestly relabel them as generic/static tips or omit them, per the Phase-11 rule, rather than reproduce them as-is.

## 11. Exact Files Proposed for Modification (none touched yet)

- `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt` — primary redesign target.
- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` — small addition only: wire `RecommendationViewModel.load()` into Home's existing `LaunchedEffect`, and pass `recommendationState.result` + `routineState.plan?.evening` into `HomeScreen`'s params (extending its existing parameter list, not new architecture).
- Optionally one or two new small files under `ui/components/` (see below) if a reusable score-ring/chip is worth promoting rather than keeping private to `HomeScreen.kt`.

No repository, domain model, Supabase, ML, or navigation-graph-structure changes are needed.

## 12. Components to Reuse

`ProfileAvatar`/`rememberDecodedBitmap`, `WeGlowProductImage`, `WeGlowBottomNavigation` (untouched), `RecommendationCard` (for Curated For You), theme tokens (`WeGlowSpacing`, `WeGlowRadius`, `WeGlowSize`, `WeGlowElevation`, `Typography`, `Color.kt`), and the existing `doneCount/totalCount` routine-progress computation pattern.

Note: the Phase-2 `WeGlowComponents.kt` design-system file (`WeGlowCard`, `WeGlowPrimaryButton`, `WeGlowProgressIndicator`, etc.) exists but has **zero real call sites** anywhere in the app today — every screen hand-rolls its cards/buttons instead. Decide deliberately whether the redesign finally adopts these components or continues matching the de-facto hand-rolled style used by Discover/Routines/Profile, rather than introducing a third convention.

## 13. Components That May Need to Be Created

- A genuine reusable circular score/progress ring promoted to `ui/components` (current `SkinScoreRing` is private to `HomeScreen.kt` and doesn't even sweep proportionally — it always draws a full 360° arc, i.e. it's a placeholder, not a working gauge). Since it will show a neutral/pending state (no real score), this can stay simple: an outline ring with a "Scan to unlock" label rather than a functioning gauge.
- A shared chip/badge composable if a status chip is wanted in the header (only near-duplicate private ones exist elsewhere: `TraitChip`, `FeelingChip`) — must not imply live "sync" that doesn't exist; word it neutrally.
- Everything should be built from existing theme tokens — no new colors/typography.

## 14. Detailed Implementation Plan (visual order, for future approval — not started)

1. **Header** — reuse `ProfileAvatar`, logo, add real device date (`LocalDate.now()`), greeting from `displayName`; omit fake notification badge/skin-sync chip or make them inert/neutral.
2. **Wellness card** — redesign visually with the new score-ring shell, but keep score/hydration/clarity/texture as an honest "Not available yet — complete a scan" neutral state, exactly as today's `null`-safe pattern already does.
3. **AI Face Analysis CTA** — reuse existing `onScanClick`, restyle visually only.
4. **Environment card** — omit, or render as a clearly neutral "Environmental data unavailable" shell with no numbers.
5. **Three shortcuts** — Routines (real), reuse Discover route for Hairstyle-styled shortcut (matching current behavior) or leave Hairstyle unwired if no route exists from Home; Progress shortcut either omitted or pointed at Scan/Recommendations depending on what "progress" honestly means today.
6. **Routine card** — restyle existing real `plan.morning`/`doneCount` logic into the premium visual; optionally add `evening` toggle if desired (small, non-fake addition).
7. **Insights section** — reframe as generic/static educational tips (clearly non-personalized) or omit entirely.
8. **Curated For You** — wire `RecommendationViewModel.load()` + `RecommendationCard` into a `LazyRow`.
9. **Bottom navigation** — unchanged.

## 15. Architecture Risks

- Low risk overall since Home has no ViewModel of its own and is purely parameter-driven — a visual rewrite of `HomeScreen.kt` cannot break other screens' business logic.
- Main risk is scope creep: wiring `RecommendationViewModel` into Home's `LaunchedEffect` is a minor, safe addition, but it's the one place this touches shared navigation code (`WeGlowApp.kt`) rather than staying purely inside `HomeScreen.kt`.
- Styling-convention risk: the app has three parallel styling conventions (unused Phase-2 `WeGlowComponents`/tokens, hand-rolled legacy `CardWhite`/`JungeFont` literals used by all real screens, and Material3 `WeGlowTheme` colors used by none of the screens). Recommend matching the legacy hand-rolled convention actually used by Discover/Routines/Profile for visual consistency, rather than introducing a fourth convention.

## 16. Regression-Test Plan

- Verify all three Home navigation callbacks (Scan, Discover, Recommendations) still fire to the correct routes after redesign.
- Verify bottom nav still functions and Home remains the `popUpTo` anchor.
- Verify `ProfileViewModel.refresh()` and `RoutineViewModel.load()` (and newly added `RecommendationViewModel.load()`) still fire correctly on Home entry via `LaunchedEffect(Unit)`.
- Verify greeting/avatar render correctly for: no profile yet, profile with name only, profile with image, image load failure.
- Verify routine card renders correctly for: no routine yet (empty state), partial completion, full completion — and that toggling checkmarks doesn't crash across recompositions.
- Verify Curated For You renders correctly for: loading, empty catalog, error, and populated states — matching `RecommendationsScreen`'s existing error-message handling.
- Confirm no numeric skin/hydration/clarity/texture/UV/AQI value is ever rendered under any state (manual review + grep for stray literals post-implementation).
- Confirm existing Discover/Recommendations/Routines/Profile/Scan screens are visually and functionally untouched (diff review).

---

## Verdict

**SAFE TO PROCEED WITH HOME UI IMPLEMENTATION: YES** — with the explicit scope constraints above (neutral states for score/hydration/clarity/texture/environment, generic/omitted insights, no location permission request, and the one small `WeGlowApp.kt` wiring addition for Curated For You). No implementation has been started; this document is investigation only, pending explicit approval before any file is touched.
