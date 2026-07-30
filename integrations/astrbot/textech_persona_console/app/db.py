import json
import sqlite3
from contextlib import contextmanager
from typing import Any, Iterator

import bcrypt

from .config import PRESET_ROLES, settings

# Legacy three roles still used as defaults; custom roles live in `roles` table.
LEGACY_ROLES = ("admin", "editor", "viewer")


def _connect() -> sqlite3.Connection:
    settings.console_db.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(settings.console_db), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


@contextmanager
def db() -> Iterator[sqlite3.Connection]:
    conn = _connect()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def _table_cols(conn: sqlite3.Connection, table: str) -> set[str]:
    return {r[1] for r in conn.execute(f"PRAGMA table_info({table})").fetchall()}


def init_db() -> None:
    with db() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS console_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'viewer',
                grants TEXT NOT NULL DEFAULT '[]',
                denies TEXT NOT NULL DEFAULT '[]',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        cols = _table_cols(conn, "console_users")
        if "grants" not in cols:
            conn.execute("ALTER TABLE console_users ADD COLUMN grants TEXT NOT NULL DEFAULT '[]'")
        if "denies" not in cols:
            conn.execute("ALTER TABLE console_users ADD COLUMN denies TEXT NOT NULL DEFAULT '[]'")

        # Drop legacy CHECK on role if present by rebuilding once.
        create_sql = conn.execute(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='console_users'"
        ).fetchone()
        if create_sql and create_sql[0] and "CHECK" in create_sql[0]:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS console_users_v2 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'viewer',
                    grants TEXT NOT NULL DEFAULT '[]',
                    denies TEXT NOT NULL DEFAULT '[]',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """
            )
            conn.execute(
                """
                INSERT OR IGNORE INTO console_users_v2
                (id, username, password_hash, role, grants, denies, created_at, updated_at)
                SELECT id, username, password_hash, role,
                       COALESCE(grants, '[]'), COALESCE(denies, '[]'),
                       created_at, updated_at
                FROM console_users
                """
            )
            conn.execute("DROP TABLE console_users")
            conn.execute("ALTER TABLE console_users_v2 RENAME TO console_users")

        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS api_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                token_hash TEXT NOT NULL UNIQUE,
                label TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                FOREIGN KEY(user_id) REFERENCES console_users(id) ON DELETE CASCADE
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS roles (
                name TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                is_system INTEGER NOT NULL DEFAULT 0,
                permissions TEXT NOT NULL DEFAULT '[]',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor_id INTEGER,
                actor_username TEXT NOT NULL DEFAULT 'anonymous',
                actor_role TEXT NOT NULL DEFAULT '',
                action TEXT NOT NULL,
                resource TEXT NOT NULL,
                method TEXT NOT NULL,
                status_code INTEGER NOT NULL,
                outcome TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '{}',
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_audit_events_created ON audit_events(id DESC)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_audit_events_actor ON audit_events(actor_username, id DESC)"
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_audit_events_action ON audit_events(action, id DESC)"
        )
        for name, meta in PRESET_ROLES.items():
            existing = conn.execute("SELECT name FROM roles WHERE name = ?", (name,)).fetchone()
            if not existing:
                conn.execute(
                    "INSERT INTO roles (name, label, is_system, permissions) VALUES (?, ?, 1, ?)",
                    (name, meta["label"], json.dumps(meta["permissions"], ensure_ascii=False)),
                )
            else:
                # Keep system role permissions in sync with code presets.
                conn.execute(
                    "UPDATE roles SET label = ?, permissions = ?, is_system = 1, updated_at = datetime('now') WHERE name = ?",
                    (meta["label"], json.dumps(meta["permissions"], ensure_ascii=False), name),
                )

        count = conn.execute("SELECT COUNT(*) AS c FROM console_users").fetchone()["c"]
        if count == 0:
            pw = settings.console_bootstrap_password.encode("utf-8")
            hashed = bcrypt.hashpw(pw, bcrypt.gensalt()).decode("utf-8")
            conn.execute(
                "INSERT INTO console_users (username, password_hash, role) VALUES (?, ?, ?)",
                ("admin", hashed, "admin"),
            )


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))
    except Exception:
        return False


def _parse_json_list(raw: Any) -> list[str]:
    if raw is None or raw == "":
        return []
    if isinstance(raw, list):
        return [str(x) for x in raw]
    try:
        data = json.loads(raw)
        if isinstance(data, list):
            return [str(x) for x in data]
    except Exception:
        pass
    return []


def _user_row(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if not row:
        return None
    d = dict(row)
    d["grants"] = _parse_json_list(d.get("grants"))
    d["denies"] = _parse_json_list(d.get("denies"))
    return d


def get_user_by_username(username: str) -> dict[str, Any] | None:
    with db() as conn:
        row = conn.execute(
            "SELECT id, username, password_hash, role, grants, denies, created_at, updated_at FROM console_users WHERE username = ?",
            (username,),
        ).fetchone()
        return _user_row(row)


def get_user_by_id(user_id: int) -> dict[str, Any] | None:
    with db() as conn:
        row = conn.execute(
            "SELECT id, username, role, grants, denies, created_at, updated_at FROM console_users WHERE id = ?",
            (user_id,),
        ).fetchone()
        return _user_row(row)


def get_user_with_password(user_id: int) -> dict[str, Any] | None:
    with db() as conn:
        row = conn.execute(
            "SELECT id, username, password_hash, role, grants, denies, created_at, updated_at FROM console_users WHERE id = ?",
            (user_id,),
        ).fetchone()
        return _user_row(row)


def list_users() -> list[dict[str, Any]]:
    with db() as conn:
        rows = conn.execute(
            "SELECT id, username, role, grants, denies, created_at, updated_at FROM console_users ORDER BY id"
        ).fetchall()
        return [_user_row(r) for r in rows]  # type: ignore[misc]


def role_exists(name: str) -> bool:
    with db() as conn:
        row = conn.execute("SELECT name FROM roles WHERE name = ?", (name,)).fetchone()
        return row is not None


def create_user(
    username: str,
    password: str,
    role: str,
    grants: list[str] | None = None,
    denies: list[str] | None = None,
) -> dict[str, Any]:
    if not role_exists(role):
        raise ValueError(f"unknown role: {role}")
    with db() as conn:
        cur = conn.execute(
            "INSERT INTO console_users (username, password_hash, role, grants, denies) VALUES (?, ?, ?, ?, ?)",
            (
                username,
                hash_password(password),
                role,
                json.dumps(grants or [], ensure_ascii=False),
                json.dumps(denies or [], ensure_ascii=False),
            ),
        )
        uid = cur.lastrowid
    user = get_user_by_id(uid)
    assert user is not None
    return user


def update_user(
    user_id: int,
    *,
    password: str | None = None,
    role: str | None = None,
    grants: list[str] | None = None,
    denies: list[str] | None = None,
) -> dict[str, Any]:
    if role is not None and not role_exists(role):
        raise ValueError(f"unknown role: {role}")
    with db() as conn:
        if password is not None:
            conn.execute(
                "UPDATE console_users SET password_hash = ?, updated_at = datetime('now') WHERE id = ?",
                (hash_password(password), user_id),
            )
        if role is not None:
            conn.execute(
                "UPDATE console_users SET role = ?, updated_at = datetime('now') WHERE id = ?",
                (role, user_id),
            )
        if grants is not None:
            conn.execute(
                "UPDATE console_users SET grants = ?, updated_at = datetime('now') WHERE id = ?",
                (json.dumps(grants, ensure_ascii=False), user_id),
            )
        if denies is not None:
            conn.execute(
                "UPDATE console_users SET denies = ?, updated_at = datetime('now') WHERE id = ?",
                (json.dumps(denies, ensure_ascii=False), user_id),
            )
    user = get_user_by_id(user_id)
    if not user:
        raise KeyError("user not found")
    return user


def delete_user(user_id: int) -> None:
    with db() as conn:
        conn.execute("DELETE FROM console_users WHERE id = ?", (user_id,))


def create_api_token(user_id: int, token: str, label: str = "") -> None:
    token_hash = bcrypt.hashpw(token.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
    with db() as conn:
        conn.execute(
            "INSERT INTO api_tokens (user_id, token_hash, label) VALUES (?, ?, ?)",
            (user_id, token_hash, label),
        )


def find_user_by_api_token(token: str) -> dict[str, Any] | None:
    with db() as conn:
        rows = conn.execute(
            "SELECT t.token_hash, u.id, u.username, u.role, u.grants, u.denies FROM api_tokens t JOIN console_users u ON u.id = t.user_id"
        ).fetchall()
    for row in rows:
        try:
            if bcrypt.checkpw(token.encode("utf-8"), row["token_hash"].encode("utf-8")):
                return {
                    "id": row["id"],
                    "username": row["username"],
                    "role": row["role"],
                    "grants": _parse_json_list(row["grants"]),
                    "denies": _parse_json_list(row["denies"]),
                }
        except Exception:
            continue
    return None


def list_roles() -> list[dict[str, Any]]:
    with db() as conn:
        rows = conn.execute(
            "SELECT name, label, is_system, permissions, created_at, updated_at FROM roles ORDER BY is_system DESC, name"
        ).fetchall()
    out = []
    for r in rows:
        d = dict(r)
        d["is_system"] = bool(d["is_system"])
        d["permissions"] = _parse_json_list(d.get("permissions"))
        out.append(d)
    return out


def get_role(name: str) -> dict[str, Any] | None:
    with db() as conn:
        row = conn.execute(
            "SELECT name, label, is_system, permissions, created_at, updated_at FROM roles WHERE name = ?",
            (name,),
        ).fetchone()
    if not row:
        return None
    d = dict(row)
    d["is_system"] = bool(d["is_system"])
    d["permissions"] = _parse_json_list(d.get("permissions"))
    return d


def create_role(name: str, label: str, permissions: list[str]) -> dict[str, Any]:
    name = name.strip()
    if not name or not name.replace("_", "").replace("-", "").isalnum():
        raise ValueError("角色名仅允许字母数字与 _ -")
    if get_role(name):
        raise ValueError("角色已存在")
    with db() as conn:
        conn.execute(
            "INSERT INTO roles (name, label, is_system, permissions) VALUES (?, ?, 0, ?)",
            (name, label or name, json.dumps(permissions, ensure_ascii=False)),
        )
    role = get_role(name)
    assert role
    return role


def update_role(name: str, *, label: str | None = None, permissions: list[str] | None = None) -> dict[str, Any]:
    role = get_role(name)
    if not role:
        raise KeyError("role not found")
    with db() as conn:
        if label is not None:
            conn.execute(
                "UPDATE roles SET label = ?, updated_at = datetime('now') WHERE name = ?",
                (label, name),
            )
        if permissions is not None:
            # System presets can still have permissions adjusted by admin if desired,
            # but we keep is_system flag.
            conn.execute(
                "UPDATE roles SET permissions = ?, updated_at = datetime('now') WHERE name = ?",
                (json.dumps(permissions, ensure_ascii=False), name),
            )
    updated = get_role(name)
    assert updated
    return updated


def delete_role(name: str) -> None:
    role = get_role(name)
    if not role:
        raise KeyError("role not found")
    if role["is_system"]:
        raise ValueError("不能删除系统预设角色")
    with db() as conn:
        used = conn.execute("SELECT COUNT(*) AS c FROM console_users WHERE role = ?", (name,)).fetchone()["c"]
        if used:
            raise ValueError(f"仍有 {used} 个用户使用该角色")
        conn.execute("DELETE FROM roles WHERE name = ?", (name,))


def resolve_permissions(user: dict[str, Any]) -> list[str]:
    """Role permissions + grants − denies."""
    role = get_role(user.get("role") or "viewer")
    base = set(role["permissions"] if role else PRESET_ROLES.get("viewer", {}).get("permissions", []))
    grants = set(user.get("grants") or [])
    denies = set(user.get("denies") or [])
    return sorted((base | grants) - denies)


def record_audit_event(
    *,
    actor_id: int | None,
    actor_username: str,
    actor_role: str,
    action: str,
    resource: str,
    method: str,
    status_code: int,
    outcome: str,
    detail: dict[str, Any] | None = None,
    max_entries: int = 20000,
) -> None:
    """Append one redacted audit event. Request bodies and credentials are never stored."""
    safe_detail = detail if isinstance(detail, dict) else {}
    with db() as conn:
        conn.execute(
            """
            INSERT INTO audit_events
            (actor_id, actor_username, actor_role, action, resource, method, status_code, outcome, detail)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                actor_id,
                (actor_username or "anonymous")[:120],
                (actor_role or "")[:80],
                (action or "unknown")[:160],
                (resource or "")[:500],
                (method or "")[:16],
                int(status_code),
                (outcome or "unknown")[:40],
                json.dumps(safe_detail, ensure_ascii=False, separators=(",", ":"))[:4000],
            ),
        )
        keep = max(1000, min(int(max_entries), 100000))
        conn.execute(
            "DELETE FROM audit_events WHERE id IN "
            "(SELECT id FROM audit_events ORDER BY id DESC LIMIT -1 OFFSET ?)",
            (keep,),
        )


