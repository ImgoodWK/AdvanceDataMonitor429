import re
from datetime import datetime, timezone
from typing import Any

from .. import json_store
from ..config import PERSONA_LIB, PERSONA_LIB_ALT

_KINDS = {"persona", "memory", "knowledge"}
_SENSITIVE = re.compile(
    r"(?i)(?:api[_ -]?key|access[_ -]?token|client[_ -]?secret|password|passwd|"
    r"bearer\s+[a-z0-9._-]+|sk-[a-z0-9_-]{8,}|验证码|口令|密钥|私钥)"
)


def _load_raw() -> tuple[str, dict[str, Any]]:
    """Read legacy data when needed, but always write the authoritative primary path."""
    primary = json_store.read_json(PERSONA_LIB, {})
    if isinstance(primary, dict) and primary:
        return PERSONA_LIB, primary
    alt = json_store.read_json(PERSONA_LIB_ALT, {})
    if isinstance(alt, dict) and alt:
        return PERSONA_LIB, dict(alt)
    return PERSONA_LIB, primary if isinstance(primary, dict) else {}


def _string_list(value: Any, limit: int = 80) -> list[str]:
    if isinstance(value, str):
        values = re.split(r"[|｜,/，、\s]+", value)
    elif isinstance(value, list):
        values = value
    else:
        values = []
    out: list[str] = []
    for item in values:
        text = str(item or "").strip()[:80]
        if text and text not in out:
            out.append(text)
        if len(out) >= limit:
            break
    return out


def _attributes(value: Any) -> dict[str, str]:
    if not isinstance(value, dict):
        return {}
    out: dict[str, str] = {}
    for raw_key, raw_value in value.items():
        key = re.sub(r"\s+", " ", str(raw_key or "").strip())[:64]
        val = str(raw_value or "").strip()[:4000]
        if key and val:
            out[key] = val
        if len(out) >= 100:
            break
    return out


def _normalize_entry(user_id: str, value: dict[str, Any]) -> dict[str, Any]:
    entry = dict(value)
    entry["kind"] = str(entry.get("kind") or "persona")
    if entry["kind"] not in _KINDS:
        entry["kind"] = "persona"
    entry["scope"] = "private" if str(entry.get("scope") or "shared") == "private" else "shared"
    entry["owner_id"] = str(entry.get("owner_id") or "") if entry["scope"] == "private" else ""
    entry["names"] = _string_list(entry.get("names"))
    entry["tags"] = _string_list(entry.get("tags"))
    entry["attributes"] = _attributes(entry.get("attributes"))
    entry["contributors"] = _string_list(entry.get("contributors"), limit=50)
    if entry["kind"] == "persona":
        entry["platform"] = str(entry.get("platform") or "qq_official")[:80]
        if not str(user_id).startswith(("name:", "private:")):
            entry["subject_id"] = str(entry.get("subject_id") or user_id)[:180]
    for field in ("appearance", "personality", "content", "extra", "topic"):
        if field in entry:
            entry[field] = str(entry.get(field) or "")[:4000]
    return entry


def list_personas() -> list[dict[str, Any]]:
    path, raw = _load_raw()
    out = []
    for uid, val in (raw or {}).items():
        if not isinstance(val, dict) or str(uid).startswith("_"):
            continue
        data = _normalize_entry(str(uid), val)
        out.append(
            {
                "user_id": str(uid),
                "names": data.get("names") or [],
                "tags": data.get("tags") or [],
                "attributes": data.get("attributes") or {},
                "appearance": data.get("appearance") or "",
                "personality": data.get("personality") or "",
                "extra": data.get("extra") or "",
                "contributors": data.get("contributors") or [],
                "kind": data.get("kind"),
                "scope": data.get("scope"),
                "last_updated": data.get("_last_updated") or "",
                "data": data,
                "source_path": path,
            }
        )
    out.sort(key=lambda x: (x["kind"] != "persona", (x["names"] or [x["user_id"]])[0]))
    return out


def get_persona(user_id: str) -> dict[str, Any] | None:
    path, raw = _load_raw()
    val = (raw or {}).get(str(user_id))
    if not isinstance(val, dict):
        return None
    return {"user_id": str(user_id), "data": _normalize_entry(str(user_id), val), "source_path": path}


def upsert_persona(user_id: str, patch: dict[str, Any]) -> dict[str, Any]:
    path, raw = _load_raw()
    uid = str(user_id).strip()
    if not uid or uid.startswith("_") or len(uid) > 180:
        raise ValueError("invalid persona id")
    if _SENSITIVE.search(str(patch)):
        raise ValueError("人设库禁止保存密码、Key、Token 或其他凭据")
    entry = raw.get(uid)
    if not isinstance(entry, dict):
        entry = {
            "kind": "persona",
            "scope": "shared",
            "owner_id": "",
            "names": [],
            "tags": [],
            "attributes": {},
            "appearance": "",
            "personality": "",
            "content": "",
            "extra": "",
            "contributors": ["console"],
        }
    for key, value in patch.items():
        key = str(key)
        if key.startswith("_") and key != "_last_updated":
            continue
        if value is None:
            entry.pop(key, None)
        else:
            entry[key] = value
    entry = _normalize_entry(uid, entry)
    contributors = _string_list(entry.get("contributors"), limit=50)
    if "console" not in contributors:
        contributors.append("console")
    entry["contributors"] = contributors
    entry["_last_updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    raw[uid] = entry
    json_store.write_json(PERSONA_LIB, raw)
    return {"user_id": uid, "data": entry, "source_path": PERSONA_LIB}


def delete_persona(user_id: str) -> None:
    _path, raw = _load_raw()
    uid = str(user_id)
    if uid not in raw:
        raise KeyError("persona not found")
    del raw[uid]
    json_store.write_json(PERSONA_LIB, raw)


def export_personas() -> dict[str, Any]:
    _path, raw = _load_raw()
    return {
        "schema_version": 3,
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "personas": {key: value for key, value in raw.items() if isinstance(value, dict) and not str(key).startswith("_")},
    }


def import_personas(payload: dict[str, Any], mode: str = "merge") -> dict[str, Any]:
    source = payload.get("personas") if isinstance(payload.get("personas"), dict) else payload
    if not isinstance(source, dict):
        raise ValueError("personas must be an object")
    _path, current = _load_raw()
    merged: dict[str, Any] = {} if mode == "replace" else dict(current)
    imported = 0
    for raw_uid, raw_entry in source.items():
        uid = str(raw_uid).strip()
        if not uid or uid.startswith("_") or len(uid) > 180 or not isinstance(raw_entry, dict):
            continue
        if _SENSITIVE.search(str(raw_entry)):
            raise ValueError(f"{uid} 含疑似凭据，已拒绝导入")
        merged[uid] = _normalize_entry(uid, raw_entry)
        imported += 1
    json_store.write_json(PERSONA_LIB, merged)
    return {"ok": True, "mode": mode, "imported": imported, "total": len(merged)}
