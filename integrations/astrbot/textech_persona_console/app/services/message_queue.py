from __future__ import annotations

import hashlib
import json
import os
import re
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from .. import json_store
from ..config import COMPANIONS, CONSOLE_BRIDGE_QUEUE, PC_CONFIG, PERSONA_LIB, PERSONA_LIB_ALT

try:
    import fcntl
except ImportError:  # pragma: no cover - Windows test fallback
    fcntl = None  # type: ignore

_QUEUE_LOCK = threading.RLock()
_UMO_RE = re.compile(r"^[A-Za-z0-9_.-]+:(FriendMessage|GroupMessage):[^:\s]{3,240}$")
_SENSITIVE_VALUE_RE = re.compile(
    r"(?i)(?:api[_ -]?key|access[_ -]?token|client[_ -]?secret|password|passwd|"
    r"session[_ -]?secret)\s*[:=]\s*\S{6,}|bearer\s+\S{8,}|sk-[A-Za-z0-9_-]{8,}"
)
_ACTIVE_STATUSES = {"pending", "processing", "sending"}
_MAX_ACTIVE_JOBS = 30
_MAX_STORED_JOBS = 200
PREVIEW_TARGET_KEY = "preview:local"
DEFAULT_PERSONA_KEY = "bot:default"


def _queue_path() -> Path:
    return json_store.data_path(CONSOLE_BRIDGE_QUEUE)


@contextmanager
def _locked_queue() -> Iterator[Path]:
    path = _queue_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    lock_path = path.with_suffix(path.suffix + ".lock")
    with _QUEUE_LOCK:
        with lock_path.open("a+b") as lock_file:
            if fcntl is not None:
                fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
            try:
                yield path
            finally:
                if fcntl is not None:
                    fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def _read_queue(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"version": 1, "jobs": []}
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError):
        return {"version": 1, "jobs": []}
    jobs = value.get("jobs") if isinstance(value, dict) else None
    return {"version": 1, "jobs": jobs if isinstance(jobs, list) else []}


def _write_queue(path: Path, data: dict[str, Any]) -> None:
    tmp = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    with tmp.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(tmp, path)


def _tokens(raw: Any) -> list[str]:
    if isinstance(raw, list):
        values = [str(item) for item in raw]
    else:
        values = re.split(r"[,，、\s]+", str(raw or ""))
    return [item.strip() for item in values if item.strip()]


def _mask_identifier(value: Any) -> str:
    text = str(value or "").strip()
    if not text:
        return "未知"
    if len(text) <= 6:
        return "＊" * max(3, len(text))
    return f"{text[:2]}…{text[-4:]}"


def _target_key(kind: str, umo: str) -> str:
    digest = hashlib.sha256(f"{kind}\0{umo}".encode("utf-8")).hexdigest()[:20]
    return f"{kind}:{digest}"


def _valid_umo(value: Any, expected_type: str) -> str:
    umo = str(value or "").strip()
    match = _UMO_RE.fullmatch(umo)
    if not match or match.group(1) != expected_type:
        return ""
    return umo


def _target_records() -> list[dict[str, str]]:
    data = json_store.read_json(COMPANIONS, {})
    config = json_store.read_json(PC_CONFIG, {})
    users = data.get("users") if isinstance(data.get("users"), dict) else {}
    groups = data.get("groups") if isinstance(data.get("groups"), dict) else {}
    records: list[dict[str, str]] = []

    for raw_id, raw_user in users.items():
        if not isinstance(raw_user, dict):
            continue
        if raw_user.get("enabled", True) is False or raw_user.get("manual_disabled") is True:
            continue
        umo = _valid_umo(
            raw_user.get("umo") or raw_user.get("last_umo") or raw_user.get("last_inbound_umo"),
            "FriendMessage",
        )
        if not umo:
            continue
        user_id = str(raw_user.get("user_id") or raw_id).strip()
        display = str(
            raw_user.get("nickname")
            or raw_user.get("last_display_name")
            or raw_user.get("qq_display_name")
            or "已知私聊用户"
        ).strip()[:80]
        records.append(
            {
                "target_key": _target_key("private", umo),
                "kind": "private",
                "display_name": display,
                "id_hint": _mask_identifier(user_id),
                "umo": umo,
            }
        )

    mode = str(config.get("group_access_mode") or "whitelist").strip().lower()
    whitelist = set(_tokens(config.get("group_whitelist_ids") or config.get("target_group_ids")))
    blacklist = set(_tokens(config.get("group_blacklist_ids")))
    for raw_id, raw_group in groups.items():
        if not isinstance(raw_group, dict) or raw_group.get("enabled", True) is False:
            continue
        group_id = str(raw_group.get("group_id") or raw_id).strip()
        if mode == "whitelist" and (not whitelist or group_id not in whitelist):
            continue
        if mode == "blacklist" and group_id in blacklist:
            continue
        umo = _valid_umo(raw_group.get("umo"), "GroupMessage")
        if not umo:
            continue
        display = str(raw_group.get("name") or raw_group.get("group_name") or "已知群聊").strip()[:80]
        records.append(
            {
                "target_key": _target_key("group", umo),
                "kind": "group",
                "display_name": display,
                "id_hint": _mask_identifier(group_id),
                "umo": umo,
            }
        )

    records.sort(key=lambda item: (item["kind"], item["display_name"].lower(), item["id_hint"]))
    return records[:300]


