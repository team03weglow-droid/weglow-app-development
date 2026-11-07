# Task 6 — Standardize Scan / Hairstyle Repository Error Handling

**Scope:** Replace throwing, presentation-text-carrying exceptions in the acne and hairstyle scan repository paths with a typed, presentation-independent failure contract, mapped to UI copy only inside the ViewModels.

---

## 1. Root inconsistency confirmed

`AcneScanRepository.analyze()` and `HairstyleRepository.analyze()` both threw exceptions instead of returning `Result<T>` like the rest of the codebase's repositories (`AuthRepository`, `ProfileRepository`, `CatalogRepository`, `ProfileImageRepository`). Worse, the data layer embedded literal user-facing sentences directly in `IllegalStateException` messages (e.g. `"Sign in to save your face shape and see hairstyle recommendations."`, `"The installed skin analysis model is incompatible."`), and both `ScanViewModel` and `HairstyleViewModel` read `error.message` straight into UI state for anything typed `IllegalStateException` — meaning any new data-layer exception's wording automatically became user-visible text with no ViewModel review.

## 2. Contracts before

```kotlin
interface AcneScanRepository {
    suspend fun analyze(photoReference: String): AcneScanResult   // throws
}

interface HairstyleRepository {
    suspend fun analyze(photoReference: String, gender: String?): HairstyleResult   // throws
}
```

`LocalAcneScanRepository`/`LocalHairstyleRepository` threw bare `IOException` (uncaught, from `ScanPhotoDecoder`), `IllegalStateException` wrapping `OutOfMemoryError`/`LinkageError` with hardcoded text, and bare `IllegalStateException` from `check(...)` model-shape validations (also uncaught). `SupabaseHairstyleRepository` additionally threw `IllegalStateException` with hardcoded text for six distinct data/auth failure modes. Both ViewModels consumed these via `is IOException ->`, `is IllegalStateException -> error.message`, `else ->`.

The existing `Result<T>`-based repositories were inspected for convention: they use plain `runCatching { }` returning `Result<T>` of a raw `Throwable`, with **no typed classification** — their own ViewModel (`AuthViewModel`) still reads `error.message ?: fallback`. Copying that convention as-is would not satisfy this task's "no raw `exception.message` to UI" requirement, so scan/hairstyle needed typed failures riding *inside* `Result`, not a bare `Throwable`. `FaceValidator`/`FaceImageValidator` (already typed, flat `sealed interface FaceValidationResult` with mapping kept separate) confirmed this pattern already exists in the codebase and was reused conceptually — that file itself was left untouched, since it is out of scope.

## 3. Chosen error contract and why

Two small flat `sealed interface`s carried inside `Result<T>` via a tiny wrapper exception:

```kotlin
sealed interface ScanFailure {          // domain/model/ScanFailure.kt
    data object InvalidImage : ScanFailure
    data object DeviceOutOfMemory : ScanFailure
    data object ModelUnavailable : ScanFailure
    data object Unknown : ScanFailure
}
class ScanFailureException(val failure: ScanFailure, cause: Throwable? = null) : Exception(failure.toString(), cause)

sealed interface HairstyleFailure {     // domain/model/HairstyleFailure.kt
    data object InvalidImage : HairstyleFailure
    data object DeviceOutOfMemory : HairstyleFailure
    data object ModelUnavailable : HairstyleFailure
    data object NotSignedIn : HairstyleFailure
    data object ProfileUnavailable : HairstyleFailure
    data object RecommendationsUnavailable : HairstyleFailure
    data object Unknown : HairstyleFailure
}
class HairstyleFailureException(val failure: HairstyleFailure, cause: Throwable? = null) : Exception(failure.toString(), cause)
```

This is the task's own first "acceptable example" (`Result<T>` with a typed internal exception wrapper mapped before presentation) and is the smallest coherent solution because:
- It reuses the `Result<T>` shape every other repository in the app already returns — no new generic framework, no new `RepositoryResult<T, E>` type.
- Each failure enum is a flat `sealed interface` mirroring the existing `FaceValidationResult` convention exactly, instead of inventing a composed/generic hierarchy.
- The wrapper exception is the only new mechanism, and it exists solely so a typed value can travel through `kotlin.Result`'s `Throwable`-only failure slot.
- Two separate enums (rather than one shared type) avoid nested `when` matching; the ~3 duplicated cases (`InvalidImage`/`DeviceOutOfMemory`/`ModelUnavailable`) are trivial and far cheaper than a composition layer, since Acne and Hairstyle otherwise have no overlapping failure vocabulary (hairstyle alone has auth/profile/backend failure modes).

