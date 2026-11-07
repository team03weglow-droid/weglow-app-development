# Task 8 — Move Scan Flow / State Machine Out of ScanScreen

**Scope:** Move the Scan destination's workflow/state machine (mode selection, camera/analyzing stage, completion timing, and the resulting navigation) out of `ScanScreen.kt` into a dedicated `ScanFlowViewModel`, with zero visual redesign.

---

## 1. Root ScanScreen architecture problem confirmed

Confirmed by inspection: `ScanScreen.kt`'s single composable owned a full workflow state machine (`ScanFlowState` MODE_SELECT/CAMERA/ANALYZING, `ScanMode` ACNE/HAIRSTYLE), timing/orchestration state (`analysisStartedAt`, `displayedValidationError`), and three `LaunchedEffect` blocks that computed "has enough time passed to consider this analysis done" and then directly invoked `onAcneScanComplete()`/`onHairstyleScanComplete()` callbacks that triggered `navController.navigate(...)` one level up. Compose UI was the authoritative owner of business/navigation transitions, not just a renderer.

## 2. ScanScreen responsibilities before

- Rendered all 4 visual stages (mode-select, camera, analyzing, validation-error).
- Owned `flowState`/`scanMode` (`rememberSaveable`) and `analysisStartedAt`/`displayedValidationError` (`remember`).
- Ran 3 `LaunchedEffect`s deciding completion/validation-reveal timing against wall-clock (`SystemClock.elapsedRealtime()`) and directly invoking navigation-triggering callbacks.
- Hosted camera permission, gallery picker, and CameraX mechanisms (legitimately UI-owned).

## 3. State classification

**UI-local (stayed in Compose, unchanged):** `hasCameraPermission` + its permission launcher; the gallery `PickVisualMedia` launcher; `CameraCaptureScreen`'s CameraX state (`imageCapture`, `camera`, `flashOn`, `cameraProvider`); `FigmaAnalyzingScreen`'s `photoBitmap` preview load, progress `Animatable` tick loop (`delay(80)`), and scan-line `rememberInfiniteTransition`.

**Workflow (moved to `ScanFlowViewModel`):** `ScanMode`, `ScanFlowState`, `analysisStartedAt`, `displayedValidationError`, and the completion/validation-reveal timing logic that decides when to emit a navigation effect.

## 4. State machine before

```
MODE_SELECT --selectMode--> CAMERA --photo ready--> ANALYZING
ANALYZING --min-duration elapsed + result--> [LaunchedEffect calls onXScanComplete() directly]
ANALYZING --cancel--> CAMERA
CAMERA --back arrow--> MODE_SELECT
(System Back only custom-handled during ANALYZING; falls through to default nav-pop otherwise)
```
All owned and executed inside `ScanScreen`'s composable body.

## 5. State machine after

Identical transitions, now owned by `ScanFlowViewModel`:

```
ScanFlowViewModel.uiState: ScanFlowUiState(scanMode, stage: ScanFlowState, displayedValidationError)

selectMode(mode)          -> stage=CAMERA, scanMode=mode
onPhotoReady(uri, gender) -> scanViewModel.setPhoto(uri); stage=ANALYZING; dispatch to scanViewModel.analyze() or hairstyleViewModel.analyze(...)
retryAcne() / retryHairstyle(gender) -> same timing reset, re-dispatch to the same repository-backed ViewModel
cancelAnalysis()          -> cancels pending completion jobs + underlying ViewModel's analysis; stage=CAMERA
returnToModeSelection()   -> stage=MODE_SELECT
resetFlow()               -> back to ScanFlowUiState() defaults (called on every fresh entry into the Scan destination)

Internal watchers (viewModelScope, react to scanViewModel.uiState / hairstyleViewModel.uiState):
  result != null (matching mode+stage) -> schedule minimum-duration wait -> emit ScanFlowEffect
  validationError != null (hairstyle, matching stage) -> schedule reveal wait -> update displayedValidationError
```

`ScanScreen` reads `ScanFlowUiState` and calls these actions; it never decides completion or calls `navController` itself.

## 6. Exact files modified/created

