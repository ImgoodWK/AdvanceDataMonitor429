from typing import Any

from fastapi import Cookie, Depends, Header, HTTPException, Request, Response
from itsdangerous import BadSignature, URLSafeTimedSerializer

from . import db as dbmod
from .config import settings

serializer = URLSafeTimedSerializer(settings.session_secret, salt="textech-console")


def make_session_token(user_id: int, username: str, role: str) -> str:
    return serializer.dumps({"uid": user_id, "username": username, "role": role})


def load_session(token: str) -> dict[str, Any] | None:
    try:
        data = serializer.loads(token, max_age=settings.session_max_age)
        if not isinstance(data, dict) or "uid" not in data:
            return None
        return data
    except BadSignature:
        return None


def set_session_cookie(response: Response, token: str) -> None:
    response.set_cookie(
        key=settings.cookie_name,
        value=token,
        httponly=True,
        samesite="lax",
        secure=settings.cookie_secure,
        max_age=settings.session_max_age,
        path="/",
    )


def clear_session_cookie(response: Response) -> None:
    response.delete_cookie(settings.cookie_name, path="/")


def enrich_user(user: dict[str, Any]) -> dict[str, Any]:
    perms = dbmod.resolve_permissions(user)
    return {
        "id": user["id"],
        "username": user["username"],
        "role": user["role"],
        "grants": user.get("grants") or [],
        "denies": user.get("denies") or [],
        "permissions": perms,
    }

def attach_authenticated_user(request: Request, user: dict[str, Any]) -> dict[str, Any]:
    enriched = enrich_user(user)
    request.state.authenticated_user = enriched
    return enriched



def current_user(
    request: Request,
    textech_session: str | None = Cookie(default=None, alias=settings.cookie_name),
    authorization: str | None = Header(default=None),
    x_api_token: str | None = Header(default=None),
) -> dict[str, Any]:
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization[7:].strip()
        user = dbmod.find_user_by_api_token(token)
        if user:
            return attach_authenticated_user(request, user)
    if x_api_token:
        user = dbmod.find_user_by_api_token(x_api_token.strip())
        if user:
            return attach_authenticated_user(request, user)
    if textech_session:
        data = load_session(textech_session)
        if data:
            user = dbmod.get_user_by_id(int(data["uid"]))
            if user:
                return attach_authenticated_user(request, user)
    raise HTTPException(status_code=401, detail="未登录")


def user_has_perm(user: dict[str, Any], perm: str) -> bool:
    perms = user.get("permissions")
    if perms is None:
        perms = dbmod.resolve_permissions(user)
    return perm in perms


def require_perm(*perms: str):
    """Require ANY of the listed permissions (OR). Returns a FastAPI Depends()."""

    def dep(user: dict[str, Any] = Depends(current_user)) -> dict[str, Any]:
        if not any(user_has_perm(user, p) for p in perms):
            raise HTTPException(status_code=403, detail=f"权限不足，需要: {', '.join(perms)}")
        return user

    return Depends(dep)


def require_all_perms(*perms: str):
    def dep(user: dict[str, Any] = Depends(current_user)) -> dict[str, Any]:
        missing = [p for p in perms if not user_has_perm(user, p)]
        if missing:
            raise HTTPException(status_code=403, detail=f"权限不足，缺少: {', '.join(missing)}")
        return user

    return Depends(dep)


# Backward-compatible role shortcuts (map to permissions)
RequireAdmin = require_perm("console.manage")
RequireEditor = require_perm("bot_users.edit", "personas.edit", "memories.edit", "config.edit")
RequireViewer = require_perm(
    "bot_users.view", "personas.view", "memories.view", "config.view", "ops.view"
)
