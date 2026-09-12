"""Convert the supplied Keras face-shape classifier into an Android LiteRT model."""

from pathlib import Path

import keras
import numpy as np
import tensorflow as tf


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/hairstyle/face_shape.keras"
TARGET = ROOT / "app/src/main/assets/hairstyle/face_shape.tflite"


model = keras.models.load_model(SOURCE, compile=False)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# Keep float weights: post-training dynamic-range quantization changed this classifier's
# probabilities enough to affect confidence reporting on validation input.
TARGET.write_bytes(converter.convert())

interpreter = tf.lite.Interpreter(model_path=str(TARGET))
interpreter.allocate_tensors()
input_detail = interpreter.get_input_details()[0]
output_detail = interpreter.get_output_details()[0]

# Conversion parity check uses the model's required raw 0..255 RGB input range.
rng = np.random.default_rng(20260912)
sample = rng.uniform(0, 255, size=(1, 224, 224, 3)).astype(np.float32)
keras_output = model(sample, training=False).numpy()
interpreter.set_tensor(input_detail["index"], sample)
interpreter.invoke()
litert_output = interpreter.get_tensor(output_detail["index"])
np.testing.assert_allclose(keras_output, litert_output, rtol=1e-4, atol=1e-5)
assert int(np.argmax(keras_output)) == int(np.argmax(litert_output))

print("input:", interpreter.get_input_details())
print("output:", interpreter.get_output_details())
print("max_abs_error:", float(np.max(np.abs(keras_output - litert_output))))
print("bytes:", TARGET.stat().st_size)
