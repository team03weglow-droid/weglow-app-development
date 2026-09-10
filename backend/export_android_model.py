"""Export the user's YOLOv8s checkpoint and verify its raw outputs before packaging."""
from pathlib import Path
import hashlib
import json
import os
import shutil

ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / "backend/.runtime/yolo"))
os.environ["YOLO_AUTOINSTALL"] = "false"
Path(os.environ["YOLO_CONFIG_DIR"]).mkdir(parents=True, exist_ok=True)

import numpy as np
import onnx
import onnxruntime as ort
import torch
import ultralytics
from ultralytics import YOLO


def main():
    weights = ROOT / "backend/models/best.pt"
    assets = ROOT / "app/src/main/assets/acne"
    assets.mkdir(parents=True, exist_ok=True)
    model = YOLO(str(weights))
    exported = Path(model.export(
        format="onnx", imgsz=640, batch=1, dynamic=False, simplify=False,
        opset=17, nms=False, device="cpu",
    ))
    onnx.checker.check_model(str(exported))
    session = ort.InferenceSession(str(exported), providers=["CPUExecutionProvider"])
    input_node, output_node = session.get_inputs()[0], session.get_outputs()[0]
    labels = [model.names[index] for index in range(len(model.names))]
    assert input_node.shape == [1, 3, 640, 640], input_node.shape
    assert output_node.shape == [1, len(labels) + 4, 8400], output_node.shape

    original = YOLO(str(weights)).model.float().eval()
    generator = np.random.default_rng(42)
    checks = []
    for name, image in [
        ("blank", np.ones((1, 3, 640, 640), dtype=np.float32)),
        ("random", generator.random((1, 3, 640, 640), dtype=np.float32)),
        ("gradient", np.broadcast_to(np.linspace(0, 1, 640, dtype=np.float32), (1, 3, 640, 640)).copy()),
    ]:
        with torch.inference_mode():
            expected = original(torch.from_numpy(image))[0].numpy()
        actual = session.run(None, {input_node.name: image})[0]
        np.testing.assert_allclose(actual, expected, rtol=1e-4, atol=0.02)
        score_difference = float(np.max(np.abs(actual[:, 4:] - expected[:, 4:])))
        assert score_difference < 1e-4, score_difference
        checks.append({"input": name, "max_box_difference_pixels": float(np.max(np.abs(actual[:, :4] - expected[:, :4]))),
                       "max_score_difference": score_difference})

    target = assets / "model.onnx"
    shutil.copyfile(exported, target)
    metadata = {
        "architecture": "YOLOv8s", "source_sha256": hashlib.sha256(weights.read_bytes()).hexdigest(),
        "model_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "input_name": input_node.name, "output_name": output_node.name,
        "input_size": 640, "prediction_count": 8400, "labels": labels,
        "confidence_threshold": 0.05, "iou_threshold": 0.7, "max_detections": 300,
        "output_layout": "1,4+classes,8400", "box_format": "cxcywh_pixels",
        "ultralytics_version": ultralytics.__version__, "onnxruntime_version": ort.__version__,
    }
    (assets / "model.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    report = {"model_sha256": metadata["model_sha256"], "checks": checks,
              "scope": "Numerical export parity on synthetic inputs; not a clinical accuracy evaluation."}
    (ROOT / "backend/android_export_verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"model": str(target), "bytes": target.stat().st_size, "verification": report}, indent=2))


if __name__ == "__main__":
    main()
