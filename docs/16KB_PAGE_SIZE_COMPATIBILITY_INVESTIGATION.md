# Android 16 KB Page Size Compatibility — Investigation Report

**Status:** Investigation only — no files modified.
**Branch:** `fix-16kb-page-compatibility`

Reported warning identified two libraries:

- `lib/x86_64/libandroidx.graphics.path.so`
- `lib/x86_64/libimage_processing_util_jni.so`

All findings below are backed by direct inspection of build files, the Gradle dependency graph, cached Maven artifacts, and merged/stripped build outputs already present in `app/build/`.

## A. Current build environment

| Item | Value | Source |
|---|---|---|
| AGP | **9.3.2** | `gradle/libs.versions.toml:2` |
| Gradle wrapper | **9.5.0** | `gradle/wrapper/gradle-wrapper.properties:5` |
| Kotlin | **2.2.10** | `gradle/libs.versions.toml:10` |
| compileSdk | **37** | `app/build.gradle.kts:29` |
| targetSdk | **37** | `app/build.gradle.kts:35` |
| minSdk | **24** | `app/build.gradle.kts:34` |
| Java/JVM | source/target compatibility **11**, core library desugaring **enabled** (`desugar_jdk_libs 2.1.5`) | `app/build.gradle.kts:58-62` |
| Compose | `buildFeatures.compose = true`, Compose BOM **2026.02.01** → resolves `androidx.compose.ui:*:1.11.0` | `app/build.gradle.kts:64-67`, dependency tree |
| NDK version | **none configured** — no `ndkVersion`, no `externalNativeBuild`/`ndk {}` block anywhere | grep of both `build.gradle.kts` files |
| Own C/C++/JNI code | **None.** No `.cpp`/`.c`/`CMakeLists.txt`/`jniLibs/` anywhere under `app/src`. Every packaged `.so` originates from a third-party AAR. | filesystem search |

AGP 9.3.2 already exceeds the commonly-cited "AGP ≥ 8.5.1" threshold, so **no AGP change is implicated by this warning**. Same logic applies to Gradle wrapper, Kotlin, compileSdk/targetSdk — all are already far ahead of the minimums associated with 16 KB support.

## B. Native-library inventory (from `mergeDebugNativeLibs` output, all 4 ABIs present for each)

| .so | Packaging ABIs | Owning dependency |
|---|---|---|
| `libimage_processing_util_jni.so` | arm64-v8a, armeabi-v7a, x86, x86_64 | `androidx.camera:camera-core:1.3.4` |
| `libandroidx.graphics.path.so` | arm64-v8a, armeabi-v7a, x86, x86_64 | `androidx.graphics:graphics-path:1.0.1` (transitive) |
| `libonnxruntime.so` | arm64-v8a, armeabi-v7a, x86, x86_64 | `com.microsoft.onnxruntime:onnxruntime-android:1.23.2` |
| `libonnxruntime4j_jni.so` | arm64-v8a, armeabi-v7a, x86, x86_64 | `com.microsoft.onnxruntime:onnxruntime-android:1.23.2` |

No TensorFlow Lite, MediaPipe, ML Kit, or OpenCV dependency exists anywhere in the resolved `debugRuntimeClasspath` (verified by grep against the full `./gradlew :app:dependencies` tree — zero matches). So these 4 `.so` files are the **complete** native surface of the app.

## C. Confirmed owner of `libandroidx.graphics.path.so`

Confirmed via `app/build/intermediates/merged_native_libs_blame/debug/mergeDebugNativeLibs/native-libs-blame-debug-report.txt`:

```
lib/x86_64/libandroidx.graphics.path.so->androidx.graphics:graphics-path:1.0.1
```

Confirmed **transitive** (not declared directly in `libs.versions.toml`/`build.gradle.kts`). Dependency tree shows the chain:
`androidx.compose.ui:ui-graphics:1.11.0` (declared directly, pulled in by Compose BOM 2026.02.01) → `androidx.compose.ui:ui-graphics-android:1.11.0` → `androidx.graphics:graphics-path:1.0.1`.

## D. Confirmed owner of `libimage_processing_util_jni.so`

Same blame report:

```
lib/x86_64/libimage_processing_util_jni.so->androidx.camera:camera-core:1.3.4
```

Confirmed **direct** dependency (`app/build.gradle.kts:119`, version pinned via `cameraX = "1.3.4"` in the catalog). Also verified by unzipping the cached AAR at `~/.gradle/caches/.../camera-core/1.3.4/.../camera-core-1.3.4.aar` — it contains exactly this `.so` for all 4 ABIs.

## E. Current alignment evidence

Measured directly from ELF `PT_LOAD` program headers, using a small offline ELF parser against the raw `.so` bytes extracted straight from the cached AARs — not just the Gradle-processed output.

| .so | Source version | PT_LOAD max alignment | 16 KB compliant? |
|---|---|---|---|
| `libimage_processing_util_jni.so` | camera-core **1.3.4** | 4096 | ❌ No |
| `libandroidx.graphics.path.so` | graphics-path **1.0.1** | 16384 | ✅ **Yes, already** |
| `libonnxruntime.so` | onnxruntime-android **1.23.2** | 16384 | ✅ Yes |
| `libonnxruntime4j_jni.so` | onnxruntime-android **1.23.2** | 16384 | ✅ Yes |

