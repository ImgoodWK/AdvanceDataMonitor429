from typing import Any

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..auth import require_perm
from ..config import COMPANION_EDITABLE, SOULMAP_FIELDS
from ..services import companion, memory, persona_lib, soulmap

router = APIRouter(prefix="/api/bot-users", tags=["bot-users"])


class CreateBotUserBody(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    companion: dict[str, Any] = Field(default_factory=dict)
    profile: dict[str, Any] = Field(default_factory=dict)
    create_profile: bool = True


class PatchCompanionBody(BaseModel):
    fields: dict[str, Any] = Field(default_factory=dict)


class PatchProfileBody(BaseModel):
    fields: dict[str, Any] = Field(default_factory=dict)


def _row_for(uid: str) -> dict[str, Any]:
    c = companion.get_user(uid)
    p = soulmap.get_profile(uid)
    persona = persona_lib.get_persona(uid)
    ident = companion.user_identity(c, uid)
    address = (p or {}).get("对用户的称呼") or ""
    persona_names = (persona or {}).get("data", {}).get("names") if persona else []
    return {
        **ident,
        "persona_address": address,
        "persona_names": persona_names or [],
        "nickname": ident["companion_nickname"] or address or ident["qq_display_name"] or "",
        "enabled": (c or {}).get("enabled"),
        "has_companion": c is not None,
        "has_profile": p is not None,
        "has_persona_lib": persona is not None,
        "photo_daily_limit": (c or {}).get("photo_daily_limit"),
        "last_seen": (c or {}).get("last_seen") or (c or {}).get("last_activity_at"),
        "memory_count": len(((c or {}).get("companion_memory") or {}).get("items") or [])
        if isinstance((c or {}).get("companion_memory"), dict)
        else 0,
    }


def _match_q(row: dict[str, Any], q: str) -> bool:
    if not q:
        return True
    needle = q.lower()
    blob_parts = [
        row.get("user_id"),
        row.get("qq_openid"),
        row.get("qq_display_name"),
        row.get("companion_nickname"),
        row.get("persona_address"),
        row.get("umo"),
        " ".join(row.get("persona_names") or []),
    ]
    blob = " ".join(str(x or "") for x in blob_parts).lower()
    return needle in blob


@router.get("")
def list_users(q: str | None = None, _user=require_perm("bot_users.view", "personas.view")):
    ids = set(companion.list_user_ids()) | set(soulmap.list_profile_ids())
    # also persona_lib ids
    for p in persona_lib.list_personas():
        ids.add(p["user_id"])
    items = []
    for uid in sorted(ids):
        row = _row_for(uid)
        if _match_q(row, q or ""):
            items.append(row)
    return {"users": items, "total": len(items)}


@router.post("")
def create_user(body: CreateBotUserBody, _user=require_perm("bot_users.edit")):
    try:
        entry = companion.create_user(body.user_id, body.companion)
    except ValueError as e:
        raise HTTPException(400, str(e)) from e
    profile = None
    if body.create_profile:
        profile = soulmap.upsert_profile(body.user_id, body.profile or {}, create=True)
    return {"user_id": body.user_id, "companion": entry, "profile": profile}


@router.get("/{user_id}")
def get_user(user_id: str, _user=require_perm("bot_users.view", "personas.view")):
    c = companion.get_user(user_id)
    p = soulmap.get_profile(user_id)
    persona = persona_lib.get_persona(user_id)
    if c is None and p is None and persona is None:
        raise HTTPException(404, "user not found")
    notes = soulmap.split_notes((p or {}).get("备注"))
    # Dynamic soulmap keys: known fields first, then extras from data
    profile_keys = list(SOULMAP_FIELDS)
    if p:
        for k in p.keys():
            if k not in profile_keys and not str(k).startswith("_"):
                profile_keys.append(k)
    return {
        "user_id": user_id,
        "identity": companion.user_identity(c, user_id),
        "companion": c,
        "profile": p,
        "persona_lib": (persona or {}).get("data") if persona else None,
        "notes": notes,
        "recent_photos": (c or {}).get("recent_photo_generations") or [],
        "editable_companion_fields": sorted(COMPANION_EDITABLE),
        "soulmap_fields": profile_keys,
        "summary": _row_for(user_id),
    }


@router.patch("/{user_id}/companion")
def patch_companion(user_id: str, body: PatchCompanionBody, _user=require_perm("bot_users.edit")):
    try:
        return companion.patch_user(user_id, body.fields)
    except KeyError:
        raise HTTPException(404, "companion user not found") from None


@router.patch("/{user_id}/profile")
def patch_profile(user_id: str, body: PatchProfileBody, _user=require_perm("personas.edit")):
    return soulmap.upsert_profile(user_id, body.fields, create=True)


@router.patch("/{user_id}/persona-lib")
def patch_persona_lib(user_id: str, body: PatchProfileBody, _user=require_perm("personas.edit")):
    return persona_lib.upsert_persona(user_id, body.fields)


@router.post("/{user_id}/disable")
def disable_user(user_id: str, _user=require_perm("bot_users.edit")):
    try:
        return companion.disable_user(user_id)
    except KeyError:
        raise HTTPException(404, "companion user not found") from None


@router.delete("/{user_id}/profile")
def delete_profile(user_id: str, _user=require_perm("personas.edit")):
    soulmap.delete_profile(user_id)
    return {"ok": True}


@router.get("/{user_id}/memories")
def get_memories(
    user_id: str,
    q: str | None = Query(default=None),
    _user=require_perm("memories.view", "bot_users.view"),
):
    c = companion.get_user(user_id)
    items = memory.search_memories(user_id=user_id, query=q)
    companion_ctx = None
    if c:
        companion_ctx = {
            "last_user_message": c.get("last_user_message"),
            "last_user_message_at": c.get("last_user_message_at"),
            "last_companion_message": c.get("last_companion_message"),
            "last_companion_message_at": c.get("last_companion_message_at"),
            "recent_proactive_topics": c.get("recent_proactive_topics"),
            "last_proactive_reason": c.get("last_proactive_reason"),
            "last_proactive_behavior_summary": c.get("last_proactive_behavior_summary"),
        }
    return {"user_id": user_id, "companion_context": companion_ctx, "items": items}
