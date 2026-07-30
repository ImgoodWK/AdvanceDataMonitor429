from typing import Any

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..auth import require_perm
from ..config import SOULMAP_FIELDS
from ..services import companion, persona_lib, soulmap

router = APIRouter(prefix="/api/personas", tags=["personas"])


class PatchPersonaBody(BaseModel):
    fields: dict[str, Any] = Field(default_factory=dict)


class ImportPersonaBody(BaseModel):
    data: dict[str, Any] = Field(default_factory=dict)
    mode: str = "merge"


@router.get("")
def list_all(q: str | None = None, _user=require_perm("personas.view")):
    """List the authoritative persona_lib plus legacy SoulMap-only profiles."""
    profiles = soulmap.list_profiles()
    personas = {p["user_id"]: p for p in persona_lib.list_personas()}
    items = []
    needle = (q or "").lower().strip()
    for row in profiles:
        uid = row["user_id"]
        c = companion.get_user(uid)
        ident = companion.user_identity(c, uid)
        pl = personas.get(uid)
        item = {
            **row,
            "qq_openid": ident["qq_openid"],
            "qq_display_name": ident["qq_display_name"],
            "companion_nickname": ident["companion_nickname"],
            "umo": ident["umo"],
            "persona_lib": (pl or {}).get("data") if pl else None,
            "persona_names": (pl or {}).get("names") if pl else [],
        }
        if needle:
            blob = " ".join(
                [
                    uid,
                    item.get("address") or "",
                    item.get("qq_display_name") or "",
                    item.get("companion_nickname") or "",
                    item.get("notes") or "",
                    " ".join(item.get("persona_names") or []),
                    str(item.get("data") or ""),
                ]
            ).lower()
            if needle not in blob:
                continue
        items.append(item)

    # Include persona_lib-only users without soulmap
    for uid, pl in personas.items():
        if any(i["user_id"] == uid for i in items):
            continue
        c = companion.get_user(uid)
        ident = companion.user_identity(c, uid)
        item = {
            "user_id": uid,
            "address": "",
            "gender": "",
            "notes": "",
            "last_updated": (pl.get("data") or {}).get("_last_updated") or "",
            "field_keys": [],
            "data": {},
            "fields": {},
            "qq_openid": ident["qq_openid"],
            "qq_display_name": ident["qq_display_name"],
            "companion_nickname": ident["companion_nickname"],
            "umo": ident["umo"],
            "persona_lib": pl.get("data"),
            "persona_names": pl.get("names") or [],
        }
        if needle:
            blob = " ".join(
                [
                    uid,
                    item.get("qq_display_name") or "",
                    " ".join(item.get("persona_names") or []),
                    str(item.get("persona_lib") or ""),
                ]
            ).lower()
            if needle not in blob:
                continue
        items.append(item)

    return {
        "items": items,
        "total": len(items),
        "soulmap_fields": SOULMAP_FIELDS,
    }


@router.get("/export/all")
def export_all(_user=require_perm("personas.view")):
    return persona_lib.export_personas()


@router.post("/import")
def import_all(body: ImportPersonaBody, _user=require_perm("personas.edit")):
    if body.mode not in {"merge", "replace"}:
        raise HTTPException(400, "mode must be merge or replace")
    try:
        return persona_lib.import_personas(body.data, body.mode)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.get("/{user_id}")
def get_one(user_id: str, _user=require_perm("personas.view")):
    p = soulmap.get_profile(user_id)
    pl = persona_lib.get_persona(user_id)
    if p is None and pl is None:
        raise HTTPException(404, "persona not found")
    c = companion.get_user(user_id)
    keys = list(SOULMAP_FIELDS)
    if p:
        for k in p.keys():
            if k not in keys and not str(k).startswith("_"):
                keys.append(k)
    return {
        "user_id": user_id,
        "identity": companion.user_identity(c, user_id),
        "profile": p,
        "persona_lib": (pl or {}).get("data") if pl else None,
        "notes": soulmap.split_notes((p or {}).get("备注")),
        "soulmap_fields": keys,
        "raw": p,
    }


@router.patch("/{user_id}")
def patch_one(user_id: str, body: PatchPersonaBody, _user=require_perm("personas.edit")):
    try:
        return persona_lib.upsert_persona(user_id, body.fields)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.put("/{user_id}")
def put_one(user_id: str, body: PatchPersonaBody, _user=require_perm("personas.edit")):
    """Create or merge an authoritative persona_lib record."""
    return patch_one(user_id, body, _user)


@router.delete("/{user_id}")
def delete_one(user_id: str, _user=require_perm("personas.edit")):
    try:
        persona_lib.delete_persona(user_id)
    except KeyError:
        raise HTTPException(404, "persona not found") from None
    return {"ok": True}


@router.patch("/{user_id}/legacy-soulmap")
def patch_legacy_soulmap(user_id: str, body: PatchPersonaBody, _user=require_perm("personas.edit")):
    return soulmap.upsert_profile(user_id, body.fields, create=True)


@router.delete("/{user_id}/legacy-soulmap")
def delete_legacy_soulmap(user_id: str, _user=require_perm("personas.edit")):
    soulmap.delete_profile(user_id)
    return {"ok": True}