- **New:** `app/src/main/java/com/example/weglow/feature/scan/ScanFlowViewModel.kt`
- **New:** `app/src/test/java/com/example/weglow/feature/scan/ScanFlowViewModelTest.kt`
- **New:** `app/src/test/java/android/net/FakeUri.kt` (test-only support class, see §18)
- **Modified:** `app/src/main/java/com/example/weglow/ui/screens/ScanScreen.kt` (workflow extracted; visuals untouched)
- **Modified:** `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` (wiring: construct `ScanFlowViewModel`, collect its effects, reset on entry)

**Untouched:** `ScanViewModel.kt`, `HairstyleViewModel.kt`, all repositories, `AppContainer.kt`, CameraX code, model assets/thresholds, every other screen.

## 7. New flow coordinator design

`ScanFlowViewModel(scanViewModel: ScanViewModel, hairstyleViewModel: HairstyleViewModel, elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime)` - a small, focused coordinator that:
- Holds the two existing, **unmodified** analysis ViewModels as collaborators (both already Activity-scoped per Task 7, so sharing a lifetime with this new coordinator is safe).
- Owns only workflow state (`ScanFlowUiState`) and timing (`analysisStartedAt`, three named completion/reveal jobs).
- Contains **zero** ONNX/TFLite/Supabase/image-decoding logic - it only calls `scanViewModel.analyze()/setPhoto()/cancelAnalysis()` and `hairstyleViewModel.analyze()/cancelAnalysis()`, and observes their `uiState` flows.
- Emits one-shot `ScanFlowEffect`s; never references `NavController`.
- `elapsedRealtimeMs` is an injected clock (defaulting to the real one) solely so tests can make every minimum-visible-duration wait resolve in virtual time instead of a real multi-second wait, as explicitly required by the task.

## 8. Every state transition

