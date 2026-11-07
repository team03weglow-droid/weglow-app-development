# Task 3 — Local/Supabase Hairstyle Responsibility Separation

**Scope:** Separate the local face-shape ML/classification responsibility from the Supabase hairstyle repository (`docs/ARCHITECTURAL_VIOLATIONS.md`, item 4). No other architectural finding was touched.

---

## 1. Was the architectural violation confirmed on CURRENT main?

Yes. `git log` confirmed Tasks 1 and 2 are already committed on `main` (as `d63f584` and `b27cef5` respectively — committed outside this session, not by me). A fresh read of the post-Task-2 `SupabaseHairstyleRepository.kt` showed the violation unchanged: `class SupabaseHairstyleRepository(context: Context, ...) { private val classifier = LocalHairstyleRepository(context) }` — the remote repository still constructed the on-device classifier itself.

## 2. Exact previous runtime/dependency flow

`AppContainer.hairstyleRepository(context)` → `SupabaseHairstyleRepository(context, supabaseClient, authRepository, profileRepository)` → inside that constructor, `LocalHairstyleRepository(context)` was built and stored as `classifier`. At call time: `HairstyleViewModel.analyze()` → `repository.analyze(uri, gender)` → `SupabaseHairstyleRepository.analyze()` → `classifier.analyze(...)` (on-device TFLite classification) → `profileRepository.updateFaceShape(...)` → `profileRepository.getProfile(...)` → Task-2's server-side-filtered Supabase query → `classified.copy(recommendations = ...)` → `HairstyleUiState.result` → `HairstyleResultsScreen`.

## 3. Root cause

The composition root (`AppContainer`) delegated construction of a concrete, unrelated dependency (the on-device classifier) to the class that needed to *use* it, instead of assembling both dependencies itself and injecting the classifier. This meant a class named and typed for a remote data source silently owned a second, unrelated data source as an implementation detail.

## 4. Exact new runtime/dependency flow

