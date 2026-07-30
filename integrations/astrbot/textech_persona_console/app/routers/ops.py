from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..auth import require_perm
from ..config import settings
from .. import db as dbmod, docker_api, json_store
from ..services import knowledge as know

router = APIRouter(prefix="/api", tags=["knowledge-ops"])


class KnowledgePutBody(BaseModel):
    content: str


class ChangelogBody(BaseModel):
    summary: str
    author: str = "ops"
    details: str = ""

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



@router.get("/knowledge")
def list_knowledge(_user=require_perm("knowledge.view")):
    return {"docs": know.list_docs()}


@router.get("/knowledge/export")
def export_knowledge(_user=require_perm("knowledge.view")):
    return {"files": know.export_all()}


@router.get("/knowledge/{path:path}")
def get_knowledge(path: str, _user=require_perm("knowledge.view")):
    try:
        return {"path": path, "content": know.read_doc(path)}
    except FileNotFoundError:
        raise HTTPException(404, "not found") from None
    except ValueError as e:
        raise HTTPException(400, str(e)) from e


@router.put("/knowledge/{path:path}")
def put_knowledge(path: str, body: KnowledgePutBody, _user=require_perm("knowledge.edit")):
    try:
        know.write_doc(path, body.content)
    except ValueError as e:
        raise HTTPException(400, str(e)) from e
    return {"ok": True, "path": path}


@router.post("/knowledge/changelog")
def append_changelog(body: ChangelogBody, _user=require_perm("knowledge.edit")):
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    entry = f"## {ts} — {body.summary}\n\n- author: {body.author}\n"
    if body.details:
        entry += f"\n{body.details.strip()}\n"
    know.append_changelog(entry)
    return {"ok": True}


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
        "knowledge_dir": str(settings.knowledge_dir),
        "astrbot_data_exists": settings.astrbot_data.exists(),
    }
