import json
import sqlite3
import time
from datetime import datetime
from pathlib import Path
from typing import Any

from .. import json_store
from ..config import COMPANIONS, MEMORY_PLUGIN_DIRS, settings


def _safe_json_load(path: Path) -> Any:
    try:
        text = path.read_text(encoding="utf-8-sig")
        if not text.strip():
            return None
        return json.loads(text)
    except Exception:
        return None


def _walk_file_memories(root: Path, user_id: str | None, query: str | None) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    if not root.exists():
        return results
    q = (query or "").lower()
    uid = str(user_id) if user_id else None

    for path in root.rglob("*"):
        if path.is_dir():
            continue
        rel = str(path.relative_to(root)).replace("\\", "/")

        if path.suffix.lower() == ".json":
            data = _safe_json_load(path)
            if data is None:
                continue
            blob = json.dumps(data, ensure_ascii=False)
            if uid and uid not in rel and uid not in blob:
                continue
            if q and q not in blob.lower() and q not in rel.lower():
                continue
            preview = data
            if isinstance(data, dict) and len(json.dumps(data)) > 4000:
                preview = {k: data[k] for k in list(data)[:40]}
                preview["_truncated"] = True
            results.append(
                {
                    "id": f"file:{root.name}:{rel}",
                    "source": root.name,
                    "path": rel,
                    "type": "json",
                    "editable": False,
                    "data": preview,
                }
            )
        elif path.suffix.lower() in {".db", ".sqlite", ".sqlite3"}:
            try:
                rows = _query_sqlite(path, uid, q)
                for row in rows:
                    results.append(
                        {
                            "id": f"sqlite:{root.name}:{rel}:{row.get('_table')}:{row.get('id', '')}",
                            "source": root.name,
                            "path": rel,
                            "type": "sqlite",
                            "editable": False,
                            "data": row,
                        }
                    )
            except Exception as e:
                results.append(
                    {
                        "id": f"sqlite_err:{root.name}:{rel}",
                        "source": root.name,
                        "path": rel,
                        "type": "sqlite_error",
                        "editable": False,
                        "data": {"error": str(e)},
                    }
                )
        elif path.suffix.lower() in {".md", ".txt"}:
            try:
                text = path.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            if uid and uid not in rel and uid not in text:
                continue
            if q and q not in text.lower() and q not in rel.lower():
                continue
            results.append(
                {
                    "id": f"text:{root.name}:{rel}",
                    "source": root.name,
                    "path": rel,
                    "type": "text",
                    "editable": False,
                    "data": {"text": text[:4000]},
                }
            )
    return results


def _query_sqlite(path: Path, user_id: str | None, query: str | None) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    uri = f"file:{path}?mode=ro"
    conn = sqlite3.connect(uri, uri=True)
    conn.row_factory = sqlite3.Row
    try:
        tables = [
            r[0]
            for r in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            ).fetchall()
        ]
        for table in tables[:20]:
            cols = [r[1] for r in conn.execute(f'PRAGMA table_info("{table}")').fetchall()]
            col_list = ", ".join(f'"{c}"' for c in cols[:30])
            sql = f'SELECT {col_list} FROM "{table}" LIMIT 200'
            try:
                rows = conn.execute(sql).fetchall()
            except Exception:
                continue
            for row in rows:
                d = {cols[i]: row[i] for i in range(len(cols[:30]))}
                blob = json.dumps(d, ensure_ascii=False, default=str)
                if user_id and user_id not in blob:
                    continue
                if query and query.lower() not in blob.lower():
                    continue
                d["_table"] = table
                out.append(d)
                if len(out) >= 100:
                    return out
    finally:
        conn.close()
    return out


def _ensure_memory_bucket(user: dict[str, Any]) -> dict[str, Any]:
    mem = user.get("companion_memory")
    if not isinstance(mem, dict):
        mem = {"items": [], "updated_at": ""}
        user["companion_memory"] = mem
    items = mem.get("items")
    if not isinstance(items, list):
        mem["items"] = []
    return mem


