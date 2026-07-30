from typing import Any

from .. import json_store
from ..config import COMPANION_EDITABLE, COMPANIONS, PC_CONFIG


def load_companions() -> dict[str, Any]:
    data = json_store.read_json(COMPANIONS, {"users": {}})
    if "users" not in data or not isinstance(data["users"], dict):
        data["users"] = {}
    return data


def list_user_ids() -> list[str]:
    return sorted(str(k) for k in load_companions()["users"].keys())


def get_user(user_id: str) -> dict[str, Any] | None:
    users = load_companions()["users"]
    u = users.get(str(user_id))
    return dict(u) if isinstance(u, dict) else None


def parse_umo(umo: str | None) -> dict[str, str]:
    """Parse textech-qq:FriendMessage:<openid> style UMO."""
    raw = (umo or "").strip()
    if not raw:
        return {"platform": "", "message_type": "", "qq_openid": ""}
    parts = raw.split(":")
    if len(parts) >= 3:
        return {
            "platform": parts[0],
            "message_type": parts[1],
            "qq_openid": parts[-1],
        }
    return {"platform": "", "message_type": "", "qq_openid": raw}


def user_identity(user: dict[str, Any] | None, user_id: str) -> dict[str, Any]:
    u = user or {}
    umo = u.get("umo") or ""
    parsed = parse_umo(umo if isinstance(umo, str) else "")
    openid = parsed["qq_openid"] or str(user_id)
    display = u.get("last_display_name") or ""
    observed = u.get("observed_display_names") or []
    if not display and isinstance(observed, list) and observed:
        display = str(observed[0] or "")
    return {
        "user_id": str(user_id),
        "qq_openid": openid,
        "qq_display_name": display,
        "observed_display_names": observed if isinstance(observed, list) else [],
        "companion_nickname": u.get("nickname") or "",
        "umo": umo,
        "platform": parsed["platform"],
        "message_type": parsed["message_type"],
    }


def create_user(user_id: str, fields: dict[str, Any] | None = None) -> dict[str, Any]:
    data = load_companions()
    uid = str(user_id)
    if uid in data["users"]:
        raise ValueError("user already exists")
    entry: dict[str, Any] = {
        "enabled": True,
        "manual_enabled": True,
        "nickname": "",
        "relationship_role": "",
        "style": "",
        "umo": "",
        "proactive_daily_limit": 5,
        "proactive_idle_minutes": 120,
        "proactive_min_interval_minutes": 30,
        "photo_daily_limit": 10,
        "screen_peek_daily_limit": 3,
        "poke_daily_limit": 5,
        "suspended_proactive": False,
        "simulation_mode": False,
        "companion_memory": {"items": [], "updated_at": ""},
    }
    if fields:
        for k, v in fields.items():
            if k in COMPANION_EDITABLE:
                entry[k] = v
    data["users"][uid] = entry
    json_store.write_json(COMPANIONS, data)
    return entry


def patch_user(user_id: str, patch: dict[str, Any]) -> dict[str, Any]:
    data = load_companions()
    uid = str(user_id)
    if uid not in data["users"]:
        raise KeyError("user not found")
    entry = data["users"][uid]
    for k, v in patch.items():
        if k in COMPANION_EDITABLE:
            entry[k] = v
    json_store.write_json(COMPANIONS, data)
    return entry


def disable_user(user_id: str) -> dict[str, Any]:
    return patch_user(user_id, {"enabled": False, "manual_enabled": False})


def get_pc_config() -> dict[str, Any]:
    return json_store.read_json(PC_CONFIG, {})


def patch_pc_config(patch: dict[str, Any]) -> dict[str, Any]:
    cfg = json_store.read_json(PC_CONFIG, {})
    cfg.update(patch)
    json_store.write_json(PC_CONFIG, cfg)
    return cfg
