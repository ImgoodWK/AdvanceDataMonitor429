from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from .. import db as dbmod
from ..auth import require_perm
from ..config import ALL_PERMISSIONS

router = APIRouter(prefix="/api/console-users", tags=["console-users"])


class CreateUserBody(BaseModel):
    username: str = Field(min_length=2, max_length=64)
    password: str = Field(min_length=6, max_length=128)
    role: str = "editor"
    grants: list[str] = Field(default_factory=list)
    denies: list[str] = Field(default_factory=list)


class UpdateUserBody(BaseModel):
    password: str | None = None
    role: str | None = None
    grants: list[str] | None = None
    denies: list[str] | None = None


class RoleBody(BaseModel):
    name: str = Field(min_length=2, max_length=64)
    label: str = ""
    permissions: list[str] = Field(default_factory=list)


class UpdateRoleBody(BaseModel):
    label: str | None = None
    permissions: list[str] | None = None


@router.get("")
def list_users(_user=require_perm("console.manage")):
    users = dbmod.list_users()
    for u in users:
        u["permissions"] = dbmod.resolve_permissions(u)
    return users


@router.post("")
def create_user(body: CreateUserBody, _user=require_perm("console.manage")):
    try:
        return dbmod.create_user(
            body.username,
            body.password,
            body.role,
            grants=body.grants,
            denies=body.denies,
        )
    except Exception as e:
        raise HTTPException(400, str(e)) from e


@router.patch("/{user_id}")
def update_user(user_id: int, body: UpdateUserBody, _user=require_perm("console.manage")):
    try:
        return dbmod.update_user(
            user_id,
            password=body.password,
            role=body.role,
            grants=body.grants,
            denies=body.denies,
        )
    except KeyError:
        raise HTTPException(404, "not found") from None
    except ValueError as e:
        raise HTTPException(400, str(e)) from e


@router.delete("/{user_id}")
def delete_user(user_id: int, user=require_perm("console.manage")):
    if user_id == user["id"]:
        raise HTTPException(400, "不能删除自己")
    dbmod.delete_user(user_id)
    return {"ok": True}


@router.get("/roles/catalog")
def permission_catalog(_user=require_perm("console.manage")):
    return {"permissions": ALL_PERMISSIONS, "roles": dbmod.list_roles()}


@router.get("/roles")
def list_roles(_user=require_perm("console.manage")):
    return {"roles": dbmod.list_roles(), "permissions": ALL_PERMISSIONS}


@router.post("/roles")
def create_role(body: RoleBody, _user=require_perm("console.manage")):
    unknown = [p for p in body.permissions if p not in ALL_PERMISSIONS]
    if unknown:
        raise HTTPException(400, f"未知权限: {', '.join(unknown)}")
    try:
        return dbmod.create_role(body.name, body.label or body.name, body.permissions)
    except ValueError as e:
        raise HTTPException(400, str(e)) from e


@router.patch("/roles/{name}")
def update_role(name: str, body: UpdateRoleBody, _user=require_perm("console.manage")):
    if body.permissions is not None:
        unknown = [p for p in body.permissions if p not in ALL_PERMISSIONS]
        if unknown:
            raise HTTPException(400, f"未知权限: {', '.join(unknown)}")
    try:
        return dbmod.update_role(name, label=body.label, permissions=body.permissions)
    except KeyError:
        raise HTTPException(404, "not found") from None
    except ValueError as e:
        raise HTTPException(400, str(e)) from e


@router.delete("/roles/{name}")
def delete_role(name: str, _user=require_perm("console.manage")):
    try:
        dbmod.delete_role(name)
    except KeyError:
        raise HTTPException(404, "not found") from None
    except ValueError as e:
        raise HTTPException(400, str(e)) from e
    return {"ok": True}
