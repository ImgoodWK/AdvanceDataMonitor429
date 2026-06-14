import os
import tempfile
from pathlib import Path
from threading import Lock
from typing import Dict, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from faster_whisper import WhisperModel

app = FastAPI(title="ADM Local STT", version="1.0")

DEFAULT_MODEL = os.environ.get("ADM_STT_MODEL", "small")
DEFAULT_DEVICE = os.environ.get("ADM_STT_DEVICE", "auto")
DEFAULT_COMPUTE_TYPE = os.environ.get("ADM_STT_COMPUTE_TYPE", "int8")
DEFAULT_LANGUAGE = os.environ.get("ADM_STT_LANGUAGE", "").strip() or None
MODEL_DIR = os.environ.get("ADM_STT_MODEL_DIR", "").strip() or None

_models: Dict[str, WhisperModel] = {}
_model_lock = Lock()


def normalize_model_name(model: Optional[str]) -> str:
    name = (model or "").strip()
    if not name or name == "whisper-1":
        return DEFAULT_MODEL
    if name.startswith("whisper-"):
        name = name[len("whisper-"):]
    return name


def get_model(model_name: str) -> WhisperModel:
    with _model_lock:
        if model_name not in _models:
            _models[model_name] = WhisperModel(
                model_name,
                device=DEFAULT_DEVICE,
                compute_type=DEFAULT_COMPUTE_TYPE,
                download_root=MODEL_DIR,
            )
        return _models[model_name]


@app.get("/health")
def health():
    return {
        "ok": True,
        "default_model": DEFAULT_MODEL,
        "device": DEFAULT_DEVICE,
        "compute_type": DEFAULT_COMPUTE_TYPE,
        "language": DEFAULT_LANGUAGE or "auto",
    }


@app.post("/v1/audio/transcriptions")
async def transcribe(
    file: UploadFile = File(...),
    model: str = Form(DEFAULT_MODEL),
    language: Optional[str] = Form(None),
):
    model_name = normalize_model_name(model)
    suffix = Path(file.filename or "voice.wav").suffix or ".wav"
    tmp_path = None
    try:
        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail="empty audio file")
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(content)
            tmp_path = tmp.name

        whisper = get_model(model_name)
        lang = (language or "").strip() or DEFAULT_LANGUAGE
        segments, info = whisper.transcribe(
            tmp_path,
            language=lang,
            vad_filter=True,
            beam_size=5,
        )
        text = "".join(segment.text for segment in segments).strip()
        return JSONResponse({
            "text": text,
            "model": model_name,
            "language": info.language,
            "duration": info.duration,
        })
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        if tmp_path:
            try:
                os.remove(tmp_path)
            except OSError:
                pass
