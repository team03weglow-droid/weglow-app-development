# On-device face-shape model

The Android app ships `app/src/main/assets/hairstyle/face_shape.tflite`, converted from the
Keras model supplied at the Google Drive URL recorded in `model.json`. The original `.keras`
training archive is intentionally not packaged in the APK.

The model accepts one `float32` RGB image with shape `[1, 224, 224, 3]`. Pixel values remain
in the `0..255` range because the EfficientNetB0 graph contains its own rescaling layer. It
returns a five-value softmax vector.

The class order is the alphabetical directory order normally produced by Keras
`image_dataset_from_directory`:

1. Heart
2. Oblong
3. Oval
4. Round
5. Square

If the training notebook used a different `class_indices` mapping, update both
`LocalHairstyleRepository.FACE_SHAPES` and `assets/hairstyle/model.json` before release.

To regenerate the mobile model, download the source as
`app/src/main/assets/hairstyle/face_shape.keras` and run `tools/convert_hairstyle_model.py`
with TensorFlow 2.20+ and Keras. The script checks Keras/LiteRT prediction parity before
shipping.