def list_targets() -> list[dict[str, str]]:
    return [
        {key: value for key, value in record.items() if key != "umo"}
        for record in _target_records()
    ]


def _resolve_target(target_key: str) -> dict[str, str]:
    needle = str(target_key or "").strip()
    for record in _target_records():
        if record["target_key"] == needle:
            return record
    raise ValueError("目标不存在、已禁用或已不在当前允许范围")


def _persona_key(store_key: str) -> str:
    digest = hashlib.sha256(f"persona\0{store_key}".encode("utf-8")).hexdigest()[:20]
    return f"persona:{digest}"


def _persona_records() -> list[dict[str, Any]]:
    data = json_store.read_json(PERSONA_LIB, {})
    if not isinstance(data, dict) or not data:
        data = json_store.read_json(PERSONA_LIB_ALT, {})
    if not isinstance(data, dict):
        data = {}
    records: list[dict[str, Any]] = [
        {
            "persona_key": DEFAULT_PERSONA_KEY,
            "display_name": "主 Bot 人格",
            "tags": [],
            "store_key": "",
        }
    ]
    for raw_key, raw_entry in data.items():
        if not isinstance(raw_entry, dict) or str(raw_key).startswith("_"):
            continue
        if str(raw_entry.get("scope") or "shared") == "private":
            continue
        if str(raw_entry.get("kind") or "persona") != "persona":
            continue
        names = raw_entry.get("names") if isinstance(raw_entry.get("names"), list) else []
        display = next((str(item).strip()[:80] for item in names if str(item).strip()), "共享人设")
        tags = raw_entry.get("tags") if isinstance(raw_entry.get("tags"), list) else []
        records.append(
            {
                "persona_key": _persona_key(str(raw_key)),
                "display_name": display,
                "tags": [str(item).strip()[:40] for item in tags[:8] if str(item).strip()],
                "store_key": str(raw_key),
            }
        )
    records[1:] = sorted(records[1:], key=lambda item: (item["display_name"].lower(), item["persona_key"]))
    return records[:301]


def list_personas() -> list[dict[str, Any]]:
    return [
        {key: value for key, value in record.items() if key != "store_key"}
        for record in _persona_records()
    ]


def _resolve_persona(persona_key: str) -> dict[str, Any]:
    needle = str(persona_key or DEFAULT_PERSONA_KEY).strip()
    for record in _persona_records():
        if record["persona_key"] == needle:
            return record
    raise ValueError("指定人格不存在、已改为私密或已被删除")


def _resolve_draft_target(target_key: str) -> dict[str, str]:
    if str(target_key or "").strip() == PREVIEW_TARGET_KEY:
        return {
            "target_key": PREVIEW_TARGET_KEY,
            "kind": "preview",
            "display_name": "仅网页问答 / 预览",
            "id_hint": "不发送",
            "umo": "",
        }
    return _resolve_target(target_key)

