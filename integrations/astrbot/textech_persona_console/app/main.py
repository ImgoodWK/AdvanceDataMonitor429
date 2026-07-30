import logging
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .auth import require_perm
from . import db as dbmod
from .config import settings
from .db import init_db
from .routers import auth_routes, bot_users, config_routes, console_users, messages, ops, personas, runtime_profile
from .services import knowledge as know

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"

app = FastAPI(title="TeXTech Console", version="2.3.0")
log = logging.getLogger("textech-console")


def _audit_action(method: str, path: str) -> str:
    method = method.upper()
    exact = {
        "/api/personas/import": "personas.import",
        "/api/runtime-profile": "runtime_profile.update",
        "/api/backups/restore": "backups.restore",
        "/api/ops/restart-astrbot": "ops.restart",
        "/api/knowledge/changelog": "knowledge.changelog",
        "/api/auth/change-password": "account.change_password",
        "/api/auth/token": "account.create_token",
        "/api/messages/draft": "messages.draft",
        "/api/messages/send": "messages.send",
    }
    if path in exact:
        return exact[path]
    prefixes = (
        ("/api/personas", "personas"),
        ("/api/memories", "memories"),
        ("/api/config", "config"),
        ("/api/bot-users", "bot_users"),
        ("/api/knowledge", "knowledge"),
        ("/api/messages", "messages"),
        ("/api/console-users/roles", "roles"),
        ("/api/console-users", "console_users"),
    )
    for prefix, domain in prefixes:
        if path == prefix or path.startswith(prefix + "/"):
            verb = {
                "POST": "create",
                "PUT": "upsert",
                "PATCH": "update",
                "DELETE": "delete",
            }.get(method, method.lower())
            return f"{domain}.{verb}"
    return f"http.{method.lower()}"


def _record_request_audit(request: Request, status_code: int) -> None:
    user = getattr(request.state, "authenticated_user", None)
    outcome = (
        "success"
        if status_code < 400
        else "denied"
        if status_code in {401, 403}
        else "error"
        if status_code >= 500
        else "rejected"
    )
    try:
        dbmod.record_audit_event(
            actor_id=user.get("id") if isinstance(user, dict) else None,
            actor_username=user.get("username", "anonymous") if isinstance(user, dict) else "anonymous",
            actor_role=user.get("role", "") if isinstance(user, dict) else "",
            action=_audit_action(request.method, request.url.path),
            resource=request.url.path,
            method=request.method,
            status_code=status_code,
            outcome=outcome,
            detail={"query_keys": sorted(set(request.query_params.keys()))},
        )
    except Exception as exc:  # Audit failure must not break the requested operation.
        log.warning("audit write failed: %s", exc)


@app.middleware("http")
async def audit_mutating_requests(request: Request, call_next):
    mutating = request.method.upper() in {"POST", "PUT", "PATCH", "DELETE"}
    excluded = request.url.path in {"/api/auth/login", "/api/auth/logout"}
    if not mutating or not request.url.path.startswith("/api/") or excluded:
        return await call_next(request)
    try:
        response = await call_next(request)
    except Exception:
        _record_request_audit(request, 500)
        raise
    _record_request_audit(request, response.status_code)
    return response


@app.on_event("startup")
def on_startup():
    if len(settings.session_secret) < 24:
        raise RuntimeError("SESSION_SECRET must contain at least 24 characters")
    if len(settings.console_bootstrap_password) < 12:
        raise RuntimeError("CONSOLE_BOOTSTRAP_PASSWORD must contain at least 12 characters")
    settings.console_db.parent.mkdir(parents=True, exist_ok=True)
    settings.knowledge_dir.mkdir(parents=True, exist_ok=True)
    init_db()
    know.ensure_knowledge_dir()


app.include_router(auth_routes.router)
app.include_router(console_users.router)
app.include_router(personas.router)
app.include_router(bot_users.router)
app.include_router(config_routes.router)
app.include_router(runtime_profile.router)
app.include_router(ops.router)
app.include_router(messages.router)


@app.get("/api/stats")
def stats(_user=require_perm("personas.view", "bot_users.view", "memories.view", "knowledge.view")):
    from .services import companion, memory, persona_lib, soulmap

    return {
        "companion_users": len(companion.list_user_ids()),
        "soulmap_profiles": len(soulmap.list_profile_ids()),
        "personas": len(persona_lib.list_personas()),
        "memories": len(memory.list_companion_memories()),
        "knowledge_docs": len(know.list_docs()),
    }


if STATIC_DIR.exists():
    app.mount("/assets", StaticFiles(directory=str(STATIC_DIR)), name="assets")


@app.get("/")
def index():
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/{full_path:path}")
def spa_fallback(full_path: str):
    if full_path.startswith("api/"):
        from fastapi import HTTPException

        raise HTTPException(404)
    candidate = STATIC_DIR / full_path
    if candidate.is_file():
        return FileResponse(candidate)
    return FileResponse(STATIC_DIR / "index.html")