`AppContainer.hairstyleRepository(context)` now builds `LocalHairstyleRepository(context)` itself and passes it as the `classifier: HairstyleRepository` constructor argument to `SupabaseHairstyleRepository`. The runtime call sequence inside `analyze()` is **byte-for-byte identical** to before (classify → persist face shape → re-read profile → Task-2's filtered Supabase query → merge → return) — only *who constructs the classifier* changed, not what the classifier does or when it's called.

## 5. Where local face-shape classification now lives

Unchanged: `LocalHairstyleRepository` (`app/src/main/java/com/example/weglow/data/repository/LocalHairstyleRepository.kt`) still owns the TFLite interpreter, image decoding/cropping, and the on-device face-shape classification. Not touched in this task — it now simply gets constructed one level up (in `AppContainer`) instead of inside `SupabaseHairstyleRepository`.

## 6. What `SupabaseHairstyleRepository` is responsible for after the change

Exactly the remote half: given an already-classified `HairstyleResult` from its injected `classifier`, it persists the face shape to the profile, re-reads the profile for the confirmed shape/gender, runs the Task-2 server-side-filtered Supabase query, maps rows, and merges the result. It no longer knows or cares *how* classification happens — it depends only on the existing `HairstyleRepository` interface, which already is the minimal "classify a photo, return a `HairstyleResult`" contract.

## 7. Where orchestration now occurs

Still in `SupabaseHairstyleRepository.analyze()` — this is a deliberate choice, not an oversight. Introducing a new abstraction "merely to satisfy the diagram" was explicitly out of scope. A separate use-case/coordinator class here would just re-house the same four lines (classify → persist → query → merge) that already live in one cohesive, minimal method on an existing architectural boundary (a `HairstyleRepository` implementation composing another `HairstyleRepository`, i.e. the decorator pattern). Adding a new layer purely to move these lines elsewhere would be scope not requested and complexity not earned.

## 8. How dependencies are constructed/injected

`AppContainer` (the existing composition root) now constructs both `LocalHairstyleRepository(context)` and `SupabaseHairstyleRepository(classifier = ..., client = ..., authRepository = ..., profileRepository = ...)`, matching the dependency rule that concrete dependencies should be assembled at the composition root. No service locator, no global mutable holder — the same constructor-injection style already used everywhere else in `AppContainer`.

## 9. Exact files changed

- `app/src/main/java/com/example/weglow/app/AppContainer.kt` — added `LocalHairstyleRepository` import; `hairstyleRepository(context)` now constructs the classifier and passes it in.
- `app/src/main/java/com/example/weglow/data/repository/SupabaseHairstyleRepository.kt` — constructor now takes `classifier: HairstyleRepository` instead of `context: Context`; removed the internal `LocalHairstyleRepository(context)` construction and the now-unused `android.content.Context` import; updated the class doc comment to state the new ownership boundary.

```kotlin
// AppContainer.kt — composition root now assembles both dependencies
fun hairstyleRepository(context: Context): HairstyleRepository =
    SupabaseHairstyleRepository(
        classifier = LocalHairstyleRepository(context),
        client = supabaseClient,
        authRepository = authRepository,
        profileRepository = profileRepository,
    )
```

```kotlin
// SupabaseHairstyleRepository.kt — no longer constructs its own classifier
class SupabaseHairstyleRepository(
    private val classifier: HairstyleRepository,
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : HairstyleRepository {
    override suspend fun analyze(photoReference: String, gender: String?): HairstyleResult {
        val classified = classifier.analyze(photoReference, gender)
        // ... unchanged: persist face shape, re-read profile, Task 2's filtered query, merge
    }
}
```

## 10. Whether any public interfaces changed and why

No domain interface changed — `HairstyleRepository` is untouched. The only signature change is `SupabaseHairstyleRepository`'s **constructor** (a concrete class, not part of the public domain contract), which was necessary and unavoidable to actually remove the internal construction: it had to start accepting the classifier as a parameter instead of a `Context` to build one itself. This is the smallest change that achieves the separation — reusing `HairstyleRepository` as the classifier's type avoided inventing a new interface.

## 11. How Task 2 server-side filtering was preserved

Not touched. The entire `filter { ilike(...); or { ... } }` block, `genderSynonyms`, `matchingHairstyles`, `ensureHairstylesTableIsReadable`, and the zero-match-vs-table-empty distinction are unchanged, character-for-character, from the post-Task-2 file.

## 12. How face-shape persistence/profile gender behavior was preserved

Unchanged: `profileRepository.updateFaceShape(userId, classified.faceShape)` still runs immediately after classification, using whatever face shape the injected classifier returned (previously always `LocalHairstyleRepository`, still always `LocalHairstyleRepository` via `AppContainer` in production — nothing about *which* classifier is used changed, only where it's constructed). `profile.gender` is still re-read from the persisted profile and flows into the Task-2 query exactly as before.

## 13. Tests added/changed

None. A new test exercising `SupabaseHairstyleRepository.analyze()` end-to-end was deliberately not added, because doing so would require constructing a real `SupabaseClient` — and this codebase has an explicit, established convention of never doing that in unit tests (`SupabaseProfileRepositoryTest.kt`'s own doc comment: "Exercises the pure decision + mapping logic ... without needing a live Supabase client"; every existing Supabase-repository test in this project, including `SupabaseHairstyleRepositoryMappingTest.kt`, only tests pure functions on local fixtures). Building a mocked-network-engine test harness to work around that would be new test infrastructure disproportionate to this task, matching the same judgment call made in the Task 2 report.

What this change *is* protected by:

- The change is purely a constructor-signature/wiring change with byte-identical runtime logic — the Kotlin compiler itself enforces the new wiring is correct (this is exactly why `compileDebugKotlin` and `assembleDebug` are meaningful regression checks here).
- The full existing test suite (`SupabaseHairstyleRepositoryMappingTest`, `HairstyleViewModelTest`, `BundledHairstyleModelTest`, `HairstyleRecommendationCatalogTest`, `OfflineHairstyleScanTest`) ran unchanged and all passed — confirming classification, mapping, gender-synonym matching, and the zero-match handling from Task 2 are all still intact.
- `HairstyleViewModelTest` already independently proves "no duplicate classification": it asserts the injected `HairstyleRepository` (there, a test fake) is called exactly once per `analyze()` invocation — that call-site discipline is untouched by this task.

## 14. Gradle results

`JAVA_HOME` set to Android Studio's bundled JBR — no system Java on `PATH`; environment note, not a code change.

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** (all existing tests, unchanged, pass) |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

## 15. `git diff --name-only`

```
app/src/main/java/com/example/weglow/app/AppContainer.kt
app/src/main/java/com/example/weglow/data/repository/SupabaseHairstyleRepository.kt
docs/ARCHITECTURE_REVIEW.md
```

`docs/ARCHITECTURE_REVIEW.md` is leftover from earlier in this session, unrelated to Task 3. Nothing was staged or committed.

## 16. Exact manual end-to-end tests to perform

1. Fresh account → complete onboarding with a gender selected → go to Scan → choose "Hairstyle" → capture/select a clear face photo → confirm classification still completes and shows a face shape, confidence, traits, and description identical in style to before this change.
2. Confirm the recommendation cards shown (titles, hair length, images) still come from Supabase and look correct for that face shape/gender combination (Task 2 behavior unaffected).
3. Check the user's `profiles` row in Supabase after the scan — confirm `face_shape` was updated to the detected value, exactly as before.
4. Repeat the scan with a different photo that should classify to a different face shape — confirm the profile's `face_shape` updates again and recommendations change accordingly (proves classification → persistence → query hand-off still flows correctly end-to-end).
5. Trigger an invalid photo (no face / multiple faces) — confirm `FaceImageValidator`'s validation-error screen still appears before any classification or Supabase call happens (unaffected by this task, but worth confirming the wiring didn't regress this path).
6. Kill and relaunch the app, then repeat a hairstyle scan — confirm no crash at `HairstyleViewModel`/`AppContainer` construction time (sanity check on the new constructor wiring surviving process restarts).

---

**Status:** Task 3 complete. Task 4, the single-source-of-truth cleanup, and all other architecture findings were NOT addressed.
