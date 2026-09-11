# PHASE 11 — Full Application Testing Report (Summary)

> The full, detailed report is `docs/phase-11/PHASE_11_TEST_REPORT.md`. The manual device test
> matrix is `docs/phase-11/PHASE_11_MANUAL_TEST_MATRIX.md`. This file is the condensed summary.
>
> **Addendum:** this version supersedes the original draft, which incorrectly stated no routines
> correctness defect existed. A user manual observation (Routines date selector appearing to do
> nothing meaningful) was investigated end-to-end and confirmed as a real defect — see Defect 5
> below. A "Team 03" observation was also reconciled.

## 1. Overall verdict

**PASS WITH MANUAL VERIFICATION REQUIRED**

## 2. Test inventory

- Before Phase 11: **107**
- After Phase 11 (final, including this addendum): **117**
- New: **10** total — 3 in `RecommendationEngineTest` (12-result cap, alphabetical tie-break,
  unrelated-concern exclusion) + 7 in the new `RoutineCompletionKeyTest` (Defect 5)
- Updated: **0** existing test bodies changed

## 3. Defects discovered

**Defect 5 (this addendum) — Routines date selector's completion state was shared across every
date.**

- **Root cause:** the per-step "done" checkbox key built in `RoutinesScreen.kt` was
  `"${AM|PM}-$stepIndex-${product?.id}"` — it never included the selected date. Every date
  therefore mapped to the *same* key, so marking a step done on any date marked it done on all of
  them. The date chips themselves worked correctly (tapping did update `selectedDay`, and the
  tapped chip did visually highlight) — the audit had to go one level deeper than the visible
  chip state to find the real bug in the completion tracking.
- **Exact final date behavior:** the routine *product template* (which items appear under
  Cleanser/Moisturizer/Sunscreen/Treatment) intentionally still does **not** change per date —
  that is correct per the product requirement. What is now date-specific is completion: each
  date has its own independent set of checked/unchecked steps. Morning and Evening were already
  independent of each other (separate key prefix) and remain so.
- **Session-only or persisted?** **Session-only.** No routine-history table or database was
  added — none should be, per instructions. Completion state lives only in in-memory Compose
  state for as long as the Routines screen instance is alive; switching between dates within that
  time correctly preserves each date's own checkmarks, but closing the app, or even navigating
  away from the Routines tab and back, clears all of it back to empty (disclosed explicitly, not
  fixed further — that would require state-restoration or persistence work beyond this fix's
  scope).
- **Morning/evening behavior:** unchanged and already correct — verified independent both before
  and after the fix.
- **Fix:** extracted a pure `routineStepKey(day, isMorning, stepIndex, productId)` function
  (new file `feature/routine/RoutineCompletionKey.kt`) that folds the selected date into the key;
  `RoutinesScreen.kt` now calls it instead of building the key inline.
