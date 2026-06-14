# ADM Local STT

This is a local OpenAI-compatible speech-to-text service for AdvanceDataMonitor voice input.
It exposes `POST /v1/audio/transcriptions`, so the mod can use it through `voice.sttBaseUrl`.

## Windows quick start

1. Open PowerShell in `tools/local-stt/`.
2. Run:

```powershell
.\start-local-stt.bat
```

The first run installs Python packages and downloads the Whisper model. Keep the window open while playing.

## Recommended mod config

Edit `.minecraft/config/advancedatamonitor.cfg`:

```text
voice {
    B:enabled=true
    B:privacyConfirmed=true
    S:sttBaseUrl=http://127.0.0.1:8000
    S:sttApiKey=
    S:sttModel=small
    I:sttTimeoutSeconds=120
}
```

`sttApiKey` can stay empty for `127.0.0.1` and `localhost`.

## Useful environment variables

```powershell
$env:ADM_STT_MODEL = "small"          # tiny, base, small, medium, large-v3, or a local model path
$env:ADM_STT_LANGUAGE = "zh"          # optional; leave empty for auto-detect
$env:ADM_STT_DEVICE = "auto"          # auto, cpu, cuda
$env:ADM_STT_COMPUTE_TYPE = "int8"    # int8 for CPU; float16 is common for CUDA
$env:ADM_STT_PORT = "8000"
.\start-local-stt.bat
```

For Chinese voice commands, start with `small`. If recognition is poor and your machine can handle it, try `medium`.

## Health check

Open <http://127.0.0.1:8000/health>. It should return JSON with `ok: true`.
