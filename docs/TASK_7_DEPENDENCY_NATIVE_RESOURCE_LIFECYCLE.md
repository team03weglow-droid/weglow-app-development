# Task 7 — Dependency Lifecycle + Native AI Resource Lifecycle

**Scope:** Make ownership and reuse of the acne/hairstyle repositories and their native inference engines (TFLite Interpreter, ONNX OrtEnvironment/OrtSession) explicit and safe in `AppContainer`, without regressing Tasks 1–6.

---

## 1. Root lifecycle issue confirmed or disproved

**Confirmed as a latent, non-yet-manifesting design flaw** — not an active runtime bug in the app as currently wired. `AppContainer.hairstyleRepository(context)` and `AppContainer.acneScanRepository(context)` were ordinary factory functions, unlike every other dependency in `AppContainer` (`authRepository`, `profileRepository`, `profileImageRepository`, `catalogRepository`, all `by lazy`). Tracing the actual call graph in `WeGlowApp.kt` showed both are, in practice, only ever called once per Activity instance today (see §2), so no duplicate native engine is currently being constructed. However, nothing in `AppContainer` itself *prevented* a second call from silently building a second `LocalHairstyleRepository`/`LocalAcneScanRepository` — each owning its own multi-ten-MB native TFLite Interpreter or ONNX OrtEnvironment/OrtSession. This ambiguity is exactly what the task asked to close.

## 2. Investigation findings

- **AppContainer creation:** `val container = remember { AppContainer() }` in `WeGlowApp()` (`navigation/WeGlowApp.kt:59`) — one instance per Composition, i.e. one per Activity instance.
- **ViewModel creation / scope:** `scanViewModel` and `hairstyleViewModel` are created via `viewModel(factory = ...)` at the top level of `WeGlowApp()`, which is called directly from `MainActivity.setContent { }` — **not** inside a `NavHost` `composable{}` block. The nearest `ViewModelStoreOwner` there is `MainActivity` itself, so both ViewModels are Activity-scoped singletons, not per-nav-destination. They are passed down into the `ScanScreen` composable inside the nav graph; navigating to/from Scan does not recreate them.
- **Resolution frequency:** because of the above, `container.hairstyleRepository(context)` and `container.acneScanRepository(context)` are each invoked in exactly one place, exactly once per Activity instance, in the current wiring. Recomposition alone never re-invokes a `viewModel(factory=...)` closure once the ViewModel exists.
- **TFLite Interpreter:** constructed inside `LocalHairstyleRepository`'s `private val interpreter: Interpreter by lazy { ... }` (already lazy, already reused for every `analyze()` call on that instance). Never closed.
- **ONNX OrtEnvironment/OrtSession:** constructed inside `LocalAcneScanRepository`'s `private val environment: OrtEnvironment by lazy {...}` and `private val sessionHandle: SessionHandle by lazy {...}`, the latter carrying a pre-existing comment already explaining the once-built, reused-per-scan design (**earlier performance work had already solved this correctly** — confirmed by inspection, not assumed). Never closed on the success path (only closed defensively if session-shape validation fails, before `lazy` retries).
- **Context retained:** both repositories already store `private val application = context.applicationContext` internally — no Activity context was ever retained, even before this task.
- **Concurrency:** both repositories already wrap their entire `analyze()` body in `mutex.withLock { }` (a `kotlinx.coroutines.sync.Mutex`), serializing overlapping calls to the same native engine. Both ViewModels additionally guard against a second concurrent `analyze()` call (`if (analysis?.isActive == true) return`). **Already correct — no change needed.**
- **Disposal:** neither native resource is ever explicitly closed anywhere in the app. No custom `Application` class exists (`AndroidManifest.xml` declares no `android:name` on `<application>`), so there is no `onTerminate()`-style hook to begin with — and `Application.onTerminate()` is documented by Android as never called on a real device regardless.

## 3. Dependency graph before

```
MainActivity.setContent { WeGlowApp() }
  └─ WeGlowApp()
       ├─ container = remember { AppContainer() }
       ├─ scanViewModel = viewModel { ScanViewModel(container.acneScanRepository(context)) }
       │     acneScanRepository(context) = LocalAcneScanRepository(context)   ← plain factory, unmemoized
       └─ hairstyleViewModel = viewModel { HairstyleViewModel(container.hairstyleRepository(context), ...) }
             hairstyleRepository(context) = SupabaseHairstyleRepository(
                 classifier = LocalHairstyleRepository(context), ...)         ← plain factory, unmemoized
```

## 4. Dependency graph after