- **Tests added:** 7 new tests in `RoutineCompletionKeyTest.kt`, including two that directly
  reproduce the reported scenario against a real `Set<String>` (toggling a step on one date does
  not affect another date's identical step; switching dates and back restores the original
  date's completion).

**Defects 1–4 (from the original pass, unchanged):** Home/Profile skin-score cards and
Profile's "Latest Scan" row showed fabricated numbers/history as if genuine; Discover's featured
card claimed a fake match percentage and a fixed, usually-wrong skin type. All four were fixed
with neutral, structure-preserving copy — see the full report for detail.

`InMemoryHairstyleRepository`'s hardcoded per-hairstyle match percentages are the same
fake-personalization pattern but are **teammate-owned face-shape code and remain untouched**.

## 4–14. Feature area results

Unchanged from the original pass — auth, onboarding, profile, catalog, scan pipeline,
scan→recommendation contract, `RecommendationEngine`, navigation, failure states, and
security/privacy were all read end-to-end against their existing tests with no defects found.
`RoutineBuilder`/`RoutineViewModel` (the product routine template) were confirmed correct and
unchanged; the newly found defect was isolated entirely to `RoutinesScreen`'s own UI-local
date/completion state, which has no ViewModel and was not originally in scope of that check.

## 7. Team 03 reconciliation

- Repo-wide grep for `"Team 03"` (all `.kt`/`.xml`/`.json`, excluding `.git`/`build`): **zero
  matches** in current source — the only hit is a test comment stating it must never reappear.
- `git log --all -S "Team 03"` confirms the string existed only in the two earliest commits and
  was removed by the Phase 10 hardening commit `32cc3a5`.
- `RoutinesScreen`'s greeting logic sources the name from the real `ProfileViewModel`, with a
  neutral no-name fallback — no hardcoded identity anywhere.
- **Conclusion:** current source is clean. The emulator sighting is most likely a **stale,
  pre-Phase-10 APK** that was never reinstalled from the current `phase-11-testing` build.
  Added manual **Test P3**: uninstall any existing build, install the current build fresh, and
  confirm "Team 03" never appears (Routines greeting uses the real profile name or a neutral
  fallback). No fake identity was reintroduced — there was nothing in source to fix.

## 15. Fake-data audit (updated)

- `Team 03` / `avatar_rukman`: zero matches (see §7 above for the full reconciliation).
- Home/Profile skin-score fabrications: fixed (Defects 1–3).
- Discover featured-card fabrication: fixed (Defect 4).
- Routines completion state fabrication risk (data looked "shared/fake" across dates): this
  turned out to be a **state-keying bug**, not fabricated data being displayed — no false
  "completed" claim was ever shown, but the *lack* of per-date distinction made the feature
  functionally meaningless, which is now fixed (Defect 5).

## 16. Production files changed

- `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/RoutinesScreen.kt` (Defect 5 fix)
- `app/src/main/java/com/example/weglow/feature/routine/RoutineCompletionKey.kt` (new file,
  Defect 5 fix)

## 17. Tests added

- 3 tests in `RecommendationEngineTest.kt` (original pass)
- 7 tests in the new `RoutineCompletionKeyTest.kt` (this addendum, Defect 5)
- **10 new tests total; 0 existing tests modified**

## 18. Automated verification (re-run after the Defect 5 fix)

| Gate | Result |
|------|--------|
| `architectureCheck` | **PASS** |
| `compileDebugKotlin` | **PASS** |
| `testDebugUnitTest` | **PASS** — **117/117**, 0 failures |
| `lintDebug` | **PASS** |
| `assembleDebug` | **PASS** |

## 19. Manual tests still required

Everything involving live Supabase, real camera/gallery hardware, real on-device ONNX inference,
and real Activity back-stack behavior — unchanged in nature, now 27 test IDs total (added P3 and
Q1). Full list is in `docs/phase-11/PHASE_11_MANUAL_TEST_MATRIX.md`. Of particular note for this
addendum:
- **Q1** — confirms the Defect 5 fix on-device: independent completion per date, within one
  continuous visit to the Routines screen.
- **P3** — confirms "Team 03" cannot appear on a freshly installed current build.

## 20. Manual test matrix location

`docs/phase-11/PHASE_11_MANUAL_TEST_MATRIX.md`

## 21. Remaining issues

**BLOCKING:** None.

**NON-BLOCKING:**
- `ScanViewModel.setPhoto` has no JVM unit test (would require adding Robolectric, judged
  disproportionate for this phase) — logic verified correct by code reading instead.
- A few decorative no-op UI rows (Order History, Saved Products, Account Settings, Add to Bag)
  are incomplete features, not fake-data issues — left alone as out of scope for a
  testing/hardening phase.
- Routines completion state is session-only by design and is also cleared on navigating away
  from the Routines tab (not just app restart) — disclosed, not fixed further (would need
  `rememberSaveable`/persistence, which is new scope).

**TEAMMATE-OWNED:**
- `InMemoryHairstyleRepository`'s fabricated per-hairstyle match percentages (same
  fake-personalization pattern as Defects 1–4, deliberately not touched).

## 22. Is Phase 11 safe to commit?

**YES.** Scope remains minimal (4 UI files + 1 new pure-logic file, 2 test files with 10 new
tests, 3 docs) and fully covered by a green build gate (117/117 tests). No `git` command was run
in this session.

## 23. Is Phase 11 ready to merge before manual testing?

**NO.** The device/emulator test matrix has not been executed yet. Recommend running at minimum
Tests F1, G1, H1, I1, K1–K2, N1, P1–P3, and Q1 before merging to `main` — P3 and Q1 specifically
close out the two issues raised in this addendum.