def list_companion_memories(
    user_id: str | None = None,
    query: str | None = None,
    kind: str | None = None,
) -> list[dict[str, Any]]:
    data = json_store.read_json(COMPANIONS, {"users": {}})
    users = data.get("users") or {}
    q = (query or "").lower().strip()
    kind_f = (kind or "").strip().lower()
    uid_f = str(user_id).strip() if user_id else None
    out: list[dict[str, Any]] = []

    for uid, user in users.items():
        if not isinstance(user, dict):
            continue
        if uid_f and str(uid) != uid_f:
            continue
        mem = user.get("companion_memory")
        if not isinstance(mem, dict):
            continue
        items = mem.get("items") or []
        if not isinstance(items, list):
            continue
        for idx, item in enumerate(items):
            if not isinstance(item, dict):
                continue
            text = str(item.get("text") or "")
            item_kind = str(item.get("kind") or "")
            blob = json.dumps(item, ensure_ascii=False)
            if kind_f and kind_f not in item_kind.lower():
                continue
            if q and q not in blob.lower() and q not in str(uid).lower():
                nick = str(user.get("nickname") or "")
                disp = str(user.get("last_display_name") or "")
                if q not in nick.lower() and q not in disp.lower():
                    continue
            out.append(
                {
                    "id": f"cm:{uid}:{idx}",
                    "source": "companion_memory",
                    "type": "companion_memory",
                    "editable": True,
                    "user_id": str(uid),
                    "index": idx,
                    "text": text,
                    "kind": item_kind,
                    "weight": item.get("weight"),
                    "reason": item.get("reason"),
                    "created_at": item.get("created_at"),
                    "created_ts": item.get("created_ts"),
                    "nickname": user.get("nickname") or "",
                    "qq_display_name": user.get("last_display_name") or "",
                    "umo": user.get("umo") or "",
                    "data": item,
                }
            )
    out.sort(key=lambda x: float(x.get("created_ts") or 0), reverse=True)
    return out


def add_companion_memory(
    user_id: str,
    *,
    text: str,
    kind: str = "note",
    weight: int | float = 1,
    reason: str = "console",
) -> dict[str, Any]:
    text = (text or "").strip()
    if not text:
        raise ValueError("text required")
    data = json_store.read_json(COMPANIONS, {"users": {}})
    users = data.setdefault("users", {})
    uid = str(user_id)
    if uid not in users or not isinstance(users[uid], dict):
        raise KeyError("user not found")
    user = users[uid]
    mem = _ensure_memory_bucket(user)
    now = time.time()
    item = {
        "text": text,
        "kind": kind or "note",
        "weight": weight if weight is not None else 1,
        "reason": reason or "console",
        "created_at": datetime.now().strftime("%Y-%m-%d %H:%M"),
        "created_ts": now,
    }
    mem["items"].append(item)
    mem["updated_at"] = item["created_at"]
    json_store.write_json(COMPANIONS, data)
    idx = len(mem["items"]) - 1
    return {
        "id": f"cm:{uid}:{idx}",
        "user_id": uid,
        "index": idx,
        "data": item,
    }


def update_companion_memory(user_id: str, index: int, patch: dict[str, Any]) -> dict[str, Any]:
    data = json_store.read_json(COMPANIONS, {"users": {}})
    users = data.get("users") or {}
    uid = str(user_id)
    user = users.get(uid)
    if not isinstance(user, dict):
        raise KeyError("user not found")
    mem = _ensure_memory_bucket(user)
    items = mem["items"]
    if index < 0 or index >= len(items):
        raise IndexError("memory index out of range")
    item = items[index]
    if not isinstance(item, dict):
        item = {}
        items[index] = item
    for key in ("text", "kind", "weight", "reason"):
        if key in patch:
            item[key] = patch[key]
    if "text" in patch and not str(patch["text"] or "").strip():
        raise ValueError("text required")
    mem["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M")
    json_store.write_json(COMPANIONS, data)
    return {"id": f"cm:{uid}:{index}", "user_id": uid, "index": index, "data": item}


def delete_companion_memory(user_id: str, index: int) -> None:
    data = json_store.read_json(COMPANIONS, {"users": {}})
    users = data.get("users") or {}
    uid = str(user_id)
    user = users.get(uid)
    if not isinstance(user, dict):
        raise KeyError("user not found")
    mem = _ensure_memory_bucket(user)
    items = mem["items"]
    if index < 0 or index >= len(items):
        raise IndexError("memory index out of range")
    items.pop(index)
    mem["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M")
    json_store.write_json(COMPANIONS, data)


def search_memories(
    user_id: str | None = None,
    query: str | None = None,
    kind: str | None = None,
    include_files: bool = True,
) -> list[dict[str, Any]]:
    results = list_companion_memories(user_id=user_id, query=query, kind=kind)
    if include_files:
        for rel in MEMORY_PLUGIN_DIRS:
            root = settings.astrbot_data / rel
            results.extend(_walk_file_memories(root, user_id, query))
    return results[:500]


def list_memory_sources() -> list[dict[str, Any]]:
    sources = [
        {
            "path": COMPANIONS,
            "label": "Private Companion · companion_memory",
            "exists": json_store.data_path(COMPANIONS).exists(),
            "editable": True,
            "count": len(list_companion_memories()),
        }
    ]
    for rel in MEMORY_PLUGIN_DIRS:
        root = settings.astrbot_data / rel
        sources.append(
            {
                "path": rel,
                "label": rel.split("/")[-1],
                "exists": root.exists(),
                "editable": False,
                "file_count": sum(1 for _ in root.rglob("*") if _.is_file()) if root.exists() else 0,
            }
        )
    return sources
