from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from ..auth import require_perm
from ..services import runtime_profile

router = APIRouter(prefix="/api/runtime-profile", tags=["runtime-profile"])


class PatchRuntimeProfileBody(BaseModel):
    fields: dict[str, Any] = Field(default_factory=dict)


@router.get("")
def get_profile(_user=require_perm("config.view")):
    return runtime_profile.get_runtime_profile()


@router.patch("")
def patch_profile(body: PatchRuntimeProfileBody, _user=require_perm("config.edit")):
    try:
        return runtime_profile.patch_runtime_profile(body.fields)
    except (TypeError, ValueError) as exc:
        raise HTTPException(400, str(exc)) from exc
