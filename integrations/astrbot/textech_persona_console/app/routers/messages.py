from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..auth import require_perm
from ..services import message_queue

router = APIRouter(prefix="/api/messages", tags=["messages"])


class DraftMessageBody(BaseModel):
    target_key: str = Field(min_length=1, max_length=80)
    persona_key: str = Field(default="bot:default", min_length=1, max_length=80)
    prompt: str = Field(min_length=1, max_length=4000)


class SendMessageBody(BaseModel):
    target_key: str = Field(min_length=1, max_length=80)
    message: str = Field(min_length=1, max_length=2000)
    source_draft_id: str = Field(default="", max_length=64)
    confirm_target: str = Field(min_length=1, max_length=80)
    confirm_phrase: str = Field(min_length=1, max_length=20)


@router.get("/targets")
def list_targets(_user=require_perm("messages.view")):
    items = message_queue.list_targets()
    return {"items": items, "total": len(items)}


@router.get("/personas")
def list_personas(_user=require_perm("messages.view")):
    items = message_queue.list_personas()
    return {"items": items, "total": len(items)}


@router.get("/jobs")
def list_jobs(
    limit: int = Query(default=100, ge=1, le=200),
    _user=require_perm("messages.view"),
):
    return message_queue.list_jobs(limit=limit)


@router.post("/draft")
def create_draft(body: DraftMessageBody, user=require_perm("messages.compose")):
    try:
        return message_queue.create_draft_job(
            target_key=body.target_key,
            persona_key=body.persona_key,
            prompt=body.prompt,
            requester=user["username"],
        )
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.post("/send")
def enqueue_send(body: SendMessageBody, user=require_perm("messages.send")):
    if body.confirm_target != body.target_key or body.confirm_phrase != "SEND":
        raise HTTPException(400, "目标确认或 SEND 确认短语不匹配")
    try:
        return message_queue.create_send_job(
            target_key=body.target_key,
            message=body.message,
            source_draft_id=body.source_draft_id,
            requester=user["username"],
        )
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.delete("/jobs/{job_id}")
def cancel_job(job_id: str, user=require_perm("messages.send")):
    try:
        return message_queue.cancel_job(job_id, user["username"])
    except KeyError:
        raise HTTPException(404, "任务不存在") from None
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc

