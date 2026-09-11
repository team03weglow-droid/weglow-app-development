# PHASE 11 — Full Application Testing Report

Branch: `phase-11-testing`. Scope: aggressive testing and regression hardening of the existing
WeGlow app — no new features, no redesign, no weakened security, no fake test success.

> **Addendum note:** this version supersedes an earlier draft of this report that stated "no
> routines correctness defect was found." A user manual observation on a running emulator
> (Routines date selector appearing to do nothing meaningful) was investigated end-to-end and
> found to be a real, verified defect (Defect 5, §3) — the original code-reading pass had
> checked the date chip's own highlight state but not whether completion checkmarks were
> actually keyed by date. It has been fixed, tested, and is reflected throughout this report.
> A "Team 03" observation was also re-investigated and reconciled (§15).

## 1. Overall verdict

**PASS WITH MANUAL VERIFICATION REQUIRED**

All automated gates pass (architecture check, compile, unit tests, lint, debug assembly), and
every reachable production code path was read and reasoned about against its tests. However,
real Supabase network behavior, real camera/gallery hardware, real on-device navigation/back
stack, and real ONNX inference on a device were inspected and unit-tested at their boundaries
but were **not exercised on an emulator or device** in this session. Plain `PASS` would overstate
what was actually verified.

## 2. Test inventory

- Existing tests before Phase 11: **107** (`testDebugUnitTest`, all passing baseline)
- Tests after Phase 11 (first pass): **110** (+3 `RecommendationEngine` gap tests)
- Tests after Phase 11 (this addendum — Routines date-selector defect, see §3 Defect 5):
  **117**
- New tests, total: **10** (3 in `RecommendationEngineTest`, 7 in the new
  `RoutineCompletionKeyTest`)
- Updated tests: **0** existing test bodies changed (existing test files/new files gained test
  methods; no existing assertion was altered)

### Inventory findings (Part 1)

The existing suite (16 test files) was already unusually thorough for a project at this stage:
auth startup/sign-in/sign-up/Google/sign-out routing, onboarding persistence and identity
fallback, profile load/upload/rollback, catalog column-mapping fixtures (including real dataset
casing variants), Discover cache/retry behavior, the full `RecommendationEngine` scoring/
normalization matrix, `RoutineBuilder` category fallback rules, `ScanViewModel` cancellation/
retry/no-stale-result behavior, `YoloPostProcessor` NMS/letterbox/threshold math, a real bundled
ONNX model smoke test (`BundledAcneModelTest`), profile-image validation and camera-capture-file
lifecycle, and an `androidTest` end-to-end offline scan test.

Gaps found and closed in Phase 11 (Part 6): `RecommendationEngine` had no test for the 12-result
cap, no test for the alphabetical tie-break on equal scores, and no explicit test for a concern
that shares no root with any catalog text. All three were added (§17).

Gap found and **not** closed (by design, see §8/§19): `ScanViewModel.setPhoto(Uri)` — the method
that resets scan state and deletes an owned cache file — has no JVM unit test anywhere in the
suite, because `android.net.Uri` throws when touched under the plain JUnit/Gradle unit-test
harness this project uses (no Robolectric, no `returnDefaultValues`, confirmed by grep: zero
existing tests reference `android.net.Uri`). Adding Robolectric to close this single gap was
judged out of proportion for Phase 11 (a build/dependency change, not a "smallest safe fix").
The logic was instead verified by code inspection (§8) and is covered by manual Test N1.

No duplicated or purely implementation-detail tests were found. No test was weakened, removed,
or given a fake pass to make this phase's numbers look better.

## 3. Defects discovered

Five defects were found. Defects 1–4 are the same family — **UI copy presenting fabricated
numbers/history as if they were genuine, personalized, measured results** (Part 15/16 fake-data
audit). Defect 5 is a real **functional/state-management defect** found only after a manual
runtime observation was reconciled against the code (see below) — the original code-reading pass
had read the visible date-selector highlight logic as correct and stopped short of checking
whether *completion state* was actually keyed by date, which was the wrong scope to stop at.

### Defect 1 — Home skin-score card presented invented numbers as a real measurement

- **Severity:** Medium (user-trust; no data loss, no crash, but a factually false personalized
  claim shown to every user on the primary screen)
- **Component:** `ui/screens/HomeScreen.kt`
- **Reproduction:** Sign in, land on Home. The "THIS WEEK" card always showed skin score `74`,
  `+3 from last scan`, "Your skin is improving!", and Hydration/Clarity/Texture at `82%`/`68%`/
  `71%` — identical for every user, regardless of whether they had ever run a scan.
- **Root cause:** `skinScore`, `scoreDelta`, `hydration`, `clarity`, `texture` were hardcoded
  local constants with no backing data source anywhere in the app (`AcneScanResult` has no score
  field; no scoring pipeline exists). This was already identified and explicitly **left
  unfixed** in Phase 10 (`docs/phase-10/PHASE_10_SECURITY_PERFORMANCE_REPORT.md`, §14/§19) as
  out of that phase's scope. Phase 11's brief explicitly directs fixing it.