```
MainActivity.setContent { WeGlowApp() }
  └─ WeGlowApp()                                                    (unchanged)
       ├─ container = remember { AppContainer() }                   (unchanged)
       ├─ scanViewModel = viewModel { ScanViewModel(container.acneScanRepository(context)) }
       │     acneScanRepository(context) = memoized singleton, @Synchronized,
       │         constructs LocalAcneScanRepository(context.applicationContext) ONCE
       └─ hairstyleViewModel = viewModel { HairstyleViewModel(container.hairstyleRepository(context), ...) }
             hairstyleRepository(context) = memoized singleton, @Synchronized,
                 constructs SupabaseHairstyleRepository(
                     classifier = LocalHairstyleRepository(context.applicationContext), ...) ONCE
```

No call site changed — `WeGlowApp.kt` is byte-for-byte identical. Only `AppContainer`'s internal construction strategy changed.

## 5. Exact files modified

- `app/src/main/java/com/example/weglow/app/AppContainer.kt` — the actual fix (memoization).
- `app/src/main/java/com/example/weglow/data/repository/LocalHairstyleRepository.kt` — doc comment only, cross-referencing the new guarantee; **zero behavior change**.
- `app/src/main/java/com/example/weglow/data/repository/LocalAcneScanRepository.kt` — doc comment addition only, cross-referencing the new guarantee; **zero behavior change** (preprocessing, tensor layout, YOLO postprocessing, NMS, thresholds all untouched).
- `app/src/androidTest/java/com/example/weglow/AppContainerLifecycleTest.kt` (new) — proves the memoization invariant.
- `app/src/androidTest/java/com/example/weglow/OfflineHairstyleScanTest.kt` / `OfflineAcneScanTest.kt` — extended to call `analyze()` twice on the same repository instance.

**Untouched:** `WeGlowApp.kt`, `ScanViewModel.kt`, `HairstyleViewModel.kt`, `SupabaseHairstyleRepository.kt`, `MainActivity.kt`, `ViewModelFactory.kt`, model assets, preprocessing/postprocessing code, thresholds, Task 6's `ScanFailure`/`HairstyleFailure` types.

## 6. AppContainer changes

`acneScanRepository(context)` and `hairstyleRepository(context)` kept their exact public signatures but are now backed by a nullable instance field, checked-then-set inside an `@Synchronized` function body (classic double-checked-style memoization, matching the thread-safety guarantee Kotlin's `by lazy(SYNCHRONIZED)` gives the container's other dependencies). `context.applicationContext` is passed explicitly at the one-time construction site, even though both repositories already narrowed to `applicationContext` internally — this makes the "no Activity context retained" guarantee visible at the call site too, not just inside the repository.

## 7. LocalHairstyleRepository lifetime

One instance for the lifetime of its owning `AppContainer` (in practice, for the lifetime of the app process, since `AppContainer` is created once per Activity instance and, per the ViewModelStore analysis in §2, the ViewModel holding it is never rebuilt with a fresh container after the first resolution). Not modified structurally — only a documentation comment was added to its `interpreter` property.

## 8. TFLite/LiteRT Interpreter

- **Owner:** `LocalHairstyleRepository` (unchanged).
- **Construction:** `by lazy`, on first access, inside the one `LocalHairstyleRepository` instance `AppContainer` now guarantees exists (unchanged mechanism; now guaranteed single-instance).
- **Reuse:** every subsequent `analyze()` call on that instance reuses the same Interpreter (unchanged; now proven by a repeated-call instrumentation test, §15).
- **Disposal:** intentionally never closed. Process-scoped resource; no reliable Android "app shutting down" hook exists to hang a `close()` off of, and the OS reclaims native memory on process death. Documented explicitly in code (`AppContainer.hairstyleRepository`'s doc comment) rather than left implicit.

## 9. LocalAcneScanRepository lifetime

Same shape as §7 — one instance for the app process's lifetime, now guaranteed by `AppContainer`'s memoization. No change to its internal structure.

## 10. OrtEnvironment

- **Owner:** `LocalAcneScanRepository` (unchanged).
- **Construction:** `by lazy` (unchanged) — `OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }`.
- **Reuse:** shared by every `createSession(...)` and every `analyze()` call on that instance (unchanged).
- **Disposal:** intentionally never closed, for the same process-lifetime reasoning as the Interpreter (§8). Documented via an added cross-reference in the existing `sessionHandle` doc comment.

## 11. OrtSession

- **Owner:** `LocalAcneScanRepository`, via its `sessionHandle` (unchanged).
- **Construction:** `by lazy`, already confirmed by inspection to be built exactly once and reused for every scan in the app session — this was already correct from earlier performance work, and is untouched (no change to session creation, options, or optimization level).
- **Reuse:** confirmed via a repeated-call instrumentation test (§15) that a second `analyze()` on the same instance still works.
- **Disposal:** never closed on the success path (process-scoped, same reasoning as §8/§10); the pre-existing `createdSession.close()` on a failed shape check (before `lazy` retries) is unchanged.

## 12. Context-retention audit

Both `LocalHairstyleRepository` and `LocalAcneScanRepository` already stored only `context.applicationContext` internally before this task — confirmed by reading their constructors (`private val application = context.applicationContext`). `AppContainer`'s memoized construction now also explicitly passes `context.applicationContext` at the call site, so the guarantee is visible in two places instead of relying solely on the repository's own internal narrowing. `SupabaseHairstyleRepository` retains no `Context` at all (only `SupabaseClient`/`AuthRepository`/`ProfileRepository`). No Activity Context is retained anywhere in this dependency graph, before or after this task.

## 13. Concurrency/thread-safety decision

**No new concurrency mechanism was introduced.** Both repositories already wrap their entire `analyze()` body in `mutex.withLock { }`, fully serializing any overlapping calls to the same native engine, and both ViewModels already reject a second concurrent `analyze()` invocation while one is in flight. `AppContainer`'s two memoizing functions are marked `@Synchronized` purely to make the one-time construction itself race-free (defensive, since these are practically only ever called from Compose's single UI thread) — this is the smallest possible mechanism (a JVM built-in method-level lock), not a worker pool, scheduler, or queue.