## 4. Contracts after

```kotlin
interface AcneScanRepository {
    suspend fun analyze(photoReference: String): Result<AcneScanResult>
}

interface HairstyleRepository {
    suspend fun analyze(photoReference: String, gender: String?): Result<HairstyleResult>
}
```

Both `Local*Repository` implementations now classify every catch site into a typed failure instead of throwing text. `SupabaseHairstyleRepository` returns `Result.failure(HairstyleFailureException(...))` at each of its six failure sites instead of throwing, and propagates the classifier's own `Result` failure unchanged. Both ViewModels `fold()` the `Result` and map the typed failure to UI copy in a private, ViewModel-owned function; an outer `catch (Exception)` remains only as a last-resort safety net (a misbehaving repository that throws instead of returning `Result.failure`), always collapsing to the `Unknown` case — never reading `.message`.

## 5. Exact files modified

New:
- `app/src/main/java/com/example/weglow/domain/model/ScanFailure.kt`
- `app/src/main/java/com/example/weglow/domain/model/HairstyleFailure.kt`

Modified:
- `app/src/main/java/com/example/weglow/domain/repository/AcneScanRepository.kt`
- `app/src/main/java/com/example/weglow/domain/repository/HairstyleRepository.kt`
- `app/src/main/java/com/example/weglow/data/repository/LocalAcneScanRepository.kt`
- `app/src/main/java/com/example/weglow/data/repository/LocalHairstyleRepository.kt`
- `app/src/main/java/com/example/weglow/data/repository/SupabaseHairstyleRepository.kt`
- `app/src/main/java/com/example/weglow/feature/scan/ScanViewModel.kt`
- `app/src/main/java/com/example/weglow/feature/hairstyle/HairstyleViewModel.kt`
- `app/src/test/java/com/example/weglow/feature/scan/ScanViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/hairstyle/HairstyleViewModelTest.kt`
- `app/src/androidTest/java/com/example/weglow/OfflineAcneScanTest.kt`
- `app/src/androidTest/java/com/example/weglow/OfflineHairstyleScanTest.kt`

**Untouched (verified unnecessary):** `FaceImageValidator.kt` (already typed correctly, out of scope), `ScanScreen.kt`/`HairstyleResultsScreen.kt` (already only read `state.error: String?`/`state.validationError`, never inspected exceptions — confirmed by search before editing), `AppContainer.kt` (constructor wiring unaffected by a return-type change), `SupabaseHairstyleRepositoryMappingTest.kt`, `HairstyleRecommendationCatalogTest.kt`, `BundledHairstyleModelTest.kt`, `BundledAcneModelTest.kt`, `domain/model/HairstyleRecommendation.kt`.

## 6. Typed failures introduced/reused

- `ScanFailure`: `InvalidImage`, `DeviceOutOfMemory`, `ModelUnavailable`, `Unknown`.
- `HairstyleFailure`: `InvalidImage`, `DeviceOutOfMemory`, `ModelUnavailable`, `NotSignedIn`, `ProfileUnavailable`, `RecommendationsUnavailable`, `Unknown`.
- `FaceValidationResult` (existing, in `core/image/FaceImageValidator.kt`) — reused conceptually as the precedent for this design; not modified.

## 7. Data-layer user-facing strings removed

All of the following are no longer thrown as exception text from the data layer (classification now happens via typed failures instead):

- `LocalAcneScanRepository`: `"There is not enough memory to analyze this photo..."`, `"Skin analysis could not start on this device..."`, plus the two `check(...)` messages (`"...model is incompatible."`).
- `LocalHairstyleRepository`: the equivalent OOM/Linkage messages, plus the two `check(...)` messages (`"...model is incompatible."` / `"...returned an invalid result."`).
- `SupabaseHairstyleRepository`: `"Sign in to save your face shape..."`, `"Could not save your face shape..."`, `"Could not load your profile..."`, `"Your profile is unavailable..."`, `"Could not confirm your saved face shape..."`, `"Hairstyle recommendations are temporarily unavailable."` (×2 sites), `"Hairstyles are unavailable. Please check database read access."`.

The equivalent user-facing copy still exists, but now only inside each ViewModel's private `toUserMessage()` mapping function (presentation layer), never inside a data-layer exception.

## 8. ViewModel failure mapping added/changed