- **Fix:** Replaced the hardcoded values with `null`s and rendered the existing widgets in a
  neutral pending state: the ring shows `--` instead of a fake ring fill, the delta/"improving"
  line was replaced with "Complete a skin scan to see your results here.", and each progress bar
  shows `--` with an empty (not falsely-zero) track. No scoring algorithm was invented; the card
  structure, colors, and layout are unchanged.
- **Regression test:** Not unit-testable (Compose UI text), but the change is a pure text/data
  substitution with no business logic, verified by `compileDebugKotlin`/`lintDebug`/
  `assembleDebug` passing and by manual Test P1 in the matrix.

### Defect 2 — Profile "Skin Score" card had the same fabricated-measurement pattern

- **Severity:** Medium (same class as Defect 1, same screen family)
- **Component:** `ui/screens/ProfileScreen.kt`
- **Reproduction:** Open Profile. The "Skin Score" card always showed `84/100` with a fixed 84%
  ring sweep and "Your botanical glow index is improving." — identical for every user.
- **Root cause:** Same as Defect 1 — hardcoded constants with no backing measurement. Not
  previously flagged in Phase 10 (that phase's audit covered Home only).
- **Fix:** Ring shows `--`/100 with an empty track; caption replaced with "Complete a skin scan
  to see your results here." Card structure unchanged.
- **Regression test:** Same as Defect 1 (manual Test P1).

### Defect 3 — Profile "Latest Scan" row invented a scan event that may never have happened

- **Severity:** Medium-High (this one is worse than 1/2: it asserts a specific historical event —
  "2 days ago · Hydration focused" — for every user, including one who has never run a scan)
- **Component:** `ui/screens/ProfileScreen.kt`
- **Reproduction:** Open Profile, look at "Latest Scan". Always reads "2 days ago · Hydration
  focused" regardless of whether any scan was ever performed (scan results are not persisted
  anywhere; this text could not possibly be real for any account).
- **Root cause:** Hardcoded string; no scan-history persistence exists in the app to source it
  from, and Part 15 explicitly forbids inventing that persistence in this phase.
- **Fix:** Replaced with "No scan yet. Analyze your skin to see results here." Row structure
  (icon, chevron, layout) unchanged; interaction (currently a no-op tap target, pre-existing and
  out of scope) unchanged.
- **Regression test:** Manual Test P1.

### Defect 4 — Discover "Featured" card claimed a fabricated match score and a fixed skin type

- **Severity:** Low-Medium (isolated to one card; the real, computed personalization already
  lives correctly in Recommendations)
- **Component:** `ui/screens/DiscoverScreen.kt`
- **Reproduction:** Open Discover. The top featured-product card always shows a "98% Match"
  badge and the section header always reads "Recommended for your Dry Skin" — for every user,
  including ones whose real profile skin type is Oily/Combination/Sensitive/unset.
- **Root cause:** Hardcoded strings unrelated to `DiscoverViewModel`/`RecommendationEngine`,
  which are not wired into this screen at all (Discover intentionally stays a plain catalog
  browser per the Phase 11 brief: "Do not change Discover into Recommendations").
  "98% Match" implies a computed score that does not exist for this card; "Dry Skin" is simply
  wrong for most users.
- **Fix:** Badge text changed to "Featured" (no fabricated percentage); header changed to
  "Featured Products" (no fabricated skin-type claim). No new personalization was added — this
  intentionally does not wire the real `RecommendationEngine` into Discover, per the explicit
  instruction not to turn Discover into Recommendations.
- **Regression test:** Manual Test P2.

### Defect 5 — Routines date selector visually updates but completion state is shared across every date

- **Severity:** Medium-High (functional defect, not just copy — the date selector's core
  interaction is misleading: a user marking Tuesday's cleanser done sees it marked done on every
  other date too, and there is no way to track a routine per day at all)
