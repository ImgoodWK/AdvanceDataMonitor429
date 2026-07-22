#!/usr/bin/env python3
from __future__ import annotations

import argparse
import importlib.util
import json
import shutil
import struct
import sys
import time
from pathlib import Path
from urllib.parse import urlparse


SERVER_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = SERVER_ROOT.parent
STATE_PATH = REPO_ROOT / ".workspace" / "card-art" / "meowa-state.json"
REQUIREMENTS_PATH = REPO_ROOT / ".workspace" / "card-art" / "meowa-requirements.json"
RECOVERY_ROOT = REPO_ROOT / ".workspace" / "card-art" / "meowa-recovery"
MEOWA_CLI = REPO_ROOT / ".agents" / "skills" / "game-assets" / "meowart_api.py"


def load_meowa():
    spec = importlib.util.spec_from_file_location("textech_meowart_api", MEOWA_CLI)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load bundled Meowa runner: {MEOWA_CLI}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_state(state):
    temporary = STATE_PATH.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(STATE_PATH)


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        header = handle.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("downloaded media is not a PNG")
    return struct.unpack(">II", header[16:24])


def download_allowed_png(meowa, urls, output_dir: Path):
    unique_urls = []
    seen = set()
    for key, url in sorted(urls, key=lambda item: "thumb" in item[0].lower()):
        if url in seen:
            continue
        seen.add(url)
        unique_urls.append(url)
    if not unique_urls:
        raise RuntimeError("completed payload did not expose a unique media URL")

    last_error = None
    for url in unique_urls:
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.hostname != "media.meowa.ai":
            raise RuntimeError("refusing media URL outside the official Meowa HTTPS media host")
        filename = Path(parsed.path).name
        if not filename.lower().endswith(".png"):
            raise RuntimeError("refusing recovered media without a PNG filename")
        target = output_dir / filename
        temporary = output_dir / f"{filename}.download"
        try:
            response = meowa.requests.get(
                url,
                timeout=meowa.DEFAULT_TIMEOUT,
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept": "image/png,image/*;q=0.9,*/*;q=0.1",
                },
            )
            response.raise_for_status()
            content_type = str(response.headers.get("content-type") or "").split(";", 1)[0].strip().lower()
            if content_type != "image/png":
                raise RuntimeError(f"refusing recovered media with content type {content_type or 'missing'}")
            temporary.write_bytes(response.content)
            if png_size(temporary) != (1024, 1024):
                temporary.unlink(missing_ok=True)
                continue
            temporary.replace(target)
            return {"type": "media", "path": str(target), "mime_type": "image/png"}
        except meowa.requests.RequestException as exc:
            last_error = exc
    if last_error:
        raise last_error
    raise RuntimeError("no candidate media URL delivered a 1024x1024 PNG")


def parse_args():
    parser = argparse.ArgumentParser(description="Recover completed Meowa card-art media without submitting new jobs.")
    parser.add_argument("--ids", default="", help="Comma-separated card ids; defaults to every recoverable state entry")
    parser.add_argument("--max-wait", type=int, default=900, help="Seconds to wait for an already submitted job")
    return parser.parse_args()


def poll_existing_job(meowa, api_key: str, job_id: str, max_wait: int):
    deadline = time.time() + max(max_wait, 1)
    while True:
        payload = meowa.poll_job(
            api_base=meowa.DEFAULT_API_BASE,
            api_key=api_key,
            api_job_id=job_id,
            timeout=meowa.DEFAULT_TIMEOUT,
            verify=True,
        )
        status = str(payload.get("status") or "").strip().lower()
        if status in meowa.TERMINAL_JOB_STATUSES:
            return payload
        if time.time() >= deadline:
            raise TimeoutError(f"existing job {job_id} did not finish within {max_wait}s")
        time.sleep(max(meowa.DEFAULT_POLL_INTERVAL, 0.1))


def recover_one(meowa, api_key: str, requirement, record, max_wait: int):
    card_id = requirement["id"]
    job_id = record.get("finalJobId") or record.get("jobId") or record.get("submissionJobId")
    if not job_id:
        raise ValueError("state entry has no accepted Meowa job id")

    payload = poll_existing_job(meowa, api_key, job_id, max_wait)
    if str(payload.get("status") or "").strip().lower() != "success":
        raise RuntimeError(f"existing Meowa job ended with status {payload.get('status')}")
    workflow_id = str(meowa._payload_workflow_id(payload) or payload.get("job_type") or "").strip()
    urls = [
        (key, url)
        for key, url in meowa._collect_http_urls(payload)
        if meowa._looks_like_downloadable_output_url(key, url, workflow_id=workflow_id)
    ]
    if not urls:
        all_url_fields = [key for key, _ in meowa._collect_http_urls(payload)]
        top_level_fields = sorted(str(key) for key in payload)
        raise RuntimeError(
            "completed payload has no allowed media URL "
            f"(workflow={workflow_id or 'unknown'}, url_fields={all_url_fields}, fields={top_level_fields})"
        )
    output_dir = RECOVERY_ROOT / card_id
    output_dir.mkdir(parents=True, exist_ok=True)
    images = []
    for attempt in range(1, 6):
        try:
            images = [download_allowed_png(meowa, urls, output_dir)]
            break
        except meowa.requests.RequestException as exc:
            if attempt >= 5:
                raise RuntimeError(f"media download failed after {attempt} attempts: {exc}") from exc
        if attempt < 5:
            delay = attempt * 15
            print(f"[{card_id}] media download deferred; retrying in {delay}s")
            time.sleep(delay)
    if len(images) != 1:
        raise RuntimeError(f"expected one recoverable PNG, downloaded {len(images)}")

    final_path = Path(images[0]["path"]).resolve()
    if final_path.parent != output_dir.resolve():
        raise RuntimeError("recovered output escaped its explicit output directory")
    width, height = png_size(final_path)
    if (width, height) != (1024, 1024):
        raise RuntimeError(f"expected 1024x1024 PNG, received {width}x{height}")
    runtime_path = (REPO_ROOT / requirement["runtimeFile"]).resolve()
    runtime_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(final_path, runtime_path)

    final_job_id = str(payload.get("job_id") or payload.get("api_job_id") or job_id).strip()
    manifest = {
        "status": "success",
        "job_id": final_job_id,
        "outputs": [{"type": "media", "path": str(final_path), "mime_type": "image/png"}],
    }
    (output_dir / "final_outputs.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    record.update(
        {
            "status": "recovered",
            "jobId": final_job_id,
            "finalJobId": final_job_id,
            "recoveryOutput": str(output_dir.relative_to(REPO_ROOT)),
            "width": width,
            "height": height,
            "finishedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        }
    )
    record.pop("error", None)
    record.pop("exitCode", None)
    return runtime_path


def main() -> int:
    args = parse_args()
    requested_ids = {value.strip() for value in args.ids.split(",") if value.strip()}
    state = read_json(STATE_PATH)
    requirements = {item["id"]: item for item in read_json(REQUIREMENTS_PATH)["requirements"]}
    candidates = []
    for card_id, record in state.get("cards", {}).items():
        if requested_ids and card_id not in requested_ids:
            continue
        if record.get("status") not in {"failed-validation", "submitted", "interrupted"}:
            continue
        if card_id not in requirements:
            continue
        candidates.append((requirements[card_id], record))
    if not candidates:
        print("No recoverable card-art jobs matched.")
        return 0

    meowa = load_meowa()
    api_key = meowa._resolve_auth_token()
    failed = 0
    for requirement, record in candidates:
        card_id = requirement["id"]
        try:
            runtime_path = recover_one(meowa, api_key, requirement, record, args.max_wait)
            write_state(state)
            print(f"[{card_id}] recovered {runtime_path.relative_to(REPO_ROOT)}")
        except Exception as exc:
            failed += 1
            print(f"[{card_id}] recovery deferred: {exc}", file=sys.stderr)
    print(f"Recovery result: recovered={len(candidates) - failed}, deferred={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
