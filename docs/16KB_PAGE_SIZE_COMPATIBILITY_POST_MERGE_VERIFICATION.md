# 16 KB Page-Size Compatibility Verification — Post-Merge

**Branch:** `fix-16kb-page-compatibility` (after merging `origin/main`)
**Scope:** Re-verification following the merge that introduced the face-shape TFLite pipeline
(`face_shape.tflite`, `LocalHairstyleRepository`, new Gradle dependencies, face validation/scan flow).
**Nature of this pass:** Investigation/verification only. No source, dependency, or config files were
modified. No git operations were performed.

---

## 1. Gradle gate results (run against the current merged tree)

| Task | Result |
|---|---|
| `./gradlew clean` | ✅ BUILD SUCCESSFUL |
| `./gradlew architectureCheck` | ✅ BUILD SUCCESSFUL |
| `./gradlew compileDebugKotlin` | ✅ BUILD SUCCESSFUL (only pre-existing deprecation warnings, unrelated to this change) |
| `./gradlew testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `./gradlew lintDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL (fresh `app-debug.apk`, 234,980,824 bytes) |

`stripDebugDebugSymbols` logged an informational (non-failing) message: it could not strip 8 prebuilt
`.so` files and packaged them as-is. These are exactly the 8 libraries analyzed below.

`bundleRelease` was intentionally **not** run — there is a separately identified, pre-existing AAPT2
issue involving JPEG data stored in `.png`-named drawable resources, to be fixed separately.

---

## 2. Full native-library alignment table (fresh APK, `arm64-v8a` / `x86_64`)

Extracted directly from the freshly built `app-debug.apk` (not just build intermediates). Alignment
was read via direct ELF program-header parsing (no NDK `readelf` was available on this machine, so a
small parser was written against the raw ELF headers) and cross-checked against `zipalign`'s own
per-entry alignment report — both agree on every file.

| `.so` | Maven owner:artifact:version | arm64-v8a PT_LOAD align | x86_64 PT_LOAD align | 16 KB compliant |
|---|---|---|---|---|
| `libonnxruntime.so` | `com.microsoft.onnxruntime:onnxruntime-android:1.23.2` | 16384 | 16384 | **YES** |
| `libonnxruntime4j_jni.so` | `com.microsoft.onnxruntime:onnxruntime-android:1.23.2` | 16384 | 16384 | **YES** |
| `libimage_processing_util_jni.so` | `androidx.camera:camera-core:1.4.1` | 16384 | 16384 | **YES** |
| `libsurface_util_jni.so` | `androidx.camera:camera-core:1.4.1` | 16384 | 16384 | **YES** |
| `libandroidx.graphics.path.so` | `androidx.graphics:graphics-path:1.0.1` (transitive) | 16384 | 16384 | **YES** |
| `libLiteRt.so` | `com.google.ai.edge.litert:litert:2.1.5` **(new, from merge)** | 16384 | 16384 | **YES** |
| `libLiteRtClGlAccelerator.so` | `com.google.ai.edge.litert:litert:2.1.5` **(new, from merge)** | 16384 | 16384 | **YES** |
| `libface_detector_v2_jni.so` | `com.google.mlkit:face-detection:16.1.7` **(new, from merge)** | 16384 | 16384 | **YES** |

---

## 3. Specifically-requested reconfirmation

- `libimage_processing_util_jni.so` ≥ 16384 → **16384, PASS**
- `libsurface_util_jni.so` ≥ 16384 → **16384, PASS**
- `libandroidx.graphics.path.so` ≥ 16384 → **16384, PASS**
- `libonnxruntime.so` ≥ 16384 → **16384, PASS**
- `libonnxruntime4j_jni.so` ≥ 16384 → **16384, PASS**
- New TFLite/JNI libraries from the teammate's face-shape work — `libLiteRt.so`,
  `libLiteRtClGlAccelerator.so` (LiteRT, `com.google.ai.edge.litert:litert:2.1.5`) and
  `libface_detector_v2_jni.so` (`com.google.mlkit:face-detection:16.1.7`) — all **16384, PASS**.

**Note:** `face_shape.tflite` itself is a data asset, not native code. `androidResources.noCompress`
already includes `tflite`/`onnx`, so it's stored uncompressed in the APK — unrelated to the 16 KB
ELF-alignment question.

---

## 4. zipalign 16 KB verification

Build-Tools 36.0.0 used (≥35 requirement met):

```
zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk
```

All 16 target-ABI `.so` entries (8 × arm64-v8a, 8 × x86_64) reported `(OK)`, with zero `FAILED`/`BAD`
entries anywhere in the archive.

**Result: `Verification successful`**

---

## 5. CameraX consistency

`:app:dependencies --configuration debugRuntimeClasspath` shows `androidx.camera:camera-core`,
`camera-camera2`, `camera-lifecycle`, and `camera-view` all resolving to `1.4.1` throughout the graph,
with no `->` override arrows and no competing version anywhere in the resolved runtime classpath.

A stale `camera-core-1.3.4.aar` exists only in the local Gradle module cache (`~/.gradle/caches`) from
a prior resolution — it is not part of the resolved dependency graph and was not packaged. This was
confirmed directly: both `libimage_processing_util_jni.so` and `libsurface_util_jni.so` in the built
APK trace back only to the `camera-core-1.4.1.aar`.

**CameraX confirmed: 1.4.1** ✅

---

## 6. Impact of the merge on prior 16 KB conclusions

The merge added two new native-code-bearing dependencies that were **not** present at the time of the
earlier verification:

- `com.google.ai.edge.litert:litert:2.1.5` → ships `libLiteRt.so`, `libLiteRtClGlAccelerator.so`
- `com.google.mlkit:face-detection:16.1.7` → ships `libface_detector_v2_jni.so`

Note on ML Kit: in this version, `face-detection` bundles its own JNI directly inside the AAR — it is
**not** purely a Google-Play-Services-delivered dynamic module, despite also transitively pulling in
`play-services-mlkit-face-detection` as a runtime fallback path.

Both new libraries are compliant at 16384-byte alignment. The prior conclusion is **unchanged in
outcome** but was incomplete: the earlier report predates the introduction of these two libraries and
should be treated as superseded/appended by this document rather than as still complete on its own.

No git operations were performed and no source, dependency, or configuration file was modified during
this verification pass.

---

## Final verdict

**SAFE TO MERGE: YES**

All 16 native libraries across `arm64-v8a` and `x86_64` (including both newly introduced LiteRT and ML
Kit face-detection JNI libraries) are ≥16384-byte PT_LOAD aligned, `zipalign -P 16` verification
succeeded, and all requested Gradle gates pass on the current merged tree.
