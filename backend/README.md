# Model export and optional reference backend

The Android app runs acne scans locally using its bundled ONNX model. It does not
call this Python server. No laptop, server address, internet connection, or USB
cable is needed for scanning. See [offline scan setup](../docs/ON_DEVICE_SCANS.md).

## Export the Android model

The original trained weights are at `models/best.pt` (excluded from Git), downloaded
from the user's shared `acne_500_v1-2/weights` folder:
https://drive.google.com/drive/folders/1rL2haDEVJWxD6YnIALH94xT7SDDbt3g0

The checkpoint is YOLOv8s, trained at 640 pixels, with SHA-256 prefix
`c8927d33616a`. Its classes are Acne scars, Dark spots, black heads, cysts,
freckles, nodules, open pores, papules, pustules, and whiteheads.

From the project root, with the Python environment already created:

```powershell
./backend/.runtime/uv/uv.exe pip install --python backend/.venv/Scripts/python.exe -r backend/requirements-export.txt
./backend/.venv/Scripts/python.exe backend/export_android_model.py
```

Alternatively use a Python 3.12 virtual environment and pip to install the export
requirements. The script writes `app/src/main/assets/acne/model.onnx` and its
manifest, checks tensor shapes, and compares raw ONNX predictions with the
original PyTorch model on three synthetic inputs. See
`android_export_verification.json` for measured conversion differences.
This verifies export fidelity, not accuracy on human skin.

The user's notebook trained into `acne_500_v1` but its inference cell loaded
`acne_v3.1`. This app uses the actual checkpoint in the shared `acne_500_v1-2`
folder. Use that same checkpoint when comparing Colab and Android.

## Optional original-model reference server

`app.py`, `start-local.ps1`, `smoke_test.py`, and `tests/` retain the previous
Python inference implementation for developer comparisons. The APK does not
use them. With Python 3.12:

```powershell
cd backend
py -3.12 -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.lock
cd ..
./backend/start-local.ps1
```

The script starts a loopback-only reference API on http://127.0.0.1:8000 with
explicit local authentication bypass. Stop it with Ctrl+C. You can upload an
image manually in http://127.0.0.1:8000/docs or run:

```powershell
curl.exe -F "image=@C:/path/to/photo.jpg" http://127.0.0.1:8000/v1/acne/predict
./backend/.venv/Scripts/python.exe backend/smoke_test.py C:/path/to/photo.jpg
```

`requirements.lock` records the original reference-server environment;
`requirements-export.txt` records the export dependencies. The reference API
uses confidence 0.05 (5%), IoU 0.7, maximum 300 detections, and CPU by default. It
returns class names, confidence values, normalized boxes, image dimensions, and
the model version. It applies EXIF orientation before inference and does not
save uploaded photos or prediction plots.

Run the reference API tests from `backend/`:

```powershell
.venv/Scripts/python.exe -m pytest tests -q
```

The local bypass is only for the loopback development server. The standalone API
has Supabase session verification when `ALLOW_UNAUTHENTICATED_LOCAL` is unset;
that is separate from the current offline Android scan feature.
