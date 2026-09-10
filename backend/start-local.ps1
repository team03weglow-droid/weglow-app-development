$ErrorActionPreference = 'Stop'
$scanPython = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $scanPython)) {
    throw 'Create the backend Python environment first. See backend/README.md.'
}
$env:ALLOW_UNAUTHENTICATED_LOCAL = 'true'
& $scanPython -m uvicorn app:app --app-dir $PSScriptRoot --host 127.0.0.1 --port 8000
exit $LASTEXITCODE
