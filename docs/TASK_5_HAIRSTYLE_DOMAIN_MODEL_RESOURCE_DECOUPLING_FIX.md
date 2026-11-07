# Task 5 — Remove Android Resource Dependency From Hairstyle Domain Model

**Scope:** Remove the Android drawable/resource-ID field from the hairstyle domain model (`docs/ARCHITECTURAL_VIOLATIONS.md`'s deferred Task 4 item) so the domain layer describes hairstyle data only, never Android presentation resources.

---

## 1. Root architectural violation confirmed

`HairstyleRecommendation.imageRes: Int = 0` in `domain/model/HairstyleRecommendation.kt` — a domain field whose only historical purpose was to carry an `R.drawable.*` resource ID from the local hardcoded hairstyle catalog that Task 4 already deleted. After Task 4, nothing ever assigned this field to anything but its default `0`, and nothing ever read it.

## 2. Architecture before

```kotlin
data class HairstyleRecommendation(
    val matchPercent: Int,
    val category: String,
    val title: String,
    val description: String,
    val imageRes: Int = 0,       // Android drawable resource ID — dead since Task 4
    val imageUrl: String? = null,
)
```

## 3. Architecture after

```kotlin
data class HairstyleRecommendation(
    val matchPercent: Int,
    val category: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
)
```

The domain model now contains only hairstyle data fields. Image rendering and any presentation fallback (already `painterResource(R.drawable.weglow_logo)` inside `HairstyleResultsScreen.kt`'s `HairstyleCard`) remain entirely inside the UI layer, unchanged.

## 4. Exact files modified

- `app/src/main/java/com/example/weglow/domain/model/HairstyleRecommendation.kt` — removed the `imageRes` field.
- `app/src/test/java/com/example/weglow/domain/model/HairstyleRecommendationTest.kt` (new) — guards the invariant going forward.

No other production file required changes: `SupabaseHairstyleRepository.toHairstyleRecommendationOrNull()` never assigned `imageRes` (it used named arguments and relied on the now-removed default), and `HairstyleResultsScreen.kt`'s `HairstyleCard` never read `style.imageRes` — it already branched on `style.imageUrl` with its own UI-layer fallback.

## 5. Domain field removed

`imageRes: Int` (default `0`) — an Android drawable resource ID.

## 6. All `imageRes` / hairstyle-related `R.drawable` usages, classified

Post-implementation search results:

| Match | Classification |
|---|---|
| `domain/model/HairstyleRecommendation.kt` | **Removed** — no longer exists |
| `SupabaseHairstyleRepository.toHairstyleRecommendationOrNull()` | Never referenced `imageRes` — n/a, unaffected |
| `HairstyleResultsScreen.kt` | Never referenced `style.imageRes`; uses `style.imageUrl` + `painterResource(R.drawable.weglow_logo)` fallback — **legitimate presentation-layer usage**, unrelated to the domain model, untouched |
| `ui/screens/SkinTypeScreen.kt` (`imageRes` field/usage) | **Unrelated feature** (skin-type option cards) — not hairstyle, not domain layer, out of scope |
| `ui/screens/ProfileScreen.kt` (`imageRes` field/usage) | **Unrelated feature** (profile "shelf" cards) — not hairstyle, not domain layer, out of scope |
| `app/src/test/.../HairstyleRecommendationTest.kt` | New test asserting the field name `imageRes` does **not** exist on the class — intentional, guards the invariant |
| Hairstyle drawable assets (`hairstyle_male_*`, `hairstyle_female_*`) | **Dead resources** left in place — deletion not required for compilation and explicitly out of scope per task instructions |

No `R.drawable.hairstyle*` reference exists anywhere in the codebase (confirmed by search — zero matches).

**There is no remaining Android drawable dependency inside the hairstyle domain model.**

## 7. Confirmation Supabase `imageUrl` still works

Unchanged. `toHairstyleRecommendationOrNull()` still maps `image_url` from `public.hairstyles` into `HairstyleRecommendation.imageUrl`, and `HairstyleResultsScreen.kt` still renders it via Coil's `AsyncImage` exactly as before. `SupabaseHairstyleRepositoryMappingTest.kt` (unchanged) still passes, asserting `style?.imageUrl` maps correctly.

## 8. Confirmation Task 2 (server-side filtering) remains intact

Untouched. `SupabaseHairstyleRepository`'s `filter { ilike("face_shape", ...); or { ilike("gender", ...) ... } }` block, `genderSynonyms`, and `matchingHairstyles` are unmodified.

## 9. Confirmation Task 3 (DI) remains intact

Untouched. `SupabaseHairstyleRepository` still receives `classifier: HairstyleRepository` via constructor injection from `AppContainer`; it does not construct `LocalHairstyleRepository` itself.

## 10. Confirmation Task 4 (single source of truth) remains intact

Untouched. `LocalHairstyleRepository.recommendationResult()` still returns `recommendations = emptyList()`; Supabase remains the only place `HairstyleResult.recommendations` is populated.

## 11. Tests added/updated

- **`HairstyleRecommendationTest.kt`** (new, `domain/model` package):
  - Constructs a `HairstyleRecommendation` from data fields only (no Android resource ID) and verifies `title`/`imageUrl`.
  - Asserts via reflection that the class has no `imageRes` field, guarding against the violation being reintroduced.
- Existing hairstyle tests (`SupabaseHairstyleRepositoryMappingTest`, `HairstyleRecommendationCatalogTest`, `HairstyleViewModelTest`, `OfflineHairstyleScanTest`) required no changes — none referenced `imageRes` — and all still pass, confirming Supabase mapping, zero-row handling, local-classification-produces-no-recommendations (Task 4), and ViewModel behavior are all unaffected.

## 12. Gradle gate results

`JAVA_HOME` set to Android Studio's bundled JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

All four ran together in one invocation with no failures.

## 13. Remaining architectural issues intentionally deferred

- Repository-wide error-handling/error-contract cleanup — not performed, per task scope.
- TFLite `Interpreter` lifecycle cleanup — not performed, per task scope.
- ScanScreen state-machine refactor — not performed, per task scope.
- Unused bundled hairstyle drawable assets (`hairstyle_male_*`, `hairstyle_female_*`) — left in place; deletion wasn't required for compilation and wasn't the goal of this task.

## 14. Confirmation of no UI redesign / database migration / unrelated feature changes

No Compose UI was redesigned (`HairstyleResultsScreen.kt` was not modified — its existing `imageUrl`/fallback rendering already matched the target architecture). No Supabase schema change, no migration created. `SkinTypeScreen.kt` and `ProfileScreen.kt` (unrelated `imageRes` usages) were not touched. No Git operations were performed.

## 15. `git status` summary

```
 M app/src/main/java/com/example/weglow/domain/model/HairstyleRecommendation.kt
 M docs/ARCHITECTURE_REVIEW.md   (pre-existing modification from before this session — not touched by this task)
?? app/src/test/java/com/example/weglow/domain/model/HairstyleRecommendationTest.kt
?? docs/TASK_5_HAIRSTYLE_DOMAIN_MODEL_RESOURCE_DECOUPLING_FIX.md
```

(Plus other pre-existing untracked docs from earlier sessions, unrelated to this task and left untouched.)

---

**Status:** Task 5 complete. Implementation and verification finished; stopped for manual testing per instructions. Not committed, not pushed.