**Important finding:** the currently-resolved `androidx.graphics:graphics-path:1.0.1` binary is *already* 16 KB-aligned. The build-time warning that names `libandroidx.graphics.path.so` appears to be based on a stale/cached scan rather than the artifact actually being pulled in right now — no version bump is needed for it, but it's worth re-checking the raw warning source (e.g., Play Console pre-launch report timestamp) before assuming it still applies.

Also verified with `zipalign -c -P 16 -v4` against the existing `app-debug.apk`: every native-lib zip entry reports `(OK)` and `Verification successful` — i.e., **zip-level page alignment is already fine** (AGP 9.3.2 is already producing a correctly page-aligned APK container). The only remaining problem is the *internal* ELF segment alignment baked into the camera-core 1.3.4 binary itself, which Gradle/AGP cannot fix — it can only be fixed by using a build of that library compiled with 16 KB alignment.

## F. Exact minimal proposed change

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | `cameraX = "1.3.4"` → `cameraX = "1.4.1"` (minimum verified fix) |

That's it — one line. Because `app/build.gradle.kts` already references `libs.androidx.camera.core/camera2/lifecycle/view` through the single `cameraX` catalog variable, bumping it updates all four CameraX artifacts consistently with no other file touched.

Independently confirmed (by extracting the raw `.so` from the already-cached `camera-core-1.4.1.aar`, without applying it to the project) that in 1.4.1:

- `libimage_processing_util_jni.so` → PT_LOAD alignment **16384** (fixed)
- a new `libsurface_util_jni.so` ships in this version → also **16384** aligned, for all 4 ABIs

This matches public CameraX release notes: 1.2.x/1.3.x were built with 4 KB alignment; the fix shipped starting in **CameraX 1.4.0** ([CameraX 1.4.0 is Stable](https://groups.google.com/a/android.com/g/camerax-developers/c/JFZ951-t924)). Latest stable at time of writing is reportedly 1.5.1, but 1.4.1 is the smallest verified jump and has direct binary proof; 1.5.1 is a reasonable alternative if you'd rather not fall behind, but it's a larger surface-area change and was not locally verified.

**No other dependency needs a version change**: onnxruntime-android 1.23.2 is already compliant, and graphics-path 1.0.1 (transitive, not independently pinnable) is already compliant.

## G. Why this change is necessary

`androidx.camera:camera-core:1.3.4`'s bundled `libimage_processing_util_jni.so` was compiled with 4 KB ELF segment alignment. On a device that boots with a 16 KB page size (required for some newer/future Android devices, and enforced by Google Play submission checks for apps targeting Android 15+), the dynamic linker cannot safely mmap a 4 KB-aligned shared library, which either blocks install or causes a runtime failure. Upgrading to camera-core ≥ 1.4.0 is the only way to change this, since it's a prebuilt native binary — nothing in the WeGlow Gradle config can re-align it.

## H. Risks to existing WeGlow functionality

- CameraX 1.3.4 → 1.4.1 is a minor-version bump within the same stable API surface; Google documents it as source/binary compatible for typical `CameraController`/`ImageAnalysis`/`Preview` usage. Given `minSdk = 24`, this is within CameraX 1.4.x's supported floor (API 21+), so no minSdk conflict.
- 1.4.1 adds a new native lib (`libsurface_util_jni.so`, ~3-5 KB per ABI) — negligible APK size increase.
- Behavioral risk is concentrated in `ImageAnalysis`/`ImageCapture`/`CameraController` internals since `libimage_processing_util_jni.so` implements YUV-to-RGB and image transform logic used by the app's acne-scan camera pipeline — this must be manually retested (see verification plan).
- No functionality is being removed; ML (ONNX), Compose, Supabase, Coil, etc. are untouched.

## I. Files that WOULD need modification (when approved)

- `gradle/libs.versions.toml` — bump `cameraX` version string only.

No changes needed to `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle-wrapper.properties`, `AndroidManifest.xml`, or any Kotlin source file.

## J. Verification plan after implementation

1. `./gradlew :app:assembleDebug` and re-inspect `app/build/intermediates/merged_native_libs_blame/debug/.../native-libs-blame-debug-report.txt` to confirm camera-core resolves to 1.4.1 for `libimage_processing_util_jni.so` and the new `libsurface_util_jni.so`.
2. Re-run the same ELF `PT_LOAD` alignment check against `app/build/intermediates/stripped_native_libs/debug/.../lib/{arm64-v8a,x86_64}/libimage_processing_util_jni.so` — expect `16384` for both.
3. `zipalign -c -P 16 -v4` against the assembled release APK/AAB — expect `Verification successful` with no `(BAD)` entries (should stay clean, since this was already passing).
4. Build a release AAB and run it through `bundletool` or Android Studio's APK Analyzer 16 KB check, and/or re-upload to Play Console pre-launch report to confirm the specific warning clears.
5. Functional regression pass on-device (physical device, since CameraX camera pipeline behavior can't be fully validated in emulator): camera preview, capture, and the acne-scan analysis flow end-to-end, plus existing instrumented tests (`androidTestImplementation` suite) and unit tests (`testImplementation`, which use `onnxruntime-desktop` and are unaffected by this change).
6. Confirm `androidx.graphics:graphics-path` still resolves to `1.0.1` post-change (it should, since nothing touches the Compose BOM) and re-verify its `.so` alignment remains 16384 to rule out any unexpected transitive version drift from the CameraX bump.

---

**SAFE TO PROCEED WITH IMPLEMENTATION: YES**

Awaiting approval before touching `gradle/libs.versions.toml`.
