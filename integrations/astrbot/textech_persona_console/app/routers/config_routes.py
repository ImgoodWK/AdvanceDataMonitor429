from typing import Any

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..auth import require_perm
from .. import json_store
from ..config import CMD_CONFIG, CONSOLE_BRIDGE_CONFIG, PC_CONFIG, SOULMAP_CONFIG
from ..services import companion, memory, soulmap
from ..services.mask import mask_config

router = APIRouter(prefix="/api", tags=["config-memory"])

PLUGIN_MAP = {
    "soulmap": SOULMAP_CONFIG,
    "private_companion": PC_CONFIG,
    "console_bridge": CONSOLE_BRIDGE_CONFIG,
    "cmd": CMD_CONFIG,
}


class PatchConfigBody(BaseModel):
    fields: dict[str, Any] = Field(default_factory=dict)
    reveal_secrets: bool = False


class CreateMemoryBody(BaseModel):
    user_id: str = Field(min_length=1)
    text: str = Field(min_length=1)
    kind: str = "note"
    weight: float = 1
    reason: str = "console"


class PatchMemoryBody(BaseModel):
    text: str | None = None
    kind: str | None = None
    weight: float | None = None
    reason: str | None = None


@router.get("/memories")
def search_all_memories(
    user_id: str | None = None,
    q: str | None = None,
    kind: str | None = None,
    _user=require_perm("memories.view"),
):
    """List memories. Empty filters return all companion_memory items."""
    return {
        "sources": memory.list_memory_sources(),
        "items": memory.search_memories(user_id=user_id, query=q, kind=kind),
        "total": len(memory.list_companion_memories(user_id=user_id, query=q, kind=kind)),
    }


@router.post("/memories")
def create_memory(body: CreateMemoryBody, _user=require_perm("memories.edit")):
    try:
        return memory.add_companion_memory(
            body.user_id,
            text=body.text,
            kind=body.kind,
            weight=body.weight,
            reason=body.reason,
        )
    except KeyError:
        raise HTTPException(404, "bot user not found in companions") from None
    except ValueError as e:
        raise HTTPException(400, str(e)) from e


@router.patch("/memories/{user_id}/{index}")
def patch_memory(user_id: str, index: int, body: PatchMemoryBody, _user=require_perm("memories.edit")):
    patch = {k: v for k, v in body.model_dump().items() if v is not None}
    try:
        return memory.update_companion_memory(user_id, index, patch)
    except KeyError:
        raise HTTPException(404, "user not found") from None
    except IndexError:
        raise HTTPException(404, "memory not found") from None
    except ValueError as e:
        raise HTTPException(400, str(e)) from e


@router.delete("/memories/{user_id}/{index}")
def delete_memory(user_id: str, index: int, _user=require_perm("memories.edit")):
    try:
        memory.delete_companion_memory(user_id, index)
    except KeyError:
        raise HTTPException(404, "user not found") from None
    except IndexError:
        raise HTTPException(404, "memory not found") from None
    return {"ok": True}


@router.get("/config/{plugin}")
def get_config(plugin: str, reveal: bool = False, user=require_perm("config.view")):
    if plugin not in PLUGIN_MAP:
        raise HTTPException(404, "unknown plugin")
    if plugin == "soulmap":
        data = soulmap.get_soulmap_config()
    elif plugin == "private_companion":
        data = companion.get_pc_config()
    else:
        data = json_store.read_json(PLUGIN_MAP[plugin], {})
    if reveal:
        from ..auth import user_has_perm

        if not user_has_perm(user, "config.secrets"):
            raise HTTPException(403, "需要 config.secrets 权限")
        return {"plugin": plugin, "masked": False, "config": data}
    return {"plugin": plugin, "masked": True, "config": mask_config(data)}


@router.patch("/config/{plugin}")
def patch_config(plugin: str, body: PatchConfigBody, user=require_perm("config.edit")):
    if plugin not in PLUGIN_MAP:
        raise HTTPException(404, "unknown plugin")
    from ..auth import user_has_perm

    if plugin == "cmd" and not user_has_perm(user, "config.secrets") and not user_has_perm(user, "console.manage"):
        raise HTTPException(403, "改全局配置需要更高权限")
    if plugin == "console_bridge" and not user_has_perm(user, "messages.send"):
        raise HTTPException(403, "改网页投递桥配置需要 messages.send 权限")
    if plugin == "soulmap":
        data = soulmap.patch_soulmap_config(body.fields)
    elif plugin == "private_companion":
        data = companion.patch_pc_config(body.fields)
    else:
        config_path = PLUGIN_MAP[plugin]
        cfg = json_store.read_json(config_path, {})
        cfg.update(body.fields)
        json_store.write_json(config_path, cfg)
        data = cfg
    return {"plugin": plugin, "config": mask_config(data)}