- **Component:** `ui/screens/RoutinesScreen.kt`
- **Reported by:** User manual observation on a running emulator ("tapping different dates
  appears to do nothing meaningful; the same routine/completion state appears across dates"). The
  original Phase 11 pass had read this screen and reported no routine-correctness defect — that
  was wrong, because the code-reading pass verified the date chip's own highlight state (correct)
  but did not check whether the *completion checkboxes* were actually keyed by the selected date.
  This is now corrected per this addendum.
- **End-to-end audit findings, point by point:**
  1. **What controls the selected date?** `var selectedDay by remember { mutableStateOf(2) }`.
  2. **Does tapping a date update it?** Yes — each date chip's `Modifier.clickable { selectedDay
     = index }` does set it correctly.
  3. **Does the selected-date visual state change?** Yes — the tapped chip's text goes bold/black
     and its circle becomes a filled dark-green circle with white text; this part was already
     correct.
  4. **What is supposed to be date-specific?** Per the product requirement, the routine
     *template* (which products fill Cleanser/Moisturizer/Sunscreen/Treatment) is correctly
     **not** date-specific — `activeSteps` only depends on `isMorning`, which is intentional and
     correct. What *should* be date-specific is each step's **completion checkmark**.
  5. **Are completion states shared across all dates? (the actual bug)** Yes, confirmed: the
     checkbox key was `"${if (isMorning) "AM" else "PM"}-$index-${step.product?.id}"` — it never
     included `selectedDay` at all. Marking a step done under any date marked it done under
     *every* date, because they all mapped to the identical key in the single `doneState:
     Set<String>`. This is the exact defect the user observed.
  6. **Are Morning/Evening completion states shared incorrectly?** No — the key already
     included an `"AM"`/`"PM"` prefix, so Morning and Evening were already independent of each
     other. This part did not need a fix.
  7. **Does changing dates just redraw the same routine with no meaningful date-specific state?**
     Yes, prior to the fix — the step list looked byte-for-byte identical (products *and*
     checkmarks) across every date, which is exactly why it "appeared to do nothing meaningful."
  8. **Does navigating away/back reset or fabricate completion history?** It resets, but does
     not fabricate: `selectedDay`, `isMorning`, and `doneState` all use plain `remember` (not
     `rememberSaveable`), so leaving the Routines tab and returning re-composes them from their
     initial defaults (selected day resets to index 2, Morning is reselected, all checkmarks
     clear). No fake "already completed" state is ever shown — the screen honestly returns to an
     empty state rather than inventing history. This reset-on-navigation behavior was judged
     acceptable for a "session-only" design and was **not** changed, to avoid the larger,
     riskier change of moving this UI-local state to `rememberSaveable`/a ViewModel, which is
     unrequested scope for this phase (see "smallest honest behavior" below).
- **Root cause:** The completion-tracking key used to build `doneState` never included the
  selected date, only the period (AM/PM) and step position/product. There is no per-date routine
  history anywhere in the app (no Supabase table, no local database) — this was correctly *not*
  invented, per the explicit instruction not to build persistence infrastructure in this phase.
- **Fix (smallest honest behavior — session-only, in-memory, per date):**
  - Added a small pure function, `routineStepKey(day, isMorning, stepIndex, productId)` in the
    new file `feature/routine/RoutineCompletionKey.kt`, which folds the selected date into the
    key: `"$day-${AM|PM}-$stepIndex-$productId"`.
  - `RoutinesScreen.kt` now calls this function instead of building the key inline, so each
    date's checkmarks are independent, Morning/Evening remain independent (unchanged behavior),
    and switching back to a previously-visited date within the same screen session correctly
    shows that date's own completion state again (nothing is cleared when `selectedDay` changes —
    only the *key* changes, so the `Set<String>` naturally keeps every date's history distinct
    for as long as the screen instance is alive).
  - Added an explicit code comment at the `doneState` declaration and in
    `RoutineCompletionKey.kt` stating this is session-only and does not survive an app restart —
    exactly the limitation the brief requires to be made explicit.
  - **No Supabase table, migration, or any other persistence was added.** Completion history
    still does not survive an app restart (or leaving the Routines tab, per point 8 above) — this
    is a real, disclosed limitation, not fixed further in this phase.
- **Regression tests:** `app/src/test/java/com/example/weglow/feature/routine/
  RoutineCompletionKeyTest.kt` (7 new tests) — different days produce different keys for an
  otherwise identical step; Morning/Evening remain independent for the same day/step; different
  step indices on the same day/period produce different keys; the same day/period/step/product
  always produces the same key (idempotent toggling); two different no-matched-product steps on
  the same day never collide; and two tests that directly reproduce the reported scenario against
  a real `Set<String>` — toggling a step done on day 0 does not mark day 1's equivalent step done,
  and switching from day A to day B and back to day A still shows day A's completion.

### Related finding — explicitly NOT fixed (teammate-owned, out of scope)

`data/repository/InMemoryHairstyleRepository.kt` hardcodes a `matchPercent` (96, 92, 89, 88, …)
per hairstyle, keyed only on the onboarding `gender` answer — not on any real face-shape
detection. This is the same class of fabricated-personalization issue as Defects 1–4, but it is
teammate-owned face-shape work. Per the explicit instruction ("Do not modify teammate-owned
face-shape work unless necessary to resolve a compile/build regression"), **this was left
untouched** and is reported here for visibility rather than fixed. See §21 (TEAMMATE-OWNED).

## 4. Authentication test results

Read `AuthViewModel`, `SupabaseAuthRepository`, and `AuthViewModelTest` in full. All of the
required flows already have passing, non-network, fake-repository-backed unit tests: startup
without a session → LOGIN; startup with a session + completed profile → HOME; startup with a
session + incomplete profile (including a profile row that exists but has
`onboarding_completed = false`, which must route by the flag, not row existence) → ONBOARDING;
sign-in success routes by profile completion; sign-in failure surfaces an error and does not
authenticate; Google OAuth is confirmed not to authenticate merely by opening the browser
(`signInWithGoogle()` succeeding is distinct from the deep-link completing); sign-up preserves
its own `SignedUp(fullName)` event against a race with the session observer's `AUTHENTICATED`
emission (a regression explicitly guarded from a Phase 4 bug); sign-out clears the session and
emits `SignedOut`. No fake auth path exists — every success path goes through the real
`AuthRepository` interface, and no unit test calls a live network. Protected navigation and the
logout back-stack are enforced by `WeGlowApp`'s `popUpTo(navController.graph.id) { inclusive =
true }` on sign-out, which clears the entire authenticated back stack before navigating to
Login — verified by code inspection; back-stack behavior itself requires a running Activity and
is manual Test K2.

## 5. Onboarding test results

Read `OnboardingViewModel`, `SupabaseProfileRepository`, and `OnboardingViewModelTest`. Answers
persist through `ProfileRepository.saveProfile` (never held only in UI memory) keyed to
`authRepository.currentUserId()`, with `onboardingCompleted = true` only set on the final saved
profile. Save failure does not set `saveCompleted`. Save without an authenticated user does not
call the repository at all. `start()` correctly resolves the display name from an explicit
signup name, then falls back to the identity provider's metadata, then to `null` — and
critically resets *all* prior answers so a second `start()` call (session restore / re-entry)
can never leak a previous attempt's answers into a new save. "Skip for now" on skin type persists
`null`, not a fabricated default. No new questionnaire requirements were introduced.

## 6. Profile test results

Read `ProfileViewModel`, `SupabaseProfileRepository`, `SupabaseProfileImageRepository`, and
`ProfileViewModelTest`. Persisted `full_name` wins over the identity-provider name; a missing
persisted name falls back to identity, never to a hardcoded placeholder. Profile-image upload is
tested for: missing auth (rejected before upload), successful upload (persists path, updates
displayed bytes), Storage failure (no false success, no DB write attempted), DB write failure
after a successful Storage upload (the just-uploaded object is rolled back via
`deleteProfileImage`, and the failure is the one surfaced — a stale-orphan/false-success case
that is correctly handled), and replacing an existing image (old object deleted only after the
new reference is confirmed persisted). Duplicate taps while uploading are ignored. No JVM test
performs a real Supabase Storage upload — all of it goes through `ProfileImageRepository` fakes.
Confirmed no static avatar (`avatar_rukman`) or hardcoded identity (`Team 03`) exists anywhere in
current `app/src/main` (both were Phase 10 fixes; grep for both strings returns zero matches).

## 7. Catalog/Discover test results

Read `SupabaseCatalogRepository`, `DiscoverViewModel`, and both existing test files plus this
phase's review. Column mapping is defensively tested against the real dataset's column casing
(`Product_ID`, `Target_Skin_Type`, `Target_Concerns`, etc.), camelCase, snake_case, and several
alternate names per field; null/blank optional fields map to `null`, not empty-string noise;
price parsing has a raw-fallback path (`price_text_raw`) and strips thousands separators before
parsing; image URL extraction handles a plain string, a JSON-stringified array (escaped
`\/` too), and a nested object/array of image objects, always taking the first resolvable URL.
An empty catalog result and a decode failure both surface `"Products are temporarily
unavailable."` — a message safe to show a user, with the real cause left in server logs, not the
client. `DiscoverViewModel` keeps a successful load and does not re-fetch on repeat
`loadProducts()` calls (tested), while a failed load always retries on the next call (tested).
Discover intentionally remains a plain browsable catalog, not converted into a Recommendations
surface. The one defect found here (Defect 4, §3) was a UI-copy issue, not a data-mapping issue.