Both `ScanViewModel` and `HairstyleViewModel` now:
1. Call `repository.analyze(...)` and get back `Result<T>`.
2. `.fold(onSuccess, onFailure)` it — success publishes the result as before; failure unwraps `(error as? XFailureException)?.failure ?: XFailure.Unknown` and maps that to UI copy via a private `toUserMessage()` function.
3. Keep a defensive outer `catch (Exception)` (after the `CancellationException` passthrough) that also collapses to `XFailure.Unknown.toUserMessage()` — this exists only to guard against a repository implementation that throws instead of returning `Result.failure`, and is covered by a dedicated test in each ViewModel's test suite.

`HairstyleViewModel` additionally preserves its pre-existing `wasValidating` branch (an exception from `faceValidator.validate()` itself still surfaces as `FaceValidationResult.ProcessingError`, unchanged from before this task).

## 9. Acne flow preservation

`ScanViewModel`'s public API (`setPhoto`, `analyze`, `analyzeReference`, `cancelAnalysis`, `clear`) and `ScanUiState` shape are unchanged. `ScanScreen.kt` was not touched — it already only reads `ScanUiState.error: String?`. The acne flow (camera/gallery → analyze → real detection result) is behaviorally identical; only the repository's internal failure classification changed.

## 10. Hairstyle flow preservation

`HairstyleViewModel`'s public API and `HairstyleUiState` shape are unchanged. `HairstyleResultsScreen.kt` was not touched. The full flow (face validation → local classification → profile face_shape update → server-filtered Supabase recommendations → results UI) runs through the same sequence of calls as before; only failure propagation changed from throw-with-text to typed `Result`.

## 11. Confirmation zero Supabase matches remain successful empty result

`SupabaseHairstyleRepository.analyze()` only returns `Result.failure(...)` from three sites: the classifier's own failure, `NotSignedIn`, `ProfileUnavailable` (×3 conditions), the Supabase query's own `catch (Exception)`, and `ensureHairstylesTableIsReadable()` when the *entire* table is unreadable/empty. When the query succeeds but returns zero rows for this specific face-shape/gender combination, `ensureHairstylesTableIsReadable()` succeeds (the table has rows, just none matching), and the function falls through to `Result.success(classified.copy(recommendations = emptyList()))` — a legitimate empty result, not an error. This is unchanged in structure from the pre-Task-6 code (which had the identical "empty rows ≠ failure" comment and logic) — only the return mechanism (throw vs. `Result`) changed. `SupabaseHairstyleRepositoryMappingTest.matchingHairstyles(rows, "Square", null).isEmpty()` continues to pass, confirming the pure-function half of this behavior.

## 12. Tests added/updated

**`ScanViewModelTest.kt`:**
- Updated the `repository(...)` fake to wrap successes in `Result.success`.
- Added `repositoryResult(...)` fake for tests that need to return `Result` directly.
- Rewrote `failedScanCanBeRetriedWithoutStaleResults` to use `Result.failure(ScanFailureException(ScanFailure.InvalidImage))` and assert the exact deterministic message (previously a loose `contains("photo")` check).
- Added `eachTypedFailureMapsToItsOwnDeterministicMessage` — exhaustively asserts every `ScanFailure` case maps to its intended, distinct UI message.
- Added `unexpectedRepositoryExceptionNeverExposesRawMessage` — a repository that *throws* a raw exception with a secret string; asserts the UI message never contains it.
- Added `repositoryFailureResultAlsoNeverExposesRawMessage` — a repository that *returns* `Result.failure(RuntimeException(secret))` (not a typed exception); asserts the same.

**`HairstyleViewModelTest.kt`:** the same four categories of additions/rewrites, using `HairstyleFailure`/`HairstyleFailureException`, plus verifying the `RecommendationsUnavailable` retry path.

**`OfflineAcneScanTest.kt` / `OfflineHairstyleScanTest.kt`** (instrumentation): updated the direct repository call sites to `.getOrThrow()` since `analyze()` now returns `Result<T>`.

**Unchanged and still passing (verified, not modified):** `SupabaseHairstyleRepositoryMappingTest`, `HairstyleRecommendationCatalogTest` (Task 4 invariant — local classification produces zero recommendations — re-verified untouched), `BundledHairstyleModelTest`, `BundledAcneModelTest`, `HairstyleRecommendationTest` (Task 5).

## 13. Search audit results for raw exception messages

Post-implementation search of the scan/hairstyle data and presentation files for `error.message`, `Throwable.message`, `throw IllegalStateException`/`IllegalArgumentException` with copy, and any `.message` read in `ScanScreen.kt`/`HairstyleResultsScreen.kt`:

