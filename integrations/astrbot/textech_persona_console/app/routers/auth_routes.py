import secrets

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field

from .. import db as dbmod
from ..auth import (
    clear_session_cookie,
    current_user,
    enrich_user,
    make_session_token,
    require_perm,
    set_session_cookie,
)
from ..config import ALL_PERMISSIONS

router = APIRouter(prefix="/api/auth", tags=["auth"])


class LoginBody(BaseModel):
    username: str
    password: str


class TokenBody(BaseModel):
    label: str = ""


class ChangePasswordBody(BaseModel):
    current_password: str = Field(min_length=1)
    new_password: str = Field(min_length=6, max_length=128)


@router.post("/login")
def login(body: LoginBody, response: Response):
    user = dbmod.get_user_by_username(body.username)
    if not user or not dbmod.verify_password(body.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="用户名或密码错误")
    token = make_session_token(user["id"], user["username"], user["role"])
    set_session_cookie(response, token)
    return enrich_user(user)


@router.post("/logout")
def logout(response: Response):
    clear_session_cookie(response)
    return {"ok": True}


@router.get("/me")
def me(user=Depends(current_user)):
    return user


@router.get("/permissions")
def list_permission_catalog(_user=Depends(current_user)):
    return {"permissions": ALL_PERMISSIONS}


@router.post("/change-password")
def change_password(body: ChangePasswordBody, user=require_perm("account.password")):
    full = dbmod.get_user_with_password(user["id"])
    if not full or not dbmod.verify_password(body.current_password, full["password_hash"]):
        raise HTTPException(400, "当前密码不正确")
    dbmod.update_user(user["id"], password=body.new_password)
    return {"ok": True}


@router.post("/token")
def create_token(body: TokenBody, user=require_perm("console.manage")):
    raw = secrets.token_urlsafe(32)
    dbmod.create_api_token(user["id"], raw, body.label or "api")
    return {"token": raw, "label": body.label or "api"}