## 8. Scan pipeline test results

Read `ScanViewModel`, `LocalAcneScanRepository`, `ScanPhotoDecoder`, `YoloPostProcessor`, and all
four related test files (`ScanViewModelTest`, `YoloPostProcessorTest`, `BundledAcneModelTest`,
`OfflineAcneScanTest`). `ScanViewModel.setPhoto(uri)` was specifically checked against the
critical regression named in the brief: **selecting a new photo after a successful (or failed)
scan must not leave the old result visible as if it belonged to the new photo.** It does not —
`setPhoto` replaces the entire `ScanUiState` with a fresh `ScanUiState(photoUri = uri)`,
unconditionally clearing `result` and `error`, and separately deletes the previous file only if
it was a file this app itself wrote for a scan capture (never a `content://` gallery pick,
verified by `ScanCacheFileOwnershipTest`). This is correct as written; **no fix was needed**.
`analyzeReference` also resets `result = null` at the start of every analysis attempt (so a
retry after failure cannot show a stale success, and vice versa), and cancellation is verified to
never publish a late result (`cancellationDoesNotPublishLateResults`). The one coverage gap
found — `setPhoto` itself has no JVM unit test because it takes a real `android.net.Uri`, which
is not usable under this project's Robolectric-free unit-test setup — is documented in §2/§19
rather than patched with a disproportionate build change; the underlying logic was verified by
code reading, and manual Test N1 covers it end-to-end on device.

`ScanPhotoDecoder` (Part 9) was reviewed but not newly unit-tested — it is inherently
Android-framework-bound (`ImageDecoder`/`BitmapFactory`/`ExifInterface`/`ContentResolver`). Its
existing coverage is the `androidTest` `OfflineAcneScanTest`, which exercises a real EXIF-rotated
JPEG through the real decoder and repository and asserts the returned `imageWidth`/`imageHeight`
reflect the *oriented* image. Device verification of large-input bounding, additional EXIF
orientations, and legacy (`SDK < 28`) decoding remains a manual/device concern — no separate new
test fixture was created per the instruction not to manufacture large binary fixtures
unnecessarily.

