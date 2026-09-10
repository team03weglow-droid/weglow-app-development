"""Run the real checkpoint through the API; optionally pass a local photo path."""
from io import BytesIO
import os
from pathlib import Path
import sys

from fastapi.testclient import TestClient
from PIL import Image

from app import create_app


if __name__ == "__main__":
    os.environ["ALLOW_UNAUTHENTICATED_LOCAL"] = "true"
    os.environ.setdefault("YOLO_CONFIG_DIR", str(Path(__file__).parent / ".runtime" / "yolo"))
    Path(os.environ["YOLO_CONFIG_DIR"]).mkdir(parents=True, exist_ok=True)
    if len(sys.argv) > 1:
        payload = Path(sys.argv[1]).read_bytes()
    else:
        buffer = BytesIO()
        Image.new("RGB", (640, 480), "white").save(buffer, "JPEG")
        payload = buffer.getvalue()
        print("Synthetic blank image: execution check only, not accuracy validation.")
    with TestClient(create_app()) as client:
        print("Checkpoint classes:", client.app.state.predictor.model.names)
        response = client.post("/v1/acne/predict", files={"image": ("scan.jpg", payload, "image/jpeg")})
        response.raise_for_status()
        print(response.json())
