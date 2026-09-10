"""YOLO acne inference. Run from backend/: uvicorn app:app --host 127.0.0.1."""
from contextlib import asynccontextmanager
from io import BytesIO
import hashlib
import logging
import os
from pathlib import Path
from threading import Lock
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import FastAPI, File, Header, HTTPException, UploadFile, Depends
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

MAX_UPLOAD_BYTES = 12 * 1024 * 1024
MAX_PIXELS = 25_000_000
Image.MAX_IMAGE_PIXELS = MAX_PIXELS
logger = logging.getLogger(__name__)


class Detection(BaseModel):
    label: str
    confidence: float = Field(ge=0, le=1)
    box: list[float]  # Normalized xyxy relative to the EXIF-corrected image.


class Prediction(BaseModel):
    detections: list[Detection]
    image_width: int
    image_height: int
    model_version: str
    confidence_threshold: float


def decode_image(payload: bytes) -> Image.Image:
    try:
        with Image.open(BytesIO(payload)) as image:
            if image.format not in {"JPEG", "PNG", "WEBP"}:
                raise HTTPException(415, "Use a JPEG, PNG, or WebP image.")
            if image.width * image.height > MAX_PIXELS:
                raise HTTPException(413, "Image dimensions are too large.")
            # Match the phone preview. Ultralytics handles letterboxing and normalization.
            return ImageOps.exif_transpose(image).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError) as error:
        raise HTTPException(422, "Cannot decode this image.") from error
    except (Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
        raise HTTPException(413, "Image dimensions are too large.") from error


class YoloPredictor:
    def __init__(self, weights: Path, confidence: float):
        config_dir = Path(os.environ.setdefault("YOLO_CONFIG_DIR", str(Path(__file__).parent / ".runtime" / "yolo")))
        config_dir.mkdir(parents=True, exist_ok=True)
        from ultralytics import YOLO

        if not weights.is_file():
            raise RuntimeError(f"Trained model not found: {weights}. See backend/README.md.")
        if not 0 <= confidence <= 1:
            raise RuntimeError("ACNE_CONFIDENCE must be between 0 and 1.")
        self.model = YOLO(str(weights))
        if self.model.task != "detect":
            raise RuntimeError("The configured model must be a YOLO detection model.")
        self.version = hashlib.sha256(weights.read_bytes()).hexdigest()[:12]
        self.confidence = confidence
        self.lock = Lock()

    def predict(self, image: Image.Image) -> Prediction:
        # A shared Ultralytics predictor must not be mutated by simultaneous requests.
        with self.lock:
            result = self.model.predict(
                source=image, imgsz=640, conf=self.confidence,
                iou=0.7, max_det=300, save=False, verbose=False,
                device=os.getenv("ACNE_DEVICE", "cpu"),
            )[0]
            detections = []
            if result.boxes is not None:
                for box, score, class_id in zip(
                    result.boxes.xyxyn.cpu().tolist(),
                    result.boxes.conf.cpu().tolist(),
                    result.boxes.cls.cpu().tolist(),
                ):
                    coordinates = [max(0.0, min(1.0, float(value))) for value in box]
                    if coordinates[0] >= coordinates[2] or coordinates[1] >= coordinates[3]:
                        continue
                    detections.append(Detection(
                        label=str(result.names[int(class_id)]),
                        confidence=float(score), box=coordinates,
                    ))
        return Prediction(
            detections=detections, image_width=image.width, image_height=image.height,
            model_version=self.version, confidence_threshold=self.confidence,
        )


def require_user(authorization: str | None = Header(default=None)):
    if os.getenv("ALLOW_UNAUTHENTICATED_LOCAL", "false").lower() == "true":
        return
    supabase_url = os.getenv("SUPABASE_URL", "").rstrip("/")
    api_key = os.getenv("SUPABASE_PUBLISHABLE_KEY", "")
    if not supabase_url.startswith("https://") or not api_key:
        raise HTTPException(503, "Authentication is not configured.")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Sign in before scanning.")
    request = Request(f"{supabase_url}/auth/v1/user", headers={
        "Authorization": authorization, "apikey": api_key,
    })
    try:
        with urlopen(request, timeout=10) as response:
            if response.status != 200:
                raise HTTPException(401, "Session expired.")
    except HTTPError as error:
        raise HTTPException(401 if error.code in (401, 403) else 503, "Cannot verify session.") from error
    except (URLError, TimeoutError) as error:
        raise HTTPException(503, "Authentication unavailable.") from error


def create_app(predictor=None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application):
        application.state.predictor = predictor or YoloPredictor(
            Path(os.getenv("ACNE_MODEL_PATH", str(Path(__file__).parent / "models" / "best.pt"))),
            float(os.getenv("ACNE_CONFIDENCE", "0.05")),
        )
        yield

    application = FastAPI(title="WeGlow Acne Analysis", lifespan=lifespan)

    @application.get("/health")
    def health():
        return {"status": "ready", "model_version": application.state.predictor.version}

    @application.post("/v1/acne/predict", response_model=Prediction, dependencies=[Depends(require_user)])
    async def predict(image: UploadFile = File(...)):
        try:
            payload = await image.read(MAX_UPLOAD_BYTES + 1)
            if len(payload) > MAX_UPLOAD_BYTES:
                raise HTTPException(413, "Photo exceeds 12 MB.")
            decoded = await run_in_threadpool(decode_image, payload)
            try:
                return await run_in_threadpool(application.state.predictor.predict, decoded)
            finally:
                decoded.close()
        except HTTPException:
            raise
        except Exception:
            logger.exception("Acne inference failed")
            raise HTTPException(503, "Analysis temporarily unavailable.")
        finally:
            await image.close()

    return application


app = create_app()