`LocalAcneScanRepository`'s session-reuse change (Phase 10) was checked against the risk named in
the brief (input shape, RGB order, normalization, threshold, labels, letterboxing, NMS, box
conversion all unchanged): `sessionHandle` still validates the exact tensor shape
`[1,3,640,640]` before use, `prepareInput` still writes RGB channel-first `[0,1]` floats, and the
model-metadata thresholds/labels are still read from the same `acne/model.json`/`.onnx` bundle
exercised end-to-end by `BundledAcneModelTest` (a real ONNX Runtime session run whose output
shape and SHA-256 are asserted) and `OfflineAcneScanTest`. No regression found.

## 9. Scan → Recommendations contract results

Traced the full chain named in the brief: `ScanScreen`/`CameraCaptureScreen` write a
`weglow_scan_*.jpg` file → `ScanViewModel.setPhoto`/`analyze` → `LocalAcneScanRepository.analyze`
→ real ONNX inference → `AcneScanResult` → `WeGlowApp`'s Recommendations route extracts
`scanState.result?.detections?.map { it.label }` only when navigated with `fromScan = true` →
`RecommendationViewModel.load(scanConcerns)` → `RecommendationEngine`/`CatalogRepository`. No
hardcoded concern list exists anywhere in this chain (confirmed by inspection and by
`RecommendationViewModelTest.realScanResultDetectionLabels_driveMatchingScoringAndBasis`, which
uses a real `AcneScanResult` with real detections at the ViewModel boundary — exactly the pattern
the brief asks for). There is no global mutable scan-result holder — the only place a scan
result lives is `ScanViewModel`'s own `StateFlow`, scoped to that ViewModel instance for the
app's lifetime (created once in `WeGlowApp`). Scan never re-runs just to open Recommendations —
`onViewRecommendations` on `ScanResultsScreen` only navigates; it never calls `analyze()` again.
Profile-only Home recommendations correctly pass `fromScan = false`, and `WeGlowApp` only reads
`scanState.result` when `fromScan == true`, so a stale scan result from an earlier session cannot
leak into a profile-only recommendation request — this is exactly the `fromScan` navigation
argument documented in `Destination.kt` for this purpose, and is verified indirectly by
`RecommendationViewModelTest`'s `profileOnly_yieldsProfileOnlyBasis` (called with
`scanConcerns = null`, matching what `fromScan = false` produces at the call site).

## 10. RecommendationEngine test results

Read the full engine and its (now 15-test, was 12) test file. Verified: skin-type matching is
case/format-insensitive and recognizes universal-skin-type catalog phrases; concern matching
normalizes case/spacing/simple plurals (`letterToken`); multiple detected concerns each
contribute `+3` and are individually named in the reason; sensitivity's `+1` only applies when
the catalog text itself says "sensitive" (never inferred from category/price); products scoring
zero are excluded entirely, never shown with a fabricated reason; results sort by score
descending with a documented, now-tested alphabetical tie-break; profile-only vs.
profile-and-scan basis is set correctly, including the "scan ran but found nothing" case
(`emptyList()` vs. `null`). Phase 11 closed three real gaps: the 12-result cap was previously
asserted only implicitly, an unrelated concern (no shared letters with any catalog text) had no
explicit "must not match" test, and the alphabetical tie-break for equal scores was untested.
All three are now covered (`moreThanTwelveMatches_areCappedAtTwelve`,
`unrelatedConcern_doesNotMatchAndProductIsExcluded`,
`equalScoringProducts_tieBreakAlphabeticallyByName`). No scoring behavior was changed to make a
test convenient.

## 11. Routine test results

Read `RoutineBuilder`, `RoutineViewModel`, and both test files. Morning is
Cleanser→Moisturizer→Sunscreen, evening is Cleanser→Treatment→Moisturizer, matching the brief
exactly. A personalized (scored) match is preferred per slot; when none exists, a real catalog
product is used as a category fallback with an honest "General pick for this step" reason
(never a personalized-sounding claim); when neither exists, the step is returned with
`product = null` and empty reasons — the UI (`RoutinesScreen`) renders this as an explicit
"No suitable product found for this step yet," never a fabricated product. The same product
cannot fill two slots in the same period (tested). `RoutineViewModel` caches a successful plan
per its own lifetime (does not re-fetch the shared catalog on every tab revisit) and always
retries after a failure — same pattern as `DiscoverViewModel`, both tested.

**Addendum:** the above covers `RoutineBuilder`/`RoutineViewModel` (the *product* routine
template), which was and remains correct and unchanged. It did **not** originally cover
`RoutinesScreen`'s own date-selector and per-step completion UI state, which is a separate,
screen-local concern with no ViewModel backing it. A user manual observation surfaced that this
UI state was defective — see Defect 5 in §3 for the full end-to-end audit, root cause, fix, and
the 7 new `RoutineCompletionKeyTest` tests. This is now closed and documented as an explicit
session-only limitation (no completion history is persisted across an app restart).