def _clean_content(value: Any, limit: int, field: str) -> str:
    text = str(value or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not text:
        raise ValueError(f"{field}不能为空")
    if len(text) > limit:
        raise ValueError(f"{field}最多 {limit} 字符")
    if _SENSITIVE_VALUE_RE.search(text):
        raise ValueError(f"{field}疑似包含凭据，已拒绝进入消息队列")
    return text


def _append_job(job: dict[str, Any]) -> dict[str, Any]:
    with _locked_queue() as path:
        data = _read_queue(path)
        jobs = [item for item in data["jobs"] if isinstance(item, dict)]
        active = sum(1 for item in jobs if item.get("status") in _ACTIVE_STATUSES)
        if active >= _MAX_ACTIVE_JOBS:
            raise ValueError("消息队列繁忙，请等待现有任务完成")
        jobs.append(job)
        if len(jobs) > _MAX_STORED_JOBS:
            active_jobs = [item for item in jobs if item.get("status") in _ACTIVE_STATUSES]
            finished = [item for item in jobs if item.get("status") not in _ACTIVE_STATUSES]
            jobs = active_jobs + finished[-max(0, _MAX_STORED_JOBS - len(active_jobs)) :]
        data["jobs"] = jobs
        _write_queue(path, data)
    return _public_job(job)


def create_draft_job(
    *,
    target_key: str,
    prompt: str,
    requester: str,
    persona_key: str = DEFAULT_PERSONA_KEY,
) -> dict[str, Any]:
    target = _resolve_draft_target(target_key)
    persona = _resolve_persona(persona_key)
    now = time.time()
    job = {
        "id": uuid.uuid4().hex,
        "type": "draft",
        "status": "pending",
        "target_key": target["target_key"],
        "target_kind": target["kind"],
        "target_display_name": target["display_name"],
        "target_id_hint": target["id_hint"],
        "target_umo": target["umo"],
        "persona_key": persona["persona_key"],
        "persona_display_name": persona["display_name"],
        "prompt": _clean_content(prompt, 4000, "问题或草稿要求"),
        "requested_by": str(requester or "unknown")[:120],
        "created_at": now,
        "updated_at": now,
        "attempts": 0,
    }
    return _append_job(job)


def create_send_job(
    *,
    target_key: str,
    message: str,
    requester: str,
    source_draft_id: str = "",
) -> dict[str, Any]:
    target = _resolve_target(target_key)
    now = time.time()
    job = {
        "id": uuid.uuid4().hex,
        "type": "send",
        "status": "pending",
        "target_key": target["target_key"],
        "target_kind": target["kind"],
        "target_display_name": target["display_name"],
        "target_id_hint": target["id_hint"],
        "target_umo": target["umo"],
        "message": _clean_content(message, 2000, "发送内容"),
        "source_draft_id": str(source_draft_id or "")[:64],
        "requested_by": str(requester or "unknown")[:120],
        "created_at": now,
        "updated_at": now,
        "attempts": 0,
    }
    return _append_job(job)


def _public_job(job: dict[str, Any]) -> dict[str, Any]:
    allowed = (
        "id",
        "type",
        "status",
        "target_key",
        "target_kind",
        "target_display_name",
        "target_id_hint",
        "persona_key",
        "persona_display_name",
        "prompt",
        "message",
        "draft",
        "requested_by",
        "source_draft_id",
        "created_at",
        "updated_at",
        "completed_at",
        "attempts",
        "error",
        "delivery_path",
    )
    result = {key: job.get(key) for key in allowed if key in job}
    if "error" in result:
        result["error"] = str(result["error"] or "")[:300]
    return result


def list_jobs(limit: int = 100) -> dict[str, Any]:
    with _locked_queue() as path:
        jobs = [item for item in _read_queue(path)["jobs"] if isinstance(item, dict)]
    jobs.sort(key=lambda item: float(item.get("created_at") or 0), reverse=True)
    limit = max(1, min(int(limit), 200))
    return {"items": [_public_job(item) for item in jobs[:limit]], "total": len(jobs)}


def cancel_job(job_id: str, requester: str) -> dict[str, Any]:
    needle = str(job_id or "").strip()
    with _locked_queue() as path:
        data = _read_queue(path)
        for job in data["jobs"]:
            if not isinstance(job, dict) or job.get("id") != needle:
                continue
            if job.get("status") in {"processing", "sending", "sent", "uncertain"}:
                raise ValueError("任务正在处理、已发送或投递状态不确定，不能取消")
            job["status"] = "cancelled"
            job["cancelled_by"] = str(requester or "unknown")[:120]
            job["updated_at"] = time.time()
            _write_queue(path, data)
            return _public_job(job)
    raise KeyError("job not found")