## 14. Task-6 typed error preservation

Untouched. `AcneScanRepository.analyze()` still returns `Result<AcneScanResult>`, `HairstyleRepository.analyze()` still returns `Result<HairstyleResult>`. `ScanFailure`/`ScanFailureException`/`HairstyleFailure`/`HairstyleFailureException` were not modified, and no repository re-introduced presentation text or raw exception exposure — confirmed by re-running the Task 6 regression search (§16).

## 15. Tests added/updated

- **`AppContainerLifecycleTest.kt`** (new, instrumentation):
  - `hairstyleRepositoryIsAStableSingletonAcrossRepeatedResolution` — resolves the dependency three times, asserts all three references are the same object.
  - `acneScanRepositoryIsAStableSingletonAcrossRepeatedResolution` — same, for the acne repository.
  - `differentContainersOwnIndependentRepositories` — proves the memoization is per-`AppContainer` instance state, not an accidental global/static singleton.
- **`OfflineHairstyleScanTest.kt`** — now analyzes the *same* `LocalHairstyleRepository` instance twice in a row (`repeat(2)`), proving the lazily-built Interpreter survives and keeps working across repeated calls, plus re-verifying the Task 4 invariant (`recommendations` is empty) on every call.
- **`OfflineAcneScanTest.kt`** — same pattern for `LocalAcneScanRepository` and its ONNX session.

