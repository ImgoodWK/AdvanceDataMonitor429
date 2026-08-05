from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..auth import require_perm
from ..config import settings
from .. import db as dbmod, docker_api, json_store
router = APIRouter(prefix="/api", tags=["ops"])

class RestoreBackupBody(BaseModel):
    snapshot: str = Field(min_length=1, max_length=80)
    files: list[str] | None = None
    confirm: str = Field(min_length=1, max_length=80)


@router.get("/audit")
def list_audit(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    action: str | None = None,
    outcome: str | None = None,
    q: str | None = None,
    _user=require_perm("audit.view"),
):
    return dbmod.list_audit_events(
        limit=limit, offset=offset, action=action, outcome=outcome, query=q
    )


@router.get("/backups")
def list_backups(
    limit: int = Query(default=100, ge=1, le=500),
    _user=require_perm("backups.view"),
):
    return json_store.list_backups(limit=limit)


@router.post("/backups/restore")
def restore_backup(body: RestoreBackupBody, _user=require_perm("backups.restore")):
    if body.confirm != body.snapshot:
        raise HTTPException(400, "确认文本必须与快照名完全一致")
    try:
        return json_store.restore_backup(body.snapshot, body.files)
    except FileNotFoundError as exc:
        raise HTTPException(404, f"备份或文件不存在: {exc}") from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc



@router.post("/ops/restart-astrbot")
def restart_astrbot(_user=require_perm("ops.restart")):
    try:
        msg = docker_api.restart_container(settings.astrbot_container)
        return {"ok": True, "output": msg}
    except Exception as e:
        raise HTTPException(500, str(e)) from e


@router.get("/ops/logs")
def get_logs(tail: int = Query(default=80, ge=10, le=500), _user=require_perm("ops.view")):
    try:
        text = docker_api.container_logs(settings.astrbot_container, tail=tail)
        return {"ok": True, "logs": text[-50000:]}
    except Exception as e:
        raise HTTPException(500, str(e)) from e


@router.get("/ops/health")
def health():
    return {
        "ok": True,
        "astrbot_data": str(settings.astrbot_data),
        "astrbot_data_exists": settings.astrbot_data.exists(),
    }
