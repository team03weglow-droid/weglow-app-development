[# Phase 9 Implementation Report — Personalized Product Recommendations & Skincare Routines

## Verdict

**PASS**

---

## Addendum — Post-review fixes (blank routine images + scan→recommendations verification)

A follow-up review before final sign-off raised two items, both now resolved.

### 1. Root cause of blank routine images

`DiscoverScreen`'s product image renderer always calls Coil's `AsyncImage` unconditionally,
supplying `placeholder`/`error`/`fallback` painters (the WeGlow logo) so Coil itself handles a
`null` model, a malformed URL, or a failed load. `RoutinesScreen`, however, manually branched
on `if (product?.imageUrl != null)` *before* ever reaching `AsyncImage` — when a real, matched
product simply had a `null` (or blank) `image_url`, the `else` branch rendered a plain
`Box(...).background(PageBackground)`, i.e. a literal white square, bypassing Coil's fallback
entirely. `RecommendationsScreen` did not have this manual branch and was already close to
correct, but did not sanitize blank (non-null but empty) URL strings.

### 2. Files changed

- **New:** `ui/components/ProductImage.kt` — a shared `WeGlowProductImage` composable that
  every screen now uses. It sanitizes blank strings to `null` (so Coil's `fallback` — not a
  slower `error` round-trip — always kicks in), then always calls `AsyncImage` with the WeGlow
  logo wired to `placeholder`/`error`/`fallback`.
- **Modified:** `ui/screens/DiscoverScreen.kt` (removed its private, now-duplicated
  `ProductImage`, its 4 call sites switched to the shared component — pure refactor, no visual
  change), `ui/screens/RoutinesScreen.kt` (removed the manual null-check branch that produced
  the blank box for a *matched* product with a missing image; the "no product matched this
  step at all" placeholder box is unchanged and remains a deliberately distinct state),
  `ui/screens/RecommendationsScreen.kt` (switched to the shared component for the same blank
  handling).
- **Test additions:** `feature/recommendation/RecommendationViewModelTest.kt` gained
  `realScanResultDetectionLabels_driveMatchingScoringAndBasis` (see §4 below). No UI/instrumented
  tests were added for image rendering, consistent with the rest of the codebase (Discover's own
  image rendering has never had a Compose UI test either) — the fix was verified by code
  inspection against Discover's already-proven pattern plus a full rebuild.

### 3. How image failure/placeholder now works

For any product actually shown (Discover, Recommendations, or a matched Routine step):
`imageUrl` is trimmed and blank strings become `null` → `AsyncImage` is always invoked →
`null` triggers Coil's `fallback` painter, a malformed URL or network failure triggers `error`,
and a slow load shows `placeholder` — all three painters point at the same WeGlow logo drawable,
so there is never an unexplained blank square, only an intentional branded placeholder. A
Routine step with **no product at all** (the category had no catalog match) is a distinct,
separate state and still renders an empty box — that is correct "missing step" UX, not an image
bug.

### 4. Confirmation of real scan → recommendation data flow

Traced end to end:

`LocalAcneScanRepository` (real ONNX inference) → `ScanViewModel.uiState.result: AcneScanResult`
→ `ScanResultsScreen` (only enables "View Recommendations" once `result != null`) →
`navController.navigate(Destination.Recommendations.routeFor(fromScan = true))` → in
`WeGlowApp.kt`, `scanConcerns = scanState.result?.detections?.map { it.label }` (read directly
from the same `ScanViewModel` instance held by the app — no serialization, no global mutable
holder, no re-running inference) → `RecommendationViewModel.load(scanConcerns)` →
`RecommendationEngine.recommend(profile, scanConcerns, products)` → real `CatalogRepository`
products.

Verified specifically:

- **No hardcoded concerns / no fake scan values** — `RecommendationViewModel` has no
  `AcneScanRepository` dependency at all, so it structurally cannot invent or re-derive scan
  data; it only ever receives whatever `List<String>?` the caller passes in.
- **No re-run of inference** — confirmed by the dependency graph above; opening Recommendations
  never touches `AcneScanRepository`/ONNX.
- **Scan concerns are not lost during navigation** — the `fromScan` boolean nav argument (added
  in the original Phase 9 pass) ensures a profile-only entry point (Home) never reads stale
  `scanState`, while a scan-triggered entry point always reads the live result held by the
  still-alive `ScanViewModel`.
- **Profile-only vs. profile+scan are distinguishable** — `RecommendationResult.basis`
  (`PROFILE_ONLY` vs `PROFILE_AND_SCAN`) drives distinct UI copy, covered by
  `profileOnly_yieldsProfileOnlyBasis` and `withScanConcerns_yieldsProfileAndScanBasis`.
- **Matching concerns receive the intended score; unrelated products are excluded** — proven by
  the new `realScanResultDetectionLabels_driveMatchingScoringAndBasis` test (§5).

### 5. Example of how a detected concern affects scoring

The new test builds a real `AcneScanResult` with `AcneDetection(label = "black heads", ...)` and
`AcneDetection(label = "papules", ...)`, extracts labels exactly as `WeGlowApp` does
(`detections.map { it.label }`), and runs them through the real pipeline:

- A product with `targetSkinType = "Oily"` and `targetConcerns = "Blackheads, Open Pores"`
  matches the user's `Oily` skin type (**+2**) and the detected `"black heads"` label — normalized
  to `blackheads`, matching the catalog's `"Blackheads"` regardless of spacing/casing — (**+3**),
  for a total score of **5**, and carries a reason mentioning "Black heads".
- An unrelated product (`targetSkinType = "Dry"`, `targetConcerns = "Chapped Lips"`) scores
  **0** against this profile/scan and is **excluded from the results entirely** — never shown
  with a fabricated or empty reason.
- `result.basis == PROFILE_AND_SCAN`, confirming the scan was actually used.

### 6. Test/build results (after these fixes)

| Task | Result |
|---|---|
| `./gradlew architectureCheck` | **PASS** |
| `./gradlew compileDebugKotlin` | **PASS** (pre-existing deprecation warnings only) |
| `./gradlew testDebugUnitTest` | **PASS** — 102/102 tests, 0 failures |
| `./gradlew lintDebug` | **PASS** — same single pre-existing `AutoboxingStateCreation` finding on an untouched line in `RoutinesScreen.kt`; nothing new |
| `./gradlew assembleDebug` | **PASS** |

### 7. Phase 9 status

**PASS. Safe to commit.** No Git operations were performed.

---

## 1. Existing recommendation/routine implementation discovered

No recommendation engine existed prior to this phase. `RoutinesScreen` had a fully hardcoded
morning/evening step list (fake product names, brands, badges like "PURIFYING" / "1 MIN",
`essential` / `initialDone` flags, drawable images). `ScanResultsScreen` already had a "View
routines" button wired to an `onViewRecommendations` callback, but it navigated to the
(unrelated) Routines destination — no real Recommendations concept existed anywhere in the
app. Hairstyle recommendations (`InMemoryHairstyleRepository`) are a separate, teammate-owned,
admittedly-simulated feature and were left untouched, per scope boundaries.

## 2. Discover behavior — preserved

`DiscoverScreen`, `DiscoverViewModel`, and `SupabaseCatalogRepository` were left untouched
except for additive field mapping (see §3). Search, category filter, price-range filter, and
gallery/list toggle all remain unchanged and continue to pass their existing tests.

## 3. Product schema/mapping actually used

Extended `Product` (backward-compatible — new fields default to `null`, so existing positional
constructors and tests are unaffected) and `SupabaseCatalogRepository.toProductOrNull()` to
also map `Target_Skin_Type`, `Target_Concerns`, `Texture`, and `price_text_raw` (as a price
fallback). The mapping reuses the existing case/separator-insensitive column matcher already in
place in the repository, and was verified with new tests using the real dataset's
`Product_ID` / `Target_Skin_Type` / `Target_Concerns` casing.

## 4. Recommendation architecture implemented

```
Compose
  → RecommendationViewModel / RoutineViewModel
  → RecommendationEngine / RoutineBuilder   (domain layer, pure functions, no I/O)
  → ProfileRepository + CatalogRepository   (existing repositories, reused as-is)
```

No Supabase access from Compose, no new repository, no second product store, and no global
mutable state. This was verified against the project's `architectureCheck` Gradle task, which
forbids `domain/` and `feature/`/`ui/` code from touching Supabase or `data.remote` directly.

## 5. Exact recommendation matching/ranking rules

For each product, a deterministic score is built up:

- **+2** if `Target_Skin_Type` text (normalized) contains the user's normalized skin type, or
  contains a universal phrase such as "all skin types".
- **+3** per detected scan concern whose normalized letters appear in `Target_Concerns`. The
  normalization strips spacing/casing and tolerates simple plural mismatches, so the model's
  raw label `"black heads"` matches catalog text `"Blackheads"`.
- **+1** only if the user's profile marks them sensitive **and** the catalog text itself
  mentions "sensitive" — sensitivity-safety is never inferred from category or price.

Products scoring `0` are excluded entirely — the result list is never padded with unrelated
items. Remaining products are sorted by score (descending), then by name for stability, and
capped at 12 results.

## 6. How real profile data is used

`skin_type` and `is_skin_sensitive` are read via
`ProfileRepository.getProfile(authRepository.currentUserId())` — the same pattern already used
by `ProfileViewModel`. The user id always comes from the authenticated session, never from the
UI layer.

## 7. How real acne scan data is used

`AcneDetection.label` values from the most recent scan are passed as a small, stable
`List<String>` at the navigation call site — never a global mutable holder, never a serialized
image, and the model is never re-run just to open Recommendations. A `fromScan` boolean
navigation argument distinguishes entry points, so a profile-only visit (e.g. from Home) can
never accidentally pick up stale scan state left over from an earlier session.

## 8. Recommendations-without-scan behavior

`HomeScreen` now has a "Recommended for You" entry card that opens Recommendations with
`scanConcerns = null` (profile-only). This is distinct from `ScanResults → "View
Recommendations"`, which passes the real detected concerns. The UI copy makes the basis
explicit:

- "Based on your profile."
- "Based on your profile and recent scan concerns."
- "Based on your profile. Your recent scan detected no concerns."

Scan concerns are never fabricated when no scan exists.

## 9. Dedicated Recommendations page behavior

A new `RecommendationsScreen` was added with:

- Title: "Recommended for You"
- A context line explaining the basis (profile / profile + scan)
- Loading state
- Error state with retry
- A distinct "no personalization signal" state (neither skin type, sensitivity, nor scan
  concerns were available) vs. a "no matching products" state (signals existed, nothing in the
  catalog matched)
- Real product cards: image, brand, name, price, and per-product explainable reasons
- A closing disclaimer: *"Recommendations are for general skincare guidance and are not
  medical advice."*

## 10. Scan Results → Recommendations navigation

The button was renamed "View Recommendations" and now navigates to
`Destination.Recommendations.routeFor(fromScan = true)`, carrying only the small `fromScan`
flag through navigation (the actual concern labels are read from the existing `ScanViewModel`
state already held by the app, not re-serialized).

## 11. Routine implementation

`RoutinesScreen` was kept (day picker, feeling chips, and daily-note UI were left untouched),
but its hardcoded step arrays were replaced with `RoutineViewModel` / `RoutineBuilder` output:

- **Morning:** Cleanser → Moisturizer → Sunscreen
- **Evening:** Cleanser → Treatment → Moisturizer

Each step is matched by keyword against the real, free-form `Category` text in the catalog. A
step with no matching product shows "No suitable product found for this step yet" rather than
inventing one. A personalized (scored) match is always preferred; the builder falls back to a
real, un-personalized catalog product — labeled honestly as "General pick for this step" — only
when nothing personalized is available for that category.

## 12. Medical/safety wording

No diagnosis or cure claims appear anywhere in the new UI. Reasons are phrased as "Matches your
X skin profile", "Matches concerns detected in your scan", and "Formulated for sensitive skin"
(only shown when the catalog text supports it), plus the general skincare disclaimer noted in
§9.

## 13. Files changed

**New files:**

- `domain/model/Recommendation.kt`
- `domain/model/Routine.kt`
- `domain/recommendation/RecommendationEngine.kt`
- `domain/recommendation/RoutineBuilder.kt`
- `feature/recommendation/RecommendationViewModel.kt`
- `feature/routine/RoutineViewModel.kt`
- `ui/screens/RecommendationsScreen.kt`

**Modified files:**

- `domain/model/Product.kt`
- `data/repository/SupabaseCatalogRepository.kt`
- `navigation/Destination.kt`
- `navigation/WeGlowApp.kt`
- `ui/screens/HomeScreen.kt`
- `ui/screens/RoutinesScreen.kt`
- `ui/screens/ScanResultScreen.kt`

## 14. Tests added/changed

31 new tests across 4 new test files, plus 2 additions to an existing test file:

- `RecommendationEngineTest` (13 tests) — skin-type normalization/matching, concern
  normalization/matching (casing, spacing, plurals), multiple detected concerns, sensitivity
  gating, no-scan vs. scan-with-no-concerns basis, no profile/no scan signal, no matching
  products, empty catalog, ranking order.
- `RoutineBuilderTest` (5 tests) — per-category personalized selection, evening treatment slot,
  missing-category behavior (no invented product), catalog fallback with honest reason,
  no-duplicate-product-within-a-period.
- `RecommendationViewModelTest` (7 tests) — profile-only vs. profile+scan basis, unauthenticated
  user, profile repository failure, catalog repository failure, empty catalog, duplicate-load
  guarding.
- `RoutineViewModelTest` (4 tests) — successful load, unauthenticated user, catalog failure,
  missing-category step.
- `SupabaseCatalogRepositoryTest` (+2 tests) — real dataset column casing
  (`Product_ID`/`Target_Skin_Type`/`Target_Concerns`/`Texture`), absence of target fields.

## 15–19. Build verification results

| Task | Result |
|---|---|
| `./gradlew architectureCheck` | **PASS** |
| `./gradlew compileDebugKotlin` | **PASS** (pre-existing deprecation warnings only) |
| `./gradlew testDebugUnitTest` | **PASS** — 101/101 tests, 0 failures |
| `./gradlew lintDebug` | **PASS** — one pre-existing `AutoboxingStateCreation` finding on an untouched line in `RoutinesScreen.kt`; nothing new introduced |
| `./gradlew assembleDebug` | **PASS** |

## 20. Remaining non-blocking issues

- Category-keyword heuristics for routine steps (`cleans` / `moistur` / `spf` / `serum`, etc.)
  are a best-effort mapping, since the actual live `Category` values in the Supabase table
  weren't directly inspectable from this environment. These are easy to tune once real category
  values are confirmed.
- No budget/price signal is used in recommendation scoring. This was intentional, per the
  Phase 9 brief, to avoid coupling Recommendations to Discover's transient price-filter UI
  state.
- `Icons.Filled.ArrowBack` deprecation warnings appear across the codebase (including one new
  file); these are pre-existing elsewhere and not a regression introduced by this phase.

## 21. Safe to commit and merge

**Yes.**
]()