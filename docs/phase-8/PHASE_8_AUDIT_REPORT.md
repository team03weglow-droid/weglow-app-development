# Phase 8 — Camera, Scanning & AI Model Integration Audit / Hardening

## Verdict

**PASS WITH NON-BLOCKING ISSUES**

---

## 1. Existing implementation discovered

A complete, real, on-device acne-scan pipeline already exists and is production-shaped:

- **Camera/gallery acquisition**: CameraX (front camera, `Preview` + `ImageCapture`) plus Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) for gallery — no legacy storage permission, no custom gallery UI.
- **AI model**: a real bundled YOLOv8s ONNX model (`app/src/main/assets/acne/model.onnx` + `model.json` manifest, 10 acne-related classes), run via `onnxruntime-android` entirely on-device.
- **Architecture**: `ScanScreen` (Compose) → `ScanViewModel` (StateFlow) → `AcneScanRepository` (interface) → `LocalAcneScanRepository` (ONNX inference) → `YoloPostProcessor` (NMS / letterbox undo) → `ScanResultsScreen`. Clean separation, matches Phase 1 layering, wired through `AppContainer` / `WeGlowApp` DI exactly like every other feature.
- A separate, unrelated "Face Shape" / Hairstyle flow also exists in the same screen (see §9).

## 2. Complete scan runtime flow

```
User → ScanModeSelectScreen (chooses Skin Scan) → camera permission check
     → CameraCaptureScreen (CameraX preview) OR gallery Photo Picker
     → captured file:// / picked content:// Uri → ScanViewModel.setPhoto()
     → ScanViewModel.analyze() → LocalAcneScanRepository.analyze()
         → ScanPhotoDecoder.decode() (bounded/downsampled, EXIF-corrected bitmap)
         → Letterbox → prepareInput() (640×640, RGB, CHW, [0,1] float tensor)
         → OrtSession.run() (real ONNX inference, mutex-serialized)
         → YoloPostProcessor.decode() (per-class NMS, letterbox undo, confidence gate)
         → AcneScanResult(detections, width, height, modelVersion, threshold)
     → ScanUiState(result=...) → ScanResultsScreen (draws real boxes over the real photo)
```

## 3. Files inspected

`ScanScreen.kt`, `ScanViewModel.kt`, `ScanResultScreen.kt`, `HairstyleResultsScreen.kt`, `LocalAcneScanRepository.kt`, `YoloPostProcessor.kt`, `AcneScanRepository.kt`, `AcneScanResult.kt`, `ScanPhotoDecoder.kt`, `ProfileCameraImageStore.kt`, `AppContainer.kt`, `WeGlowApp.kt`, `Destination.kt`, `AndroidManifest.xml`, `file_paths.xml`, `HairstyleViewModel.kt`, `InMemoryHairstyleRepository.kt`, `HairstyleRepository.kt`, `HairstyleRecommendation.kt`, `model.json`, plus tests: `ScanViewModelTest`, `YoloPostProcessorTest`, `BundledAcneModelTest`, `OfflineAcneScanTest` (instrumented), `ProfileCameraImageStoreTest`. Also reviewed `app/build.gradle.kts` (dependencies, `architectureCheck` task) and the git history of the YOLO confidence threshold.

## 4. Problems found

| # | Finding | Severity |
|---|---|---|
| A | **Stale unit test.** `YoloPostProcessorTest.ignoresBelowThresholdInvalidAndPaddingOnlyDetections` encoded the *original* 0.25 confidence default. Two later, deliberate teammate commits (`7b4b8bb`, `bccdeec`) intentionally lowered production's default / `model.json` threshold to **0.15** ("Show low-confidence detections without confidence labels"). The test's fixture score of 0.25 is now *above* the current intended threshold, so it no longer exercises rejection — it was failing (confirmed by running it). **Verdict: the test was stale, production behavior is correct and intentional** (confirmed by two dedicated commits + matching `model.json` value). Fixed the test only (see §5). |
| B | **CameraX resource retention.** `CameraCaptureScreen`'s `AndroidView` bound `Preview` + `ImageCapture` to the Activity's `lifecycleOwner` but never unbound them when the composable left composition (navigating to the analyzing/results screen). Since the single-Activity's lifecycle stays `RESUMED` across in-app navigation, the camera (and torch, if left on) kept streaming until the user re-entered the camera screen. Real, if minor, resource/battery concern — fixed (see §5). |
| C | **Simulated "Face Shape" flow (pre-existing, not part of this Phase 8 scope).** See §9 — flagged, not modified. |

No other correctness issues found in preprocessing, tensor layout, NMS, letterbox math, error states, or permission handling — all verified correct against the model's actual manifest.

## 5. Files changed

1. **`app/src/test/java/com/example/weglow/data/scan/YoloPostProcessorTest.kt`** — changed one fixture score in the stale test from `0.25f` to `0.1f` (below the current, intentionally-lowered 0.15 default) so the test again exercises "below current threshold" rejection, and added a comment documenting why. **No production code touched.**
2. **`app/src/main/java/com/example/weglow/ui/screens/ScanScreen.kt`** — added a `cameraProvider` reference and a `DisposableEffect { onDispose { cameraProvider?.unbindAll() } }` in `CameraCaptureScreen` so the camera is released when the screen leaves composition.

Both changes were re-verified against the full build/test suite after applying.

## 6. Why each change was necessary

- The stale test would otherwise fail every CI run for a reason unrelated to any real defect, and it silently misrepresented what the "correct" threshold behavior is supposed to be. Per explicit Phase 8 instructions, production behavior was investigated first and found deliberate; only the assertion fixture needed updating.
- The camera-unbind fix closes a genuine, verifiable resource-lifecycle gap directly called out by the audit's own camera-safety checklist ("activity lifecycle behavior," "repeated scans do not create obvious resource leaks"). It is additive and low-risk (a `DisposableEffect` cleanup), does not alter the capture/inference pipeline, and does not touch the acne model at all.

## 7. Camera/gallery safety status

**Good**, with the one leak now fixed:

- Runtime `CAMERA` permission requested via `ActivityResultContracts.RequestPermission`; denial path shown with a clear message and a gallery fallback (no dead end).
- Gallery uses Android Photo Picker — no broad `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` permission requested, matches the "don't request broad storage permission" requirement.
- Camera cancellation: `ImageCaptureException` in `onError` deliberately does not emit a Uri — no orphaned/bad photo state.
- Gallery cancellation: `uri == null` guarded everywhere.
- Invalid/corrupted/unreadable images: `ScanPhotoDecoder` throws `IOException`, mapped by `ScanViewModel` to a user-facing "Cannot read this photo" error rather than a crash or fake result.
- Photo capture uses a private cache file read back in-process via `ContentResolver` (not exposed to another process/app), so the lack of `FileProvider` here is safe, unlike the Phase 6 profile-picture flow which correctly does use a non-exported `FileProvider` scoped to a single cache subdirectory.
- Temp scan files are per-capture-timestamped `cacheDir` files; OS-managed cache eviction applies (no unbounded accumulation logic exists, but this matches how the profile-picture capture store already works and is non-blocking).

## 8. Acne model integration status

**Confirmed real, no simulation.** Traced the full path: real ONNX model loaded from assets → real tensor built from actual decoded pixels → real `session.run()` → real YOLO decode with real confidence/NMS math → real coordinates drawn over the real captured photo. `BundledAcneModelTest` (loads the actual `.onnx` file and asserts its SHA-256 against the manifest) and `OfflineAcneScanTest` (instrumented, full round-trip against a real bitmap) both exist and both pass. No random values, no hardcoded classes, no fake confidence, no fake fallback. Failure paths (`OutOfMemoryError`, `LinkageError`, corrupt file, incompatible model shape) all surface as real errors, never fabricated results.

## 9. Face-shape integration status

**Present, but not a genuine face-shape model.** The "Face Shape" scan mode (`ScanModeSelectScreen` → `ScanMode.HAIRSTYLE`) runs a **scripted, hardcoded progress animation** (`AnalyzingScreen`, fixed messages like "Detecting face shape...", "Matching hairstyles...") with no underlying inference, then navigates to `HairstyleResultsScreen`, which shows a **static, gender-only** (not photo-derived) recommendation set from `InMemoryHairstyleRepository`. There is no face-shape model, and no ML input from the captured photo at all in this path.

Per the Phase 8 scope rules, this is teammate-owned, separately-tracked work and does not block this audit — the acne pipeline this phase is scoped to is real and production-ready. However, unlike a simply-absent feature, this one actively presents itself to the user as running a real AI face-shape analysis (progress bar, specific technical-sounding messages) while never touching the captured photo. This is flagged as a **non-blocking but user-trust-relevant** issue for the teammate owning face-shape work to address before that flow ships — it was not modified, per the explicit instruction not to invent/duplicate/rebuild that teammate's work.

## 10. Performance/resource status

- Image decoding is bounded (`ScanPhotoDecoder` downsamples to ≤1600px via `ImageDecoder` / `BitmapFactory.inSampleSize`), preventing OOM on large camera/gallery images.
- Orientation is corrected from EXIF before both preview and inference, keeping detection boxes aligned with what the user sees.
- Model input (640×640, RGB, CHW, `[0,1]`) matches the manifest exactly, verified against the real ONNX session's declared input tensor shape at runtime (`check(shape.contentEquals(...))`).
- Inference runs on `Dispatchers.Default` inside `viewModelScope`, off the main/Compose thread; `ensureActive()` checks throughout allow cooperative cancellation.
- Model session/tensors are `.use{}`-scoped (closed after every scan, including error/cancellation paths); a `Mutex` serializes concurrent scan attempts so rotation/recomposition cannot trigger duplicate concurrent inference.
- Fixed: camera hardware resource retention across navigation (§4B, §5).

## 11. Security status

- No Supabase secrets, service-role keys, or hardcoded credentials found anywhere in the scan/camera code (or elsewhere scanned).
- `FileProvider` is `exported="false"`, scoped to a single private cache subdirectory (`profile-camera/`) — no broad filesystem exposure.
- `MainActivity` is `exported="true"` only because it's the launcher/OAuth-callback target, as required.
- No user-controlled arbitrary filesystem paths; camera output paths are app-generated, timestamp-based.
- No unnecessary broad storage permissions requested.

## 12. Test results

`./gradlew testDebugUnitTest` → **BUILD SUCCESSFUL**, all tests pass, including `BundledAcneModelTest` (real ONNX model load + native inference) and the corrected `YoloPostProcessorTest`.

## 13. `architectureCheck` result

**BUILD SUCCESSFUL** — Phase 1 dependency boundaries enforced, no violations.

## 14. `compileDebugKotlin` result

**BUILD SUCCESSFUL** — 2 pre-existing deprecation warnings (unrelated `LocalLifecycleOwner` / `Icons.Filled.ArrowBack` API deprecations), no errors.

## 15. `lintDebug` result

**BUILD SUCCESSFUL** — 0 errors, 93 warnings, 1 hint. All warnings are dependency-version notices (CameraX/ONNX Runtime updates available) and drawable-density-folder hints for the hairstyle images — nothing scan/camera/model-specific or blocking.

## 16. `assembleDebug` result

**BUILD SUCCESSFUL** — debug APK assembled cleanly with both fixes applied.

## 17. Remaining non-blocking technical debt

1. The "Face Shape" scan mode simulates AI analysis with no real model (§9) — teammate-owned, tracked separately, should be replaced with a real face-shape pipeline (or clearly relabeled as "coming soon") before release.
2. Minor `androidx.camera` / `onnxruntime` dependency version bumps available (lint `GradleDependency` / `NewerVersionAvailable`) — routine maintenance, not urgent.
3. Hairstyle drawables are in a densityless `drawable/` folder — cosmetic lint hint only.
4. No explicit temp-file cleanup policy for scan capture files in `cacheDir` (relies on OS cache eviction) — consistent with existing Phase 6 pattern, not a new issue.

## 18. Safe to commit and merge

**Yes.** The acne camera/scan/model pipeline was already production-ready. The two changes made are minimal, narrowly scoped, fully test-verified, and do not alter the acne model, its preprocessing, its thresholds, or any Phase 1–7 architecture. Nothing was staged or committed — both modified files are left in the working tree for review before committing, per the no-Git-operations constraint for this audit.
