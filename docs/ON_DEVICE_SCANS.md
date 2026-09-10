# Offline acne scans on Android

Camera and gallery acne scans execute on the user's phone. The APK includes the
trained model and ONNX Runtime; inference does not upload photos, download weights,
call a backend, or require USB. Existing Supabase sign-in/profile features still
use internet independently of scanning.

## Install and use

Build with `./gradlew.bat assembleDebug`, then install
`app/build/outputs/apk/debug/app-debug.apk` on the phone. Install it over the old
debug APK to preserve application data. APK transfer can use any normal file
transfer method; USB is optional. Once installed, take/select a photo in Acne Scan.
After signing in and reaching the scan screen, you can turn off network access
and scan. No `WEGLOW_ACNE_API_URL`, `adb reverse`, or Python service is used.

## Model and processing

- Source: user's YOLOv8s `best.pt`, shared folder `acne_500_v1-2/weights`.
- Original checkpoint SHA-256 prefix: `c8927d33616a`.
- Bundled files: `app/src/main/assets/acne/model.onnx` and `model.json`.
- The ONNX export uses FP32, opset 17, static input `[1,3,640,640]` and raw
  output `[1,14,8400]`. It preserves the 10 trained classes without retraining.
- ONNX Runtime Android 1.23.2 executes on CPU, with at most four worker threads.
- Camera/gallery decoding corrects EXIF orientation, including mirrors. Preview
  and inference use the same sampled decoder to keep box coordinates aligned.
- The image is fitted into a 640x640 square with gray (114) letterbox padding,
  then converted to RGB channel-first floats divided by 255.
- Each candidate uses its highest class score, minimum confidence 0.15 (15%). There
  is no separate YOLOv8 objectness column. Class-aware NMS uses IoU 0.7 and a
  maximum of 300 detections, before mapping boxes back to the displayed photo.
- The UI shows labels, counts, confidence and boxes. It does not invent skin
  scores, clinical severity, anatomical regions, or face shape.

Scans run on a background dispatcher. A mutex prevents overlapping native scans;
cancellation prevents old results from appearing, though a native CPU inference
already running may finish before its resources can be released. Sessions,
tensors and inference bitmaps are closed after each scan to release memory.
Performance depends on the phone. The model is about 43 MiB before APK overhead.

## Re-export and verify

Python is only needed by developers to change/re-export the model:

```powershell
./backend/.runtime/uv/uv.exe pip install --python backend/.venv/Scripts/python.exe -r backend/requirements-export.txt
./backend/.venv/Scripts/python.exe backend/export_android_model.py
./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The export script checks tensor shapes and compares ONNX raw predictions against
the original checkpoint on three synthetic inputs. The report is saved in
`backend/android_export_verification.json`. This checks conversion fidelity,
not accuracy on human skin; no clinical accuracy claim is made.

Unit tests exercise padding reversal, confidence/class interpretation, invalid
outputs, clipping, class-aware NMS, retry and cancellation. `OfflineAcneScanTest`
loads the actual bundled model through Android JNI and analyzes an EXIF-rotated
photo. Run `./gradlew.bat connectedDebugAndroidTest` with a test device/emulator;
the model test does not need an account or a running backend.

Reference docs: [ONNX Runtime mobile](https://onnxruntime.ai/docs/tutorials/mobile/),
[Ultralytics ONNX export](https://docs.ultralytics.com/integrations/onnx/).
