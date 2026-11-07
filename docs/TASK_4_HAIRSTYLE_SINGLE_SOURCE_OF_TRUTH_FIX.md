# Task 4 — Hairstyle Recommendation Single Source of Truth

**Scope:** Ensure Supabase is the single authoritative source of hairstyle recommendations, and that the local face-shape classifier no longer independently maintains a second, discarded catalog of recommendations.

---

## 1. Root architectural problem confirmed

`LocalHairstyleRepository` computed a complete second hairstyle catalog (`maleStyles()` / `femaleStyles()`, ranked via hardcoded `FaceShapeProfile.maleOrder` / `femaleOrder` and fixed match-percent scores) on every scan. `SupabaseHairstyleRepository` then unconditionally discarded that catalog via `classified.copy(recommendations = ...)`. Two sources of hairstyle-recommendation data existed; only Supabase's ever reached the UI, but the local one was still built on every scan (wasted work) and represented a real duplicate source of truth.

## 2. Architecture before

```
LocalHairstyleRepository.analyze()
  → classify face shape
  → recommendationResult(): build FULL result incl. 4 hardcoded HairstyleRecommendation
    cards (title/category/description/drawable/matchPercent)

SupabaseHairstyleRepository.analyze()
  → calls classifier (above) → gets classified.recommendations (discarded)
  → persists face shape, re-reads profile
  → queries Supabase, maps rows
  → classified.copy(recommendations = supabaseRows)   // local catalog thrown away
```

## 3. Architecture after

```
LocalHairstyleRepository.analyze()
  → classify face shape
  → recommendationResult(): returns faceShape/confidence + descriptive traits/description
    ONLY, recommendations = emptyList()

SupabaseHairstyleRepository.analyze()  (orchestration logic unchanged)
  → calls classifier → gets classified (recommendations already empty)
  → persists face shape, re-reads profile
  → queries Supabase, maps rows
  → classified.copy(recommendations = supabaseRows)   // Supabase is the sole source
```

## 4. Files modified

- `app/src/main/java/com/example/weglow/data/repository/LocalHairstyleRepository.kt`
- `app/src/test/java/com/example/weglow/data/repository/HairstyleRecommendationCatalogTest.kt`
- `app/src/androidTest/java/com/example/weglow/OfflineHairstyleScanTest.kt`

`SupabaseHairstyleRepository.kt` required no changes — it already treated `classified.recommendations` as disposable, so removing the local catalog is transparent to it.

## 5. Hardcoded/duplicate recommendation logic removed

- `maleStyles()` and `femaleStyles()` — 4 hardcoded `HairstyleRecommendation` entries each, referencing bundled drawable resources.
- The `maleOrder` / `femaleOrder` / `scores` ranking logic previously on `FaceShapeProfile`.

`recommendationResult()` no longer builds any recommendation list — it returns `emptyList()` and leaves population of that field entirely to Supabase.

## 6. Local ML output that remains

- Detected `faceShape`
- `confidencePercent`
- Per-face-shape descriptive `traits` / `description` text (used by `HairstyleResultsScreen`'s "Your X Face Shape" card)

None of this is hairstyle-recommendation data — it describes the classified face shape itself — so it correctly stays local and untouched.

## 7. Confirmation that Supabase is now the only recommendation source

`HairstyleResult.recommendations` is populated in exactly one place: `SupabaseHairstyleRepository.analyze()`, from the Supabase query result. The local classifier can no longer independently produce hairstyle cards.

## 8. Tests added/updated

- **`HairstyleRecommendationCatalogTest.kt`** — rewritten to assert:
  - Local classification returns the correct face shape and confidence.
  - Local classification produces **zero** recommendations for every face shape / gender combination (proving no competing catalog exists).
  - Traits and description are still generated correctly per face shape.
- **`OfflineHairstyleScanTest.kt`** — updated the on-device instrumentation test to assert `recommendations.size == 0` instead of `4`.
- **`SupabaseHairstyleRepositoryMappingTest.kt`** (pre-existing, unchanged) — already covers Supabase row mapping and confirms zero matching rows (`matchingHairstyles(rows, "Square", null)`) produce an empty list rather than fallback data.

## 9. Gradle gate results

JAVA_HOME set to Android Studio's bundled JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) — no system Java on `PATH`; environment note, not a code change.

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

All four ran together in one invocation; all tasks completed successfully, no failures.

## 10. Remaining architectural issues intentionally deferred

- `HairstyleRecommendation` domain model still carries an Android-specific `imageRes: Int` field — documented here, not fixed, since resolving this is a separate, later architecture task.
- The 8 bundled drawables (`hairstyle_male_*`, `hairstyle_female_*`) are now unused dead assets. Left in place since deleting APK resources wasn't requested and is a separate cleanup.
- Repository-wide error-handling cleanup, TFLite lifecycle cleanup, and the ScanScreen state-machine refactor — explicitly out of scope per task instructions, not touched.

## 11. Confirmation of scope

`git status` shows only the three files above touched by this task (plus a pre-existing, unrelated `docs/ARCHITECTURE_REVIEW.md` modification that predates this session). No UI redesign, no Supabase schema/migration changes, no unrelated scan/acne code touched, and no Git operations (commit/push) were performed.

---

**Status:** Task 4 complete. Implementation and verification finished; stopped for manual testing per instructions.