| Trigger | Before | After |
|---|---|---|
| Select mode | `scanMode = mode; flowState = CAMERA` (composable) | `ScanFlowViewModel.selectMode(mode)` |
| Photo captured/picked | `onPhotoCaptured(uri); flowState = ANALYZING; if(...) beginAcneAnalysis() else beginHairstyleAnalysis(uri)` | `ScanFlowViewModel.onPhotoReady(uri, gender)` |
| Acne result ready | `LaunchedEffect` computes remaining time, `delay`s, calls `onAcneScanComplete()` | Coordinator watcher schedules a job, `delay`s, emits `ScanFlowEffect.NavigateToAcneResults` |
| Hairstyle result ready | Same pattern, `onHairstyleScanComplete()` | Emits `ScanFlowEffect.NavigateToHairstyleResults` |
| Hairstyle validation error | `LaunchedEffect` delays 650ms then sets `displayedValidationError` | Coordinator watcher does the same, in `uiState.displayedValidationError` |
| Cancel (BackHandler / analyzing screens' cancel / validation-error "choose another photo") | 3 separate inline lambdas, each: cancel underlying VM + `flowState = CAMERA` (+ clear validation error) | Single `cancelAnalysis()` action, also cancels any pending completion/reveal job |
| Camera → back arrow | `flowState = MODE_SELECT` | `returnToModeSelection()` |
| Retry (acne) | `beginAcneAnalysis()` (reset timer, clear validation error, call `onAnalyzeAcne()`) | `retryAcne()` |
| Retry (hairstyle) | `photoUri?.let(::beginHairstyleAnalysis)` | `retryHairstyle(gender)` |
| Fresh entry into Scan | Composable recreated, `rememberSaveable`/`remember` reset naturally | `WeGlowApp.kt`'s `composable(Destination.Scan.route)` block runs `LaunchedEffect(Unit) { scanFlowViewModel.resetFlow() }` |

## 9. Timing/delay ownership before/after

All four numeric values are **unchanged** (`5_000L`, `750L`, `3_100L`, `650L`) - only their ownership and naming moved:

| Delay | Classification | Before | After |
|---|---|---|---|
| Acne minimum visible duration (5000ms) | Workflow | `ScanScreen` LaunchedEffect | `ScanFlowViewModel.ACNE_MINIMUM_ANALYSIS_MS` |
| Acne completion grace (750ms) | Workflow | `ScanScreen` LaunchedEffect | `ScanFlowViewModel.ACNE_COMPLETION_GRACE_MS` |
| Hairstyle minimum visible duration (3100ms) | Workflow | `ScanScreen` LaunchedEffect | `ScanFlowViewModel.HAIRSTYLE_MINIMUM_ANALYSIS_MS` |
| Validation-error reveal (650ms) | Workflow | `ScanScreen` LaunchedEffect | `ScanFlowViewModel.VALIDATION_ERROR_REVEAL_DELAY_MS` |
| Progress-bar tick (80ms loop) | **Visual** - stayed in Compose | `FigmaAnalyzingScreen` | unchanged, still in `FigmaAnalyzingScreen` |
| Scan-line `infiniteRepeatable` (1250ms) | **Visual** - stayed in Compose | `FigmaAnalyzingScreen` | unchanged |

All four workflow constants now have named, documented declarations instead of unexplained magic numbers.

## 10. Coroutine ownership

All workflow jobs (`acneCompletionJob`, `hairstyleCompletionJob`, `validationRevealJob`) are launched via `viewModelScope.launch` - lifecycle-aware, automatically cancelled when the ViewModel is cleared (`onCleared()` also explicitly cancels all three as a defensive, immediate cancellation). No `GlobalScope`, no unmanaged `CoroutineScope`. Cancellation is deterministic and explicit: `cancelAnalysis()` and `resetFlow()` both cancel all three named jobs before changing state, so a completion that was already "in flight" (past its `delay`) can never fire after the user cancels - this exact scenario is covered by a dedicated test (§18).

## 11. Navigation effect design

`sealed interface ScanFlowEffect { NavigateToAcneResults, NavigateToHairstyleResults }`, emitted through a `MutableSharedFlow<ScanFlowEffect>(extraBufferCapacity = 1)` (replay = 0). `WeGlowApp.kt` hosts a single `LaunchedEffect(Unit)` at the top level (mounted for the whole app lifetime, next to the existing Task 1 sign-out effect) that collects it and performs the exact same `navController.navigate(...) { popUpTo(Destination.Scan.route){inclusive=true}; launchSingleTop=true }` calls as before. `ScanFlowViewModel` never imports or references `NavController`.

## 12. One-shot event guarantee

`replay = 0` means a collector that subscribes *after* an effect was emitted never receives it - proven directly in `navigationEffectIsOneShotAndIsNeverReplayedToALateObserver` by asserting `viewModel.effects.replayCache.isEmpty()` after an effect fires. Combined with the app-lifetime-mounted collector in `WeGlowApp.kt` (always subscribed before any effect can be emitted, mirroring the existing sign-out effect's pattern), this is the same "no `shouldNavigate = true` boolean that can refire" guarantee the task required.

## 13. Camera/photo picker responsibilities remaining in UI

Unchanged, per the task's explicit carve-out: `rememberLauncherForActivityResult` (both the camera-permission launcher and the gallery `PickVisualMedia` launcher), all of `CameraCaptureScreen`'s CameraX binding/preview/capture/flash/`DisposableEffect` cleanup, and `BackHandler`'s registration (though its *body* now just calls `onCancel()`). Only their **results** (a granted permission, a picked/captured `Uri`) are forwarded into `ScanFlowViewModel` via `onPhotoReady`, `onSelectMode`, etc. - exactly the "Photo Picker → returns Uri → ScanScreen → flowViewModel.onPhotoSelected(uri)" shape the task asked for.

## 14. Back/cancel/reset behavior

Preserved exactly:
- **Processing (ANALYZING) + system Back:** `BackHandler` still fires only when `stage == ANALYZING`; now calls `onCancel` (`cancelAnalysis()`) instead of inline cancel+state logic - same resulting transition (→ CAMERA).
- **Preview/validation-error screen "choose another photo":** now also routes through `cancelAnalysis()` - previously a near-identical inline duplicate of the BackHandler's body; behaviorally identical.
- **Camera back arrow (UI button, not system Back):** `returnToModeSelection()` → MODE_SELECT, exactly as before (still does not touch `scanMode`).
- **Mode-selection close button / system Back at MODE_SELECT or CAMERA:** unchanged - falls through to `onBack` (`navController.popBackStack()`), untouched by this refactor.

## 15. Configuration-change behavior

`ScanFlowViewModel` is created via `viewModel(factory=...)` at the same top level as `ScanViewModel`/`HairstyleViewModel`, so it is Activity-scoped and **survives** Activity recreation (rotation) through the standard `ViewModelStore` mechanism - an improvement over the previous `rememberSaveable`/`remember` split, where `analysisStartedAt`/`displayedValidationError` (plain `remember`) did **not** survive rotation while `flowState`/`scanMode` (`rememberSaveable`) did. This asymmetry is now resolved: the whole workflow state survives rotation consistently. No `SavedStateHandle` was introduced - the task's own guidance was to avoid it unless genuinely needed for process-death persistence, and ViewModel-scoped survival (not process-death survival) is all that was required here.

## 16. Acne flow preservation

Camera/gallery → `scanFlowViewModel.onPhotoReady(uri, null)` → `scanViewModel.setPhoto(uri)` + `scanViewModel.analyze()` → **unmodified** `AcneScanRepository`/ONNX YOLO pipeline → real `AcneScanResult` → `ScanFlowViewModel` schedules the same 5000ms+750ms minimum-visible-duration wait → `NavigateToAcneResults` → `ScanResultsScreen`. No detection, threshold, preprocessing, or NMS behavior touched.

## 17. Hairstyle flow preservation

Camera/gallery → `scanFlowViewModel.onPhotoReady(uri, gender)` → `hairstyleViewModel.analyze(uri.toString(), gender)` → face validation → **unmodified** local face-shape classification (Task 4 invariant) → profile `face_shape` persistence → Task 2's server-filtered Supabase query (Task 2/3 invariants) → recommendations → `ScanFlowViewModel` schedules the same 3100ms minimum-visible-duration wait → `NavigateToHairstyleResults` → `HairstyleResultsScreen`.

## 18. Tests added/updated

**`ScanFlowViewModelTest.kt`** (15 tests, all passing) using real `ScanViewModel`/`HairstyleViewModel` instances backed by fakes (proving the coordinator drives the existing ViewModels rather than duplicating their logic), a `StandardTestDispatcher`, and a fixed injected clock so every wait resolves in virtual time:
1. initial state; 2/3. selecting acne/hairstyle mode; 4. photo-ready progression (and that `scanViewModel.setPhoto` is called regardless of mode); 5/6. acne/hairstyle processing progression; 7/8. acne/hairstyle success → exactly one navigation effect; 9. failure does not navigate; 10. retry after failure follows the same transition and can succeed; 11. cancel mid-analysis returns to CAMERA **and suppresses an already-scheduled completion** (a real race the old `LaunchedEffect`-key-cancellation implicitly handled - now explicit and tested); 12. `resetFlow()` starts a second scan from clean state; 13. duplicate `onPhotoReady` calls do not create duplicate flow progression (only one repository call, one effect); 14. the navigation effect is one-shot (`replayCache` stays empty); 15. a typed `HairstyleFailure` (Task 6) still reaches the underlying `HairstyleUiState.error` unmodified through the coordinator.

**`android/net/FakeUri.kt`** (new, test-only support): `android.net.Uri`'s constructor is package-private and every method on the real Android platform jar throws when unit-tested off-device (this project uses neither Robolectric nor Mockito - confirmed by checking `build.gradle.kts` and `libs.versions.toml`). A minimal same-package `Uri` subclass was the only way to obtain a working `Uri` value (for `onPhotoReady`/`setPhoto`, which are exercised by production code paths that call `.toString()` on it) without introducing a new test framework dependency.

**Existing tests preserved, not weakened:** `ScanViewModelTest`, `HairstyleViewModelTest` (Task 6), `AppContainerLifecycleTest`/offline scan tests (Task 7), `HairstyleRecommendationCatalogTest`/`HairstyleRecommendationTest` (Tasks 4/5), architecture tests - none were modified by this task and all still pass (see §20).

## 19. ScanScreen post-refactor workflow search audit

| Pattern | Remaining matches in `ScanScreen.kt` | Why legitimate |
|---|---|---|
| `ScanFlowState` | Only as `import` + read-only comparisons against `flowState.stage` (a parameter) | Rendering logic only, no mutation |
| `mutableStateOf` | `hasCameraPermission` (permission-check result), `photoBitmap` (loaded preview image), `imageCapture`/`camera`/`flashOn`/`cameraProvider` (CameraX state) | All explicitly Android-UI-mechanism state per the task's own carve-out |
| `rememberSaveable` | **Zero matches** | `flowState`/`scanMode` fully removed |
| `LaunchedEffect` | `photoBitmap` loader (visual), progress-bar 80ms tick loop (visual), 100%-completion animation trigger (visual) | None decide navigation or call an analyze/cancel action - purely presentational |
| `delay(` | Only the progress-bar's `delay(80)` | Visual animation pacing, not a workflow gate |
| `navigate(` | **Zero matches** | `ScanScreen` never touches `NavController` |
| `analyze(` / `analyzeReference(` | **Zero matches** | All analysis triggering now goes through `ScanFlowViewModel` |

**No scattered workflow state machine remains in the composable.** `ScanFlowState`/`ScanFlowUiState`/`ScanFlowEffect` appear only in their three expected files (`ScanFlowViewModel.kt`, `ScanScreen.kt` for rendering, `WeGlowApp.kt` for wiring) - no duplicated workflow logic elsewhere.

## 20. Gradle results

`JAVA_HOME` set to Android Studio's bundled JBR.

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** (`ScanFlowViewModelTest`: 15/15 passing; full suite green) |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** (extra check, since a new test-support file was added under `src/test`, and to confirm no androidTest caller broke) |

## 21. Tasks 1–7 regression verification

- **Task 1:** `WeGlowApp.kt`'s app-lifetime `AuthEvent.SignedOut` `LaunchedEffect` (`scanViewModel.clear(); hairstyleViewModel.clear()`) unchanged; extended with `scanFlowViewModel.resetFlow()` for consistency (see §8), not altering its ownership or navigation logic.
- **Task 2:** `SupabaseHairstyleRepository`'s `filter { ilike("face_shape", ...); or {...} }` block unchanged, verified present.
- **Task 3:** `SupabaseHairstyleRepository` constructor still takes `classifier: HairstyleRepository`, verified present.
- **Task 4:** `LocalHairstyleRepository.recommendationResult()` still returns `recommendations = emptyList()`, verified present.
- **Task 5:** `domain/model/HairstyleRecommendation.kt` still has zero `imageRes` matches, verified.
- **Task 6:** Both repository interfaces still return `Result<T>`; zero `error.message` matches in `feature/scan`/`feature/hairstyle`, verified.
- **Task 7:** `AppContainer`'s `acneScanRepositoryInstance`/`hairstyleRepositoryInstance` memoization fields still present and untouched by this task.

## 22. Remaining architecture issues intentionally deferred

- `ScanScreen.kt` remains a large file (visual Compose code was not split into smaller composables) - per the task's explicit instruction, file size reduction was not the goal and a large remaining amount of visual Compose code is acceptable.
- CameraX internals, image picker, permission handling, and visual design are entirely untouched, as instructed.
- No further consolidation of `ScanViewModel`/`HairstyleViewModel` was attempted - they remain focused on their own domains; `ScanFlowViewModel` only coordinates them.

## 23. `git status` summary

```
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
 M app/src/main/java/com/example/weglow/ui/screens/ScanScreen.kt
 M docs/ARCHITECTURE_REVIEW.md   (pre-existing modification from before this session - not touched by this task)
?? app/src/main/java/com/example/weglow/feature/scan/ScanFlowViewModel.kt
?? app/src/test/java/android/            (FakeUri.kt test support)
?? app/src/test/java/com/example/weglow/feature/scan/ScanFlowViewModelTest.kt
?? docs/TASK_8_SCAN_FLOW_STATE_MACHINE_EXTRACTION.md
```

(Plus other pre-existing untracked docs from earlier sessions, unrelated to this task and left untouched.)

## 24. Confirmation no Git/database/model/UI redesign/unrelated changes

No Git operations were performed. No Supabase schema/RLS change, no migration. No visual redesign - every layout, spacing, color, icon, animation, and piece of wording in `ScanScreen.kt` is byte-identical to before except for the mechanical replacement of local state reads/writes with `flowState.*` reads and coordinator-action calls. No AI model, CameraX version, threshold, or preprocessing/postprocessing behavior was changed. No unrelated feature (Home/Discover/Routines/Profile, products, recommendations, environment/location) was touched.

---

**Status:** Task 8 complete. Implementation and automated verification finished; stopped for manual testing per instructions. Not committed, not pushed.