## 12. Navigation audit results

Read `Destination.kt` and `WeGlowApp.kt` end-to-end. No raw route string duplication (`Destination`
is the single source of routes); the one parameterized route (`Recommendations`) uses a typed
`NavType.BoolType` argument with an explicit default, not string concatenation at call sites.
`popUpTo` usage was checked at every navigation call: Loading→{Login,Onboarding,Home} clears
Loading (`inclusive = true`); Login/Signup→post-auth clears the auth screens; the onboarding
wizard's terminal screen clears the *entire* wizard back to `AgeSelection` (a Phase 3 fix,
confirmed still present) so a completed user can never navigate "back" into onboarding; Scan→
ScanResults clears the Scan screen itself so a completed scan cannot be "redone" via back
navigation without an explicit new capture; logout clears the whole graph
(`popUpTo(navController.graph.id) { inclusive = true }`). No navigation loops or duplicate
destinations were found. The `fromScan` argument (§9) is the mechanism that prevents accidental
stale-scan consumption and was traced explicitly. No changes were made — the existing navigation
graph was already correct.

## 13. Failure-state results

Verified by code inspection across every ViewModel touched: catalog/profile/recommendation/
routine loads on repository failure always set an `errorMessage` from `Result.exceptionOrNull()`
(never a fabricated success), never leave `isLoading = true` forever (every code path that starts
loading has a matching terminal state), and never invent data to mask a failure. `Discover`,
`Recommendations`, and `Routines` all expose a retry path that is wired to a real "Try again" /
retry action in their screens. Scan failures map internal exception types to short, user-safe
messages (`IOException` → "Cannot read this photo…", generic → "Analysis failed. Please retry…")
— no stack trace or internal exception text reaches the UI. `SupabaseCatalogRepository` was
specifically checked for backend-diagnostic leakage: both of its failure messages are static,
user-safe strings, never the underlying Postgrest/network exception text. Camera-permission
denial and camera-unavailable states have dedicated, non-error UI (§ScanScreen). None of these
failure paths were exercised against a live, disconnected Supabase instance in this session —
that requires a device/emulator (manual Tests M1–M4).

## 14. Security/privacy regression results

Re-read `AndroidManifest.xml` in full: `android:usesCleartextTraffic="false"` present,
`android:allowBackup="false"` present, the `FileProvider` is `android:exported="false"` with a
narrow, dedicated `file_paths` resource (used only for a single profile-picture camera capture,
per its own comment). Grepped all of `app/src/main` for `service_role`, hardcoded production
credentials, and plaintext-password persistence: none found — Supabase credentials load only via
`BuildConfig` fields sourced from `local.properties`/env/Gradle properties
(`app/build.gradle.kts`), never literals in source. No sensitive value (password, token, raw
photo bytes) is passed to `Log.*` anywhere in the reviewed source. Scan photos are never
uploaded — `LocalAcneScanRepository` has no HTTP client at all, matching its own header comment,
and confirmed independently by the fact it only imports ONNX Runtime and Android graphics APIs.
Scan cache cleanup (`ScanViewModel.deleteIfOwnedScanCacheFile`) is scoped to files this app wrote
with the `weglow_scan_` prefix and cannot touch a gallery-picked `content://` file (tested).
Profile images live in a private Supabase Storage bucket accessed only via
`downloadAuthenticated`/per-user-folder paths (`SupabaseProfileImageRepository`), matching its
Phase 6 RLS-policy comment. Products are read via a plain `select()` with no write path anywhere
in `SupabaseCatalogRepository` — read-only from the client's perspective, consistent with the
brief. No security control was weakened to make any test pass.

## 15. Fake-data/user-trust audit

Grepped all of `app/src/main` for `Team 03`, `avatar_rukman`: **zero matches** — both Phase 10
fixes remain in place. No fake product names, fabricated acne detections, or hardcoded
recommendation concerns were found anywhere in production code — `RecommendationEngine` and the
full scan chain (§9) only ever operate on real `Product`/`AcneScanResult` data. No fabricated
"completed" routine/onboarding state was found beyond what is documented as Defects 1–4 (§3).

**What happened to the unsupported Home skin-score values:** the specific numbers Phase 10
identified and explicitly left in place (`74`, `+3`, `82%`/`68%`/`71%`) were **fixed in this
phase** (Defect 1, §3) — replaced with a neutral "no measurement yet" presentation that preserves
the card's visual structure without inventing a scoring algorithm. The same pattern was found and
fixed on Profile's "Skin Score" card and "Latest Scan" row (Defects 2–3), and on Discover's
featured-product card (Defect 4). The one remaining fabricated-personalization pattern in the
app — `InMemoryHairstyleRepository`'s per-hairstyle match percentages — is teammate-owned
face-shape work and was deliberately left untouched (§3, §21).

**"Team 03" reconciliation (user manual observation on emulator):** the user reported seeing
"Team 03" as the Routines greeting on a running emulator. This was re-audited end to end:

- A repo-wide grep (all `.kt`/`.xml`/`.json`, excluding `.git`/`build`) for the literal string
  `"Team 03"` returns **zero matches** in current production or resource source — the only hit
  anywhere is a code comment in `ProfileViewModelTest.kt` stating it must never be reintroduced.
- `RoutinesScreen`'s current greeting logic (`val greetingName =
  displayName?.trim()?.takeIf(String::isNotEmpty)`, feeding `"Good Morning, $greetingName"` /
  `"Good Morning"` with no name) sources the name from the real `ProfileViewModel` and has no
  hardcoded fallback identity anywhere in its branches.
- `git log --all -S "Team 03"` confirms the string was present only in this project's two
  earliest commits (`4ecfd2e`/`e00be4b`, "Initial WeGlow project") and was removed by the Phase
  10 hardening commit `32cc3a5` ("chore: harden security and improve app performance").
- The `app-debug.apk` produced by this session's own `assembleDebug` run (built from the current,
  clean `phase-11-testing` source) was inspected by timestamp and does not embed the string
  (no `Team 03` in the current source it was compiled from).

**Conclusion:** current source is clean; no runtime fallback, generated resource, or leftover
string produces "Team 03" anywhere in the reviewed code. The most likely explanation for the
emulator observation is that the emulator had an **older APK installed** from before the Phase 10
fix (`32cc3a5`) and was not reinstalled/updated from the current `phase-11-testing` build before
the observation was made. This cannot be fully closed by static analysis alone — a manual test
requirement has been added to `PHASE_11_MANUAL_TEST_MATRIX.md` (Test P3) requiring an install of
the **current** build and explicit visual confirmation that "Team 03" never appears anywhere,
including on Routines. No fake identity was reintroduced to "fix" this — there was nothing in
source to fix.

## 16. Production files changed

| File | Why |
|------|-----|
| `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt` | Defect 1: removed fabricated skin-score/hydration/clarity/texture values; render a neutral pending state instead. |
| `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt` | Defects 2–3: removed fabricated "84/100" skin score + "botanical glow index" claim and the fabricated "2 days ago · Hydration focused" scan history; also removed fabricated "4 items"/"3 items" routine-shelf counts (same fake-personalization pattern) in favor of a neutral "View steps" label. |
| `app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt` | Defect 4: removed the fabricated "98% Match" badge and the hardcoded "Recommended for your Dry Skin" header (wrong for any user whose real skin type isn't Dry) in favor of neutral "Featured"/"Featured Products" copy. |
| `app/src/main/java/com/example/weglow/ui/screens/RoutinesScreen.kt` | Defect 5: the per-step completion key now includes `selectedDay` (via `routineStepKey`) so completion checkmarks are independent per date instead of shared across every date; added an explicit code comment documenting that completion tracking is session-only (not persisted). |
| `app/src/main/java/com/example/weglow/feature/routine/RoutineCompletionKey.kt` (new file) | Defect 5: extracted the date/period/step/product → completion-key logic into one small, pure, unit-testable function, backing the `RoutinesScreen.kt` fix above. |

No other production file was modified. No teammate-owned face-shape file
(`HairstyleViewModel.kt`, `InMemoryHairstyleRepository.kt`, `HairstyleResultsScreen.kt`,
`domain/model/HairstyleRecommendation.kt`) was touched.

## 17. Tests added/updated

| File | Change |
|------|--------|
| `app/src/test/java/com/example/weglow/domain/recommendation/RecommendationEngineTest.kt` | Added 3 tests: `unrelatedConcern_doesNotMatchAndProductIsExcluded`, `moreThanTwelveMatches_areCappedAtTwelve`, `equalScoringProducts_tieBreakAlphabeticallyByName`. No existing test was modified. |
| `app/src/test/java/com/example/weglow/feature/routine/RoutineCompletionKeyTest.kt` (new file) | Added 7 tests covering Defect 5: distinct keys per day, per Morning/Evening, per step index, and for two different missing-product steps; a stable key for repeated calls; and two tests reproducing the reported scenario directly against a `Set<String>` (toggling one day's step does not affect another day's identical step; switching dates and back restores the original day's completion). |

No test was weakened, skipped, or given a fabricated pass. Defects 1–4 are UI-copy-only changes
in `@Composable` functions with no unit-testable business logic; they are covered by
`compileDebugKotlin`/`lintDebug`/`assembleDebug` succeeding and by manual Tests P1/P2 in
`PHASE_11_MANUAL_TEST_MATRIX.md`. Defect 5's underlying state-keying logic was extracted into a
pure function specifically so it *could* be unit-tested, rather than left as UI-only code.

## 18. Automated verification

All run against the actual project (JDK: Android Studio's bundled JBR 25, set as `JAVA_HOME`
for this session only — no project file was changed to achieve this). Re-run in full after the
Defect 5 fix:

- `architectureCheck`: **PASS**
- `compileDebugKotlin`: **PASS**
- `testDebugUnitTest`: **PASS** — **117/117** tests passing (107 baseline + 3 `RecommendationEngine`
  + 7 `RoutineCompletionKey`), 0 failures
- `lintDebug`: **PASS**
- `assembleDebug`: **PASS**

## 19. Manual tests still required

Everything in `docs/phase-11/PHASE_11_MANUAL_TEST_MATRIX.md` (IDs A1–P2) — none of it was run in
this session. In particular, the following were only verified by code inspection / unit tests at
their boundaries, not by exercising the real system:

- Real Supabase auth (signup/login/Google OAuth/session restore) against a live project.
- Real Supabase Storage profile-picture upload/download/replace/delete.
- Real Supabase Postgrest catalog fetch, including RLS behavior for read-only `products`.
- Real camera capture and gallery picker on a device/emulator, including permission-denied UX.
- Real on-device ONNX inference on an actual face photo (bundled-model correctness beyond the
  existing JVM smoke test and the `androidTest` synthetic-image test).
- Real Activity back-stack behavior after logout (K2) and repeated tab navigation (O1/O2).
- `ScanViewModel.setPhoto`'s cache-cleanup/stale-result behavior under real `content://`/`file://`
  URIs (N1) — logic verified by code reading only (§8), no JVM unit test exists for it.
