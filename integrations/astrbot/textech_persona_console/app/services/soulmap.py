from typing import Any

from .. import json_store
from ..config import SOULMAP_CONFIG, SOULMAP_FIELDS, SOULMAP_PROFILES


def _flatten_profiles(raw: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Resolve user_id -> profile, handling session_based nesting."""
    out: dict[str, dict[str, Any]] = {}
    for uid, val in (raw or {}).items():
        if not isinstance(val, dict):
            continue
        # Direct profile if it has known fields or 备注
        if any(k in val for k in SOULMAP_FIELDS) or "_last_updated" in val:
            out[str(uid)] = val
            continue
        # Nested sessions: pick richest
        best: dict[str, Any] | None = None
        best_score = -1
        for _sid, nested in val.items():
            if not isinstance(nested, dict):
                continue
            score = len([k for k in nested if k in SOULMAP_FIELDS or k == "备注"])
            if score > best_score:
                best_score = score
                best = nested
        if best is not None:
            out[str(uid)] = best
    return out


def list_profile_ids() -> list[str]:
    raw = json_store.read_json(SOULMAP_PROFILES, {})
    return sorted(_flatten_profiles(raw).keys())


def list_profiles() -> list[dict[str, Any]]:
    """Return all SoulMap profiles with full data (admin/editor both see all)."""
    raw = json_store.read_json(SOULMAP_PROFILES, {})
    flat = _flatten_profiles(raw)
    out = []
    for uid, profile in flat.items():
        fields = {k: v for k, v in profile.items() if not str(k).startswith("_") or k == "_last_updated"}
        out.append(
            {
                "user_id": uid,
                "address": profile.get("对用户的称呼") or "",
                "gender": profile.get("性别") or "",
                "notes": profile.get("备注") or "",
                "last_updated": profile.get("_last_updated") or "",
                "field_keys": sorted(k for k in profile.keys() if not str(k).startswith("_")),
                "data": profile,
                "fields": fields,
            }
        )
    out.sort(key=lambda x: x["user_id"])
    return out


def get_profile(user_id: str) -> dict[str, Any] | None:
    raw = json_store.read_json(SOULMAP_PROFILES, {})
    flat = _flatten_profiles(raw)
    return flat.get(str(user_id))


def upsert_profile(user_id: str, patch: dict[str, Any], *, create: bool = False) -> dict[str, Any]:
    raw = json_store.read_json(SOULMAP_PROFILES, {})
    uid = str(user_id)
    current = raw.get(uid)
    if current is None:
        if not create and uid not in _flatten_profiles(raw):
            # allow create empty
            pass
        profile: dict[str, Any] = {}
        raw[uid] = profile
    elif isinstance(current, dict) and any(k in current for k in SOULMAP_FIELDS):
        profile = current
    elif isinstance(current, dict):
        # session nested — write into richest or create default session
        flat = _flatten_profiles({uid: current})
        if uid in flat:
            # find key of richest
            best_key = None
            best_score = -1
            for sid, nested in current.items():
                if not isinstance(nested, dict):
                    continue
                score = len(nested)
                if score > best_score:
                    best_score = score
                    best_key = sid
            if best_key is None:
                best_key = "default"
                current[best_key] = {}
            profile = current[best_key]
        else:
            current["default"] = {}
            profile = current["default"]
    else:
        profile = {}
        raw[uid] = profile

    for k, v in patch.items():
        if k.startswith("_"):
            continue
        if v is None:
            profile.pop(k, None)
        else:
            profile[k] = v
    from datetime import datetime, timezone

    profile["_last_updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    json_store.write_json(SOULMAP_PROFILES, raw)
    return get_profile(uid) or profile


def delete_profile(user_id: str) -> None:
    raw = json_store.read_json(SOULMAP_PROFILES, {})
    uid = str(user_id)
    if uid in raw:
        del raw[uid]
        json_store.write_json(SOULMAP_PROFILES, raw)


def get_soulmap_config() -> dict[str, Any]:
    return json_store.read_json(SOULMAP_CONFIG, {})


def patch_soulmap_config(patch: dict[str, Any]) -> dict[str, Any]:
    cfg = json_store.read_json(SOULMAP_CONFIG, {})
    cfg.update(patch)
    json_store.write_json(SOULMAP_CONFIG, cfg)
    return cfg


def split_notes(notes: str | None) -> list[str]:
    if not notes:
        return []
    parts = [p.strip() for p in notes.replace("；", ";").split(";")]
    return [p for p in parts if p]