These test the architecture/lifecycle invariant (identity, repeated-call survival) rather than private native internals, as requested. A plain JVM unit test for `AppContainer` was not attempted: constructing `LocalHairstyleRepository`/`LocalAcneScanRepository` requires a real `Context.getApplicationContext()`, which throws on the Android stub jar used by JVM unit tests without Robolectric — introducing Robolectric for this one check would be disproportionate new test infrastructure (consistent with this project's established convention, documented in Task 3's report, of not building heavyweight test doubles for infrastructure this task doesn't otherwise need). The instrumentation test suite already exercises real `Context`, so the invariant is verified there instead.

## 16. Native-resource construction/search audit

Post-implementation search results:

```
TFLite Interpreter
Owner: LocalHairstyleRepository
Lifetime: application/process scoped (one instance for the app's lifetime, guaranteed by AppContainer)
Constructed: once, lazily, on first analyze() call on the one repository instance
Reused: yes — every subsequent analyze() call reuses it (proven by repeated-call test)
Disposed: never explicitly; process lifetime / OS reclaims native memory on process death
Context retained: applicationContext only

ONNX OrtEnvironment
Owner: LocalAcneScanRepository
Lifetime: application/process scoped
Constructed: once, lazily, on first analyze() call on the one repository instance
Reused: yes
Disposed: never explicitly; process lifetime
Context retained: applicationContext only

ONNX OrtSession
Owner: LocalAcneScanRepository (via sessionHandle)
Lifetime: application/process scoped
Constructed: once, lazily (already correct from earlier performance work)
Reused: yes (proven by repeated-call test)
Disposed: never on the success path; closed only in the pre-existing defensive branch when the shape check fails (before lazy retries) — unchanged
Context retained: applicationContext only
```

Exact search results:

| Pattern | Matches |
|---|---|
| `Interpreter(` | 1 — the one construction site inside `LocalHairstyleRepository`'s `by lazy` block |
| `OrtSession` / `createSession` | Production: only inside `LocalAcneScanRepository`'s `sessionHandle by lazy` block (plus its `SessionHandle` data-class field declaration) and a doc-comment mention in `AppContainer`; test files reference it only in their own isolated fixtures (`BundledAcneModelTest`) or comments |
| `LocalHairstyleRepository(` | Production: 1 site (`AppContainer.kt`, now inside the memoized branch) + its own class declaration; test files construct their own instances intentionally |
| `LocalAcneScanRepository(` | Same pattern as above |
| `hairstyleRepository(` | 1 production call site (`WeGlowApp.kt`, unchanged) + the new lifecycle test's repeated calls |
| `acneScanRepository(` | 1 production call site (`WeGlowApp.kt`, unchanged) + the new lifecycle test's repeated calls |
| `.close()` | 1 match — the pre-existing defensive `createdSession.close()` on a failed shape check in `LocalAcneScanRepository`; no other `.close()` exists on either native resource |

## 17. Gradle gate results

`JAVA_HOME` set to Android Studio's bundled JBR.

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

All four ran together in one invocation with no failures. As an extra safety check beyond the required four, `./gradlew compileDebugAndroidTestKotlin` was also run to confirm the new/extended instrumentation tests compile — **BUILD SUCCESSFUL**.

## 18. Tasks 1–6 regression verification

- **Task 1:** `WeGlowApp.kt`'s app-lifetime `AuthEvent.SignedOut` observer and `authViewModel.signOut()` ownership are unchanged (verified present).
- **Task 2:** `SupabaseHairstyleRepository`'s `filter { ilike("face_shape", ...); or { ... } }` block is unchanged (verified present).
- **Task 3:** `SupabaseHairstyleRepository`'s constructor still takes `classifier: HairstyleRepository` as a parameter; it does not construct `LocalHairstyleRepository` internally (verified present).
- **Task 4:** `LocalHairstyleRepository.recommendationResult()` still returns `recommendations = emptyList()` (verified present); the new repeated-call test re-asserts this on every call.
- **Task 5:** `domain/model/HairstyleRecommendation.kt` still has no `imageRes` field (verified — zero matches).
- **Task 6:** both repository interfaces still return `Result<T>`; `error.message` still does not appear in either ViewModel (verified — zero matches).

## 19. Remaining architecture issues intentionally deferred

- `FaceImageValidator`'s ML Kit `FaceDetection` client was intentionally left untouched: it is a smaller on-device resource with its own already-correct, distinct lifetime (ViewModel-scoped, explicitly closed in `HairstyleViewModel.onCleared()` via its existing `Closeable` contract) — a different and already-sound design from the process-scoped Interpreter/OrtSession case, and was not in this task's named scope (TFLite/LiteRT + ONNX only).
- `AppContainer` and its dependencies do not survive process death or, in principle, a configuration change that recreates the Activity without a retained `ViewModelStore` — this is an app-wide characteristic of the existing manual-DI design (shared by every dependency in `AppContainer`, not specific to acne/hairstyle), and introducing an `Application`-scoped container or a DI framework to fix it was explicitly out of scope for this task.
- No `close()`/`AutoCloseable` was added to `LocalHairstyleRepository`/`LocalAcneScanRepository` themselves, per the task's explicit instruction not to invent a fake shutdown hook; the ownership decision (process-scoped, un-closed) is documented in code instead.

## 20. `git status` summary

```
 M app/src/androidTest/java/com/example/weglow/OfflineAcneScanTest.kt
 M app/src/androidTest/java/com/example/weglow/OfflineHairstyleScanTest.kt
 M app/src/main/java/com/example/weglow/app/AppContainer.kt
 M app/src/main/java/com/example/weglow/data/repository/LocalAcneScanRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/LocalHairstyleRepository.kt
 M docs/ARCHITECTURE_REVIEW.md   (pre-existing modification from before this session — not touched by this task)
?? app/src/androidTest/java/com/example/weglow/AppContainerLifecycleTest.kt
?? docs/TASK_7_DEPENDENCY_NATIVE_RESOURCE_LIFECYCLE.md
```

(Plus other pre-existing untracked docs from earlier sessions, unrelated to this task and left untouched.)

## 21. Confirmation no Git/database/UI/unrelated changes

No Git operations were performed. No Supabase schema change, RLS change, or migration was created. No Compose UI was redesigned, no navigation changed, no CameraX/image-picker code touched, no visual appearance changed. No AI model, preprocessing, threshold, or postprocessing behavior was altered — both native pipelines classify/detect exactly as before; only how many instances of their owning repository can ever exist, and how explicitly that guarantee and the resulting disposal decision are documented, changed.

---

**Status:** Task 7 complete. Implementation and automated verification finished; stopped for manual testing per instructions. Not committed, not pushed.