- Visual confirmation that Defects 1–4's fixed copy renders correctly on-device (P1/P2).
- No-network graceful-failure behavior (M1–M4) against a real cut network path.
- Visual confirmation of the Defect 5 fix on-device: tapping between dates on Routines shows
  independent completion checkmarks per date, Morning/Evening stay independent, and switching
  back to a previously-checked date restores its checkmarks within the same app session (new
  manual Test Q1).
- A **clean install of the current `phase-11-testing` build**, to confirm "Team 03" does not
  appear anywhere (including Routines) now that the emulator has the current APK, not a stale
  pre-Phase-10 one (new manual Test P3 — see the Team 03 reconciliation in §15).

## 20. Manual test matrix location

`docs/phase-11/PHASE_11_MANUAL_TEST_MATRIX.md`

## 21. Remaining issues

**BLOCKING:** None found. No crash, data-loss, security, or correctness defect was found in any
reviewed path.

**NON-BLOCKING:**
- `ScanViewModel.setPhoto` has no JVM unit-test coverage (Robolectric would be required; judged
  disproportionate for this phase — see §2/§8/§19). Logic verified correct by inspection.
- `ScanPhotoDecoder` has no dedicated JVM unit test (inherently Android-framework-bound); its
  only coverage is the `androidTest` `OfflineAcneScanTest`. Additional device-level verification
  of large-image bounding and further EXIF orientations remains manual.
- `ProfileScreen`'s "Order History", "Saved Products", "Account Settings" rows and Discover's
  "Add to Bag" button are non-functional (`onClick = {}`) placeholders. Pre-existing, not a
  fake-data issue (they make no claim about data), and adding functionality is out of scope for
  a testing/hardening phase — flagged for a future phase, not fixed here.
- `AnalyzingScreen`'s hairstyle-mode message list contains a leftover ACNE-mode message branch
  ("Calculating skin score...") that is currently unreachable (the ACNE flow always renders
  `FigmaAcneAnalyzingScreen` instead). Dead code, no user-facing effect; left alone per
  no-unnecessary-refactor guidance.
- Routines completion tracking (post-Defect-5 fix) is **session-only by design**: it does not
  survive an app restart, and it is also cleared if the user navigates away from the Routines tab
  and back (plain `remember`, not `rememberSaveable` or a persisted store) — disclosed explicitly
  rather than fixed further, since adding cross-navigation or cross-restart persistence was not
  requested and would be new scope (state-restoration plumbing or a database) beyond "smallest
  honest behavior."

**TEAMMATE-OWNED:**
- `InMemoryHairstyleRepository.kt` hardcodes a `matchPercent` per hairstyle keyed only on gender,
  not on any real face-shape detection — the same fabricated-personalization pattern as Defects
  1–4, but explicitly out of scope per the instruction not to modify teammate-owned face-shape
  work. Flagged for the face-shape feature owner / Phase 12 to evaluate.

## 22. Is Phase 11 safe to commit?

**YES.** All changes remain minimal, additive-or-neutral: three UI files with pending-state text
substitutions (Defects 1–4), one UI file plus one new pure-logic file fixing a real state-keying
bug (Defect 5), two new test files (10 new tests total), and three docs. Fully covered by the
passing automated gate (117/117 tests, architecture/compile/lint/assemble all green), and
reviewed against every stated Phase 11 constraint (no redesign, no fake functionality, no
persistence invented, no weakened security, no teammate-owned edits beyond what was explicitly
excluded). No `git` command was run by Claude in this session.

## 23. Is Phase 11 ready to merge before manual testing?

**NO.** The manual device/emulator test matrix (§19/§20) has not been executed. Recommend running
Tests A1–P3 and Q1 (at minimum F1/G1/H1/I1/K1–K2/N1/P1–P3/Q1, which now also cover: real
on-device confirmation that Routines completion state is independent per date — the exact defect
originally reported by manual observation — and a clean install of the current build to close out
the "Team 03" reconciliation) before merging to `main`.