def list_audit_events(
    *,
    limit: int = 100,
    offset: int = 0,
    action: str | None = None,
    outcome: str | None = None,
    query: str | None = None,
) -> dict[str, Any]:
    limit = max(1, min(int(limit), 500))
    offset = max(0, int(offset))
    where: list[str] = []
    params: list[Any] = []
    if action:
        where.append("action = ?")
        params.append(action.strip()[:160])
    if outcome:
        where.append("outcome = ?")
        params.append(outcome.strip()[:40])
    if query:
        needle = f"%{query.strip()[:200]}%"
        where.append("(actor_username LIKE ? OR action LIKE ? OR resource LIKE ?)")
        params.extend([needle, needle, needle])
    clause = (" WHERE " + " AND ".join(where)) if where else ""
    with db() as conn:
        total = conn.execute(f"SELECT COUNT(*) AS c FROM audit_events{clause}", params).fetchone()["c"]
        rows = conn.execute(
            "SELECT id, actor_id, actor_username, actor_role, action, resource, method, "
            f"status_code, outcome, detail, created_at FROM audit_events{clause} "
            "ORDER BY id DESC LIMIT ? OFFSET ?",
            [*params, limit, offset],
        ).fetchall()
    items: list[dict[str, Any]] = []
    for row in rows:
        item = dict(row)
        try:
            item["detail"] = json.loads(item.get("detail") or "{}")
        except (TypeError, json.JSONDecodeError):
            item["detail"] = {}
        items.append(item)
    return {"items": items, "total": int(total), "limit": limit, "offset": offset}
