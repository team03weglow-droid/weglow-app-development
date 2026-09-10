from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image
import pytest

from app import Prediction, Detection, create_app, decode_image


class FakePredictor:
    version = "test-only"

    def __init__(self, detections=None):
        self.detections = detections or []
        self.calls = 0

    def predict(self, image):
        self.calls += 1
        return Prediction(detections=self.detections, image_width=image.width,
                          image_height=image.height, model_version=self.version,
                          confidence_threshold=0.25)


def photo(orientation=None):
    image = Image.new("RGB", (80, 40), "white")
    output = BytesIO()
    exif = image.getexif()
    if orientation is not None:
        exif[274] = orientation
    image.save(output, format="JPEG", exif=exif)
    return output.getvalue()


@pytest.fixture(autouse=True)
def local_auth(monkeypatch):
    monkeypatch.setenv("ALLOW_UNAUTHENTICATED_LOCAL", "true")


def test_real_output_contract_and_orientation():
    detection = Detection(label="papule", confidence=0.87, box=[0.1, 0.2, 0.3, 0.4])
    model = FakePredictor([detection])
    with TestClient(create_app(model)) as client:
        response = client.post("/v1/acne/predict", files={"image": ("photo.jpg", photo(6), "image/jpeg")})
    assert response.status_code == 200
    result = response.json()
    assert (result["image_width"], result["image_height"]) == (40, 80)
    assert result["detections"] == [detection.model_dump()]
    assert model.calls == 1


def test_empty_detection_is_a_success():
    with TestClient(create_app(FakePredictor())) as client:
        response = client.post("/v1/acne/predict", files={"image": ("photo.jpg", photo())})
    assert response.status_code == 200
    assert response.json()["detections"] == []


def test_invalid_image_does_not_run_model():
    model = FakePredictor()
    with TestClient(create_app(model)) as client:
        response = client.post("/v1/acne/predict", files={"image": ("bad.jpg", b"not an image")})
    assert response.status_code == 422
    assert model.calls == 0


def test_oversized_upload(monkeypatch):
    monkeypatch.setattr("app.MAX_UPLOAD_BYTES", 10)
    with TestClient(create_app(FakePredictor())) as client:
        response = client.post("/v1/acne/predict", files={"image": ("photo.jpg", photo())})
    assert response.status_code == 413


def test_inference_failure_is_not_reported_as_no_acne():
    class BrokenPredictor(FakePredictor):
        def predict(self, image):
            raise RuntimeError("failure")
    with TestClient(create_app(BrokenPredictor())) as client:
        response = client.post("/v1/acne/predict", files={"image": ("photo.jpg", photo())})
    assert response.status_code == 503


def test_auth_required_outside_explicit_local_mode(monkeypatch):
    monkeypatch.delenv("ALLOW_UNAUTHENTICATED_LOCAL")
    monkeypatch.setenv("SUPABASE_URL", "https://example.supabase.co")
    monkeypatch.setenv("SUPABASE_PUBLISHABLE_KEY", "test-key")
    with TestClient(create_app(FakePredictor())) as client:
        response = client.post("/v1/acne/predict", files={"image": ("photo.jpg", photo())})
    assert response.status_code == 401