| Pattern | Result |
|---|---|
| `error.message` in `Local*Repository`/`SupabaseHairstyleRepository`/`ScanViewModel`/`HairstyleViewModel` | **Zero matches** |
| `throw IllegalStateException` / `throw IllegalArgumentException` with copy, in the three repository files | **Zero matches** |
| `.message` read anywhere in `ScanScreen.kt` / `HairstyleResultsScreen.kt` | **Zero matches** |
| `Throwable` matches | Only the two safe `Throwable.toXFailure()` mapping-function declarations/doc comments — these classify a throwable into a typed enum and never read `.message` |

**There is no scan/hairstyle UI or ViewModel depending on raw data-layer exception messages.**

## 14. Gradle gate results

`JAVA_HOME` set to Android Studio's bundled JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** (`ScanViewModelTest`: 9/9 passing; `HairstyleViewModelTest`: 8/8 passing; full suite green) |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

All four ran together in one invocation with no failures.

## 15. Tasks 2–5 regression confirmation

- **Task 2** (server-side filtering): `filter { ilike("face_shape", ...); or { ... } }` block in `SupabaseHairstyleRepository` is unchanged, verified present.
- **Task 3** (DI): `SupabaseHairstyleRepository`'s constructor still takes `classifier: HairstyleRepository` as a parameter; it does not construct `LocalHairstyleRepository` internally, verified present.
- **Task 4** (single source of truth): `LocalHairstyleRepository.recommendationResult()` still returns `recommendations = emptyList()`, verified present; `HairstyleRecommendationCatalogTest` (unmodified) still passes.
- **Task 5** (no `imageRes`): `domain/model/HairstyleRecommendation.kt` was not touched by this task; confirmed no `imageRes` field present.

## 16. Remaining architectural issues intentionally deferred

- ScanScreen state-machine refactor — not performed, per task scope.
- TFLite `Interpreter` / ONNX `OrtSession` lifecycle cleanup — not performed, per task scope.
- `AppContainer` lifecycle changes — not performed; no changes were needed since constructor signatures didn't change.
- Broad repository-wide adoption of this typed-failure pattern outside acne/hairstyle (e.g. `AuthViewModel`'s existing `error.message` usage) — explicitly out of scope; noted as a pre-existing, separate inconsistency in the investigation but not touched.
- The remaining Android-drawable-shaped fields flagged in Task 5's deferred list — unrelated to this task, untouched.

## 17. `git status` summary

```
 M app/src/androidTest/java/com/example/weglow/OfflineAcneScanTest.kt
 M app/src/androidTest/java/com/example/weglow/OfflineHairstyleScanTest.kt
 M app/src/main/java/com/example/weglow/data/repository/LocalAcneScanRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/LocalHairstyleRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/SupabaseHairstyleRepository.kt
 M app/src/main/java/com/example/weglow/domain/repository/AcneScanRepository.kt
 M app/src/main/java/com/example/weglow/domain/repository/HairstyleRepository.kt
 M app/src/main/java/com/example/weglow/feature/hairstyle/HairstyleViewModel.kt
 M app/src/main/java/com/example/weglow/feature/scan/ScanViewModel.kt
 M app/src/test/java/com/example/weglow/feature/hairstyle/HairstyleViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/scan/ScanViewModelTest.kt
 M docs/ARCHITECTURE_REVIEW.md   (pre-existing modification from before this session — not touched by this task)
?? app/src/main/java/com/example/weglow/domain/model/HairstyleFailure.kt
?? app/src/main/java/com/example/weglow/domain/model/ScanFailure.kt
?? docs/TASK_6_SCAN_HAIRSTYLE_ERROR_HANDLING_STANDARDIZATION.md
```

(Plus other pre-existing untracked docs from earlier sessions, unrelated to this task and left untouched.)

## 18. Confirmation no Git/database/UI redesign/unrelated changes

No Git operations were performed (no commit, no push, no branch/stash changes). No Supabase schema change or migration was created. No Compose UI was redesigned — `ScanScreen.kt` and `HairstyleResultsScreen.kt` were not modified at all, since they already only consumed plain `String?`/typed UI state, never raw exceptions. No unrelated feature (acne detection model, CameraX, ONNX preprocessing/postprocessing, TFLite model, navigation, `AppContainer` lifecycles) was touched.

---

**Status:** Task 6 complete. Implementation and automated verification finished; stopped for manual testing per instructions. Not committed, not pushed.
