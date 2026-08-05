from __future__ import annotations

import json
import re
import sqlite3
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Iterator
from zoneinfo import ZoneInfo

from PIL import Image, ImageOps

from ..config import COMPANIONS, GENERATED_PHOTOS, PHOTO_GALLERY_DB, settings


ALLOWED_IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
THUMBNAIL_EDGE = 512
THUMBNAIL_FORMAT_VERSION = "webp-v1"
_sync_lock = threading.RLock()
_thumbnail_lock = threading.RLock()
_last_sync: dict[str, float] = {}


def _astrbot_root(root: Path | None = None) -> Path:
    return (root or settings.astrbot_data).resolve()


def _db_path(root: Path | None = None) -> Path:
    return _astrbot_root(root) / PHOTO_GALLERY_DB


def _images_root(root: Path | None = None) -> Path:
    return (_astrbot_root(root) / GENERATED_PHOTOS).resolve()


def _connect(root: Path | None = None) -> sqlite3.Connection:
    path = _db_path(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path), timeout=8, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout = 8000")
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA foreign_keys = ON")
    _ensure_schema(conn)
    return conn


@contextmanager
def _db(root: Path | None = None) -> Iterator[sqlite3.Connection]:
    conn = _connect(root)
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def _ensure_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS images (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            path TEXT NOT NULL UNIQUE,
            filename TEXT NOT NULL,
            created_at REAL NOT NULL,
            recorded_at REAL NOT NULL,
            kind TEXT NOT NULL DEFAULT '',
            operation TEXT NOT NULL DEFAULT 'generate',
            backend TEXT NOT NULL DEFAULT '',
            prompt TEXT NOT NULL DEFAULT '',
            prompt_format TEXT NOT NULL DEFAULT '',
            session TEXT NOT NULL DEFAULT '',
            producer_id TEXT NOT NULL DEFAULT '',
            producer_name TEXT NOT NULL DEFAULT '',
            producer_type TEXT NOT NULL DEFAULT 'unknown',
            has_reference INTEGER NOT NULL DEFAULT 0,
            reference_path TEXT NOT NULL DEFAULT '',
            image_size TEXT NOT NULL DEFAULT '',
            file_size INTEGER NOT NULL DEFAULT 0,
            trace TEXT NOT NULL DEFAULT '',
            presets TEXT NOT NULL DEFAULT '[]',
            trigger TEXT NOT NULL DEFAULT '',
            intent_kind TEXT NOT NULL DEFAULT '',
            sent INTEGER,
            caption TEXT NOT NULL DEFAULT '',
            source TEXT NOT NULL DEFAULT 'astrbot',
            metadata_quality TEXT NOT NULL DEFAULT 'complete',
            missing INTEGER NOT NULL DEFAULT 0,
            updated_at REAL NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_images_created ON images(created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_images_operation ON images(operation, created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_images_backend ON images(backend, created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_images_producer ON images(producer_id, created_at DESC);

        CREATE TABLE IF NOT EXISTS favorites (
            user_id INTEGER NOT NULL,
            image_id INTEGER NOT NULL,
            created_at REAL NOT NULL,
            PRIMARY KEY(user_id, image_id),
            FOREIGN KEY(image_id) REFERENCES images(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id, created_at DESC);
        """
    )


def _read_companion_data(root: Path | None = None) -> dict[str, Any]:
    path = _astrbot_root(root) / COMPANIONS
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError):
        return {"users": {}}
    return data if isinstance(data, dict) else {"users": {}}


def _display_name(user_id: str, user: dict[str, Any]) -> str:
    observed = user.get("observed_display_names")
    candidates = [
        user.get("last_display_name"),
        user.get("nickname"),
        observed[0] if isinstance(observed, list) and observed else "",
    ]
    for value in candidates:
        text = str(value or "").strip()
        if text:
            return text[:120]
    if len(user_id) > 12:
        return f"{user_id[:6]}…{user_id[-4:]}"
    return user_id or "未知用户"


def _infer_producer(
    session: str,
    filename: str,
    companion_data: dict[str, Any],
) -> tuple[str, str, str]:
    session = str(session or "").strip()
    if session == "daily_outfit":
        return "", "Bot · 每日穿搭", "bot"
    users = companion_data.get("users")
    if not isinstance(users, dict):
        users = {}
    lowered_file = filename.lower()
    for raw_id, raw_user in users.items():
        user_id = str(raw_id)
        user = raw_user if isinstance(raw_user, dict) else {}
        umo = str(user.get("umo") or "").strip()
        candidates = {
            user_id,
            umo,
            f"natural_photo_{user_id}",
            f"command_photo_{user_id}",
            f"tool_photo_{user_id}",
            f"tool_photo_{umo}" if umo else "",
        }
        if session in candidates:
            return user_id, _display_name(user_id, user), "user"
        probes = [user_id.lower()]
        if umo:
            probes.append(umo.split(":")[-1].lower())
        if any(len(probe) >= 12 and probe[:20] in lowered_file for probe in probes):
            return user_id, _display_name(user_id, user), "user"
    if session and not session.startswith(("tool_photo_", "natural_photo_", "command_photo_")):
        return "", "Bot · 自动任务", "bot"
    return "", "未知来源", "unknown"


def _file_timestamp(path: Path) -> float:
    match = re.search(r"(\d{8}_\d{6})", path.name)
    if match:
        try:
            local = datetime.strptime(match.group(1), "%Y%m%d_%H%M%S")
            return local.replace(tzinfo=ZoneInfo("Asia/Shanghai")).timestamp()
        except ValueError:
            pass
    try:
        return path.stat().st_mtime
    except OSError:
        return time.time()


def _infer_trigger(session: str) -> str:
    if session.startswith("natural_photo_"):
        return "natural_language"
    if session.startswith("command_photo_"):
        return "command"
    if session.startswith("tool_photo_"):
        return "llm_tool"
    if session == "daily_outfit":
        return "daily_outfit"
    return "proactive" if session else "historical"


def _operation(intent_kind: str, trigger: str, has_reference: bool) -> str:
    if intent_kind == "edit":
        return "edit"
    if has_reference and trigger in {"natural_language", "command"}:
        return "edit"
    return "generate"


def _upsert_record(
    conn: sqlite3.Connection,
    item: dict[str, Any],
    companion_data: dict[str, Any],
    *,
    quality: str,
    root: Path | None = None,
) -> None:
    raw_path = Path(str(item.get("path") or ""))
    filename = raw_path.name
    if not filename or Path(filename).suffix.lower() not in ALLOWED_IMAGE_SUFFIXES:
        return
    image_path = _images_root(root) / filename
    if not image_path.is_file() or image_path.is_symlink():
        return
    session = str(item.get("session") or "")[:160]
    producer_id, producer_name, producer_type = _infer_producer(session, filename, companion_data)
    trigger = str(item.get("trigger") or _infer_trigger(session))[:60]
    intent_kind = str(item.get("intent_kind") or "")[:40]
    has_reference = bool(item.get("reference") or item.get("has_reference"))
    operation = (
        "unknown"
        if quality == "historical"
        else _operation(intent_kind, trigger, has_reference)
    )
    created_at = float(item.get("ts") or item.get("created_at") or _file_timestamp(image_path))
    now = time.time()
    try:
        file_size = image_path.stat().st_size
    except OSError:
        file_size = 0
    presets = item.get("presets") if isinstance(item.get("presets"), list) else []
    sent = item.get("sent")
    sent_value = None if sent is None else int(bool(sent))
    values = (
        filename,
        filename,
        created_at,
        float(item.get("recorded_at") or now),
        str(item.get("kind") or "")[:40],
        operation,
        str(item.get("backend") or "")[:120],
        str(item.get("prompt") or "")[:12000],
        str(item.get("prompt_format") or "")[:60],
        session,
        producer_id[:200],
        producer_name[:120],
        producer_type[:20],
        int(has_reference),
        Path(str(item.get("reference_path") or "")).name[:260],
        str(item.get("image_size") or "")[:60],
        int(file_size),
        str(item.get("trace") or "")[:80],
        json.dumps(presets[:12], ensure_ascii=False),
        trigger,
        intent_kind,
        sent_value,
        str(item.get("caption") or "")[:1000],
        str(item.get("source") or "astrbot")[:60],
        quality,
        now,
    )
    conn.execute(
        """
        INSERT INTO images (
            path, filename, created_at, recorded_at, kind, operation, backend,
            prompt, prompt_format, session, producer_id, producer_name,
            producer_type, has_reference, reference_path, image_size, file_size,
            trace, presets, trigger, intent_kind, sent, caption, source,
            metadata_quality, missing, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
        ON CONFLICT(path) DO UPDATE SET
            filename = excluded.filename,
            created_at = CASE WHEN images.metadata_quality = 'historical' THEN excluded.created_at ELSE images.created_at END,
            kind = CASE WHEN excluded.kind != '' THEN excluded.kind ELSE images.kind END,
            operation = CASE
                WHEN excluded.intent_kind != '' OR excluded.metadata_quality = 'complete' THEN excluded.operation
                WHEN images.metadata_quality = 'historical' THEN excluded.operation
                ELSE images.operation
            END,
            backend = CASE WHEN excluded.backend != '' THEN excluded.backend ELSE images.backend END,
            prompt = CASE WHEN excluded.prompt != '' THEN excluded.prompt ELSE images.prompt END,
            prompt_format = CASE WHEN excluded.prompt_format != '' THEN excluded.prompt_format ELSE images.prompt_format END,
            session = CASE WHEN excluded.session != '' THEN excluded.session ELSE images.session END,
            producer_id = CASE WHEN excluded.producer_id != '' THEN excluded.producer_id ELSE images.producer_id END,
            producer_name = CASE WHEN excluded.producer_name NOT IN ('', '未知来源') THEN excluded.producer_name ELSE images.producer_name END,
            producer_type = CASE WHEN excluded.producer_type != 'unknown' THEN excluded.producer_type ELSE images.producer_type END,
            has_reference = MAX(images.has_reference, excluded.has_reference),
            reference_path = CASE WHEN excluded.reference_path != '' THEN excluded.reference_path ELSE images.reference_path END,
            image_size = CASE WHEN excluded.image_size != '' THEN excluded.image_size ELSE images.image_size END,
            file_size = excluded.file_size,
            trace = CASE WHEN excluded.trace != '' THEN excluded.trace ELSE images.trace END,
            presets = CASE WHEN excluded.presets != '[]' THEN excluded.presets ELSE images.presets END,
            trigger = CASE
                WHEN excluded.metadata_quality = 'complete' AND excluded.trigger != '' THEN excluded.trigger
                WHEN images.trigger = '' THEN excluded.trigger
                ELSE images.trigger
            END,
            intent_kind = CASE WHEN excluded.intent_kind != '' THEN excluded.intent_kind ELSE images.intent_kind END,
            sent = COALESCE(excluded.sent, images.sent),
            caption = CASE WHEN excluded.caption != '' THEN excluded.caption ELSE images.caption END,
            metadata_quality = CASE WHEN excluded.metadata_quality = 'complete' THEN 'complete' ELSE images.metadata_quality END,
            missing = 0,
            updated_at = excluded.updated_at
        """,
        values,
    )


def ensure_index(*, force: bool = False, root: Path | None = None) -> dict[str, int]:
    astrbot_root = _astrbot_root(root)
    sync_key = str(astrbot_root)
    with _sync_lock:
        now = time.monotonic()
        if not force and now - _last_sync.get(sync_key, 0) < 45:
            return gallery_stats(0, root=root)
        image_root = _images_root(root)
        image_root.mkdir(parents=True, exist_ok=True)
        companion_data = _read_companion_data(root)
        files = sorted(
            path
            for path in image_root.iterdir()
            if path.is_file() and not path.is_symlink() and path.suffix.lower() in ALLOWED_IMAGE_SUFFIXES
        )
        with _db(root) as conn:
            recent = companion_data.get("recent_photo_generations")
            if isinstance(recent, list):
                for item in recent:
                    if isinstance(item, dict) and item.get("ok") and item.get("path"):
                        _upsert_record(conn, item, companion_data, quality="complete", root=root)
            for path in files:
                _upsert_record(
                    conn,
                    {
                        "path": path.name,
                        "created_at": _file_timestamp(path),
                        "trigger": "historical",
                        "source": "filesystem_backfill",
                    },
                    companion_data,
                    quality="historical",
                    root=root,
                )
            rows = conn.execute("SELECT id, path FROM images WHERE missing = 0").fetchall()
            existing = {path.name for path in files}
            for row in rows:
                if row["path"] not in existing:
                    conn.execute(
                        "UPDATE images SET missing = 1, updated_at = ? WHERE id = ?",
                        (time.time(), row["id"]),
                    )
        _last_sync[sync_key] = now
        return gallery_stats(0, root=root)


def _parse_date(value: str | None, *, end: bool = False) -> float | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError as exc:
        raise ValueError(f"日期格式无效: {text}") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=ZoneInfo("Asia/Shanghai"))
    if end and len(text) <= 10:
        parsed += timedelta(days=1)
    return parsed.timestamp()


def _safe_image_path(raw_path: str, root: Path | None = None) -> Path | None:
    image_root = _images_root(root)
    candidate = (image_root / Path(str(raw_path or "")).name).resolve()
    if not candidate.is_relative_to(image_root) or not candidate.is_file() or candidate.is_symlink():
        return None
    return candidate


def _file_cache_version(path: Path) -> str:
    stat = path.stat()
    return f"{stat.st_mtime_ns:x}-{stat.st_size:x}"


def _thumbnail_cache_version(source_version: str) -> str:
    return f"{THUMBNAIL_FORMAT_VERSION}-{source_version}"


def _decode_row(row: sqlite3.Row, *, root: Path | None = None) -> dict[str, Any]:
    item = dict(row)
    image_path = _safe_image_path(str(item.get("path") or ""), root)
    try:
        cache_version = _file_cache_version(image_path) if image_path else ""
    except OSError:
        cache_version = ""
    item["cache_version"] = cache_version
    item["thumbnail_version"] = (
        _thumbnail_cache_version(cache_version) if cache_version else ""
    )
    item["favorite"] = bool(item.get("favorite"))
    item["has_reference"] = bool(item.get("has_reference"))
    item["legacy"] = item.get("metadata_quality") != "complete"
    item["has_prompt"] = bool(str(item.get("prompt") or "").strip())
    item.pop("path", None)
    item.pop("session", None)
    item.pop("reference_path", None)
    try:
        item["presets"] = json.loads(item.get("presets") or "[]")
    except (TypeError, json.JSONDecodeError):
        item["presets"] = []
    return item


def list_images(
    user_id: int,
    *,
    q: str | None = None,
    favorite: bool | None = None,
    operation: str | None = None,
    kind: str | None = None,
    backend: str | None = None,
    producer: str | None = None,
    has_prompt: bool | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
    sort: str = "newest",
    page: int = 1,
    limit: int = 24,
    root: Path | None = None,
) -> dict[str, Any]:
    ensure_index(root=root)
    page = max(1, int(page))
    limit = max(1, min(int(limit), 100))
    where = ["i.missing = 0"]
    params: list[Any] = [int(user_id)]
    if q and q.strip():
        needle = f"%{q.strip()[:300]}%"
        where.append(
            "(i.prompt LIKE ? OR i.caption LIKE ? OR i.filename LIKE ? OR "
            "i.producer_name LIKE ? OR i.backend LIKE ?)"
        )
        params.extend([needle] * 5)
    if favorite is not None:
        where.append("f.image_id IS NOT NULL" if favorite else "f.image_id IS NULL")
    if operation:
        where.append("i.operation = ?")
        params.append(operation[:40])
    if kind:
        where.append("i.kind = ?")
        params.append(kind[:40])
    if backend:
        where.append("i.backend = ?")
        params.append(backend[:120])
    if producer:
        where.append("i.producer_id = ?")
        params.append(producer[:200])
    if has_prompt is not None:
        where.append("i.prompt != ''" if has_prompt else "i.prompt = ''")
    start_ts = _parse_date(date_from)
    end_ts = _parse_date(date_to, end=True)
    if start_ts is not None:
        where.append("i.created_at >= ?")
        params.append(start_ts)
    if end_ts is not None:
        where.append("i.created_at < ?")
        params.append(end_ts)
    order = {
        "newest": "i.created_at DESC, i.id DESC",
        "oldest": "i.created_at ASC, i.id ASC",
        "largest": "i.file_size DESC, i.created_at DESC",
        "favorites": "favorite DESC, i.created_at DESC",
    }.get(sort, "i.created_at DESC, i.id DESC")
    clause = " AND ".join(where)
    with _db(root) as conn:
        total = conn.execute(
            f"SELECT COUNT(*) FROM images i LEFT JOIN favorites f "
            f"ON f.image_id = i.id AND f.user_id = ? WHERE {clause}",
            params,
        ).fetchone()[0]
        rows = conn.execute(
            "SELECT i.*, CASE WHEN f.image_id IS NULL THEN 0 ELSE 1 END AS favorite "
            "FROM images i LEFT JOIN favorites f ON f.image_id = i.id AND f.user_id = ? "
            f"WHERE {clause} ORDER BY {order} LIMIT ? OFFSET ?",
            [*params, limit, (page - 1) * limit],
        ).fetchall()
    return {
        "items": [_decode_row(row, root=root) for row in rows],
        "total": int(total),
        "page": page,
        "limit": limit,
        "pages": max(1, (int(total) + limit - 1) // limit),
    }


def get_image(image_id: int, user_id: int, *, root: Path | None = None) -> dict[str, Any] | None:
    ensure_index(root=root)
    with _db(root) as conn:
        row = conn.execute(
            "SELECT i.*, CASE WHEN f.image_id IS NULL THEN 0 ELSE 1 END AS favorite "
            "FROM images i LEFT JOIN favorites f ON f.image_id = i.id AND f.user_id = ? "
            "WHERE i.id = ? AND i.missing = 0",
            (int(user_id), int(image_id)),
        ).fetchone()
    return _decode_row(row, root=root) if row else None


def image_file_info(
    image_id: int,
    *,
    root: Path | None = None,
) -> tuple[Path, str] | None:
    ensure_index(root=root)
    with _db(root) as conn:
        row = conn.execute(
            "SELECT path FROM images WHERE id = ? AND missing = 0", (int(image_id),)
        ).fetchone()
    if not row:
        return None
    candidate = _safe_image_path(row["path"], root)
    if not candidate:
        return None
    try:
        return candidate, _file_cache_version(candidate)
    except OSError:
        return None


def image_file(image_id: int, *, root: Path | None = None) -> Path | None:
    info = image_file_info(image_id, root=root)
    return info[0] if info else None


def thumbnail_file(
    image_id: int,
    *,
    root: Path | None = None,
    thumbnail_root: Path | None = None,
) -> tuple[Path, str] | None:
    info = image_file_info(image_id, root=root)
    if not info:
        return None
    source_path, source_version = info
    version = _thumbnail_cache_version(source_version)
    cache_root = (thumbnail_root or settings.image_thumbnail_dir).resolve()
    cache_root.mkdir(parents=True, exist_ok=True)
    target = cache_root / f"{int(image_id)}-{version}.webp"
    if target.is_file():
        return target, version

    with _thumbnail_lock:
        if target.is_file():
            return target, version
        temporary = cache_root / f".{target.name}.{threading.get_ident()}.tmp"
        try:
            with Image.open(source_path) as opened:
                opened.seek(0)
                frame = ImageOps.exif_transpose(opened).copy()
            has_alpha = frame.mode in {"RGBA", "LA"} or (
                frame.mode == "P" and "transparency" in frame.info
            )
            frame = frame.convert("RGBA" if has_alpha else "RGB")
            frame.thumbnail(
                (THUMBNAIL_EDGE, THUMBNAIL_EDGE),
                Image.Resampling.LANCZOS,
                reducing_gap=3.0,
            )
            frame.save(temporary, format="WEBP", quality=76, method=4)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        for stale in cache_root.glob(f"{int(image_id)}-*.webp"):
            if stale != target:
                try:
                    stale.unlink()
                except OSError:
                    pass
    return target, version


def set_favorite(
    image_id: int,
    user_id: int,
    favorite: bool,
    *,
    root: Path | None = None,
) -> dict[str, Any]:
    ensure_index(root=root)
    with _db(root) as conn:
        exists = conn.execute(
            "SELECT id FROM images WHERE id = ? AND missing = 0", (int(image_id),)
        ).fetchone()
        if not exists:
            raise KeyError("image not found")
        if favorite:
            conn.execute(
                "INSERT OR IGNORE INTO favorites(user_id, image_id, created_at) VALUES (?, ?, ?)",
                (int(user_id), int(image_id), time.time()),
            )
        else:
            conn.execute(
                "DELETE FROM favorites WHERE user_id = ? AND image_id = ?",
                (int(user_id), int(image_id)),
            )
    return {"ok": True, "image_id": int(image_id), "favorite": bool(favorite)}


def facets(user_id: int, *, root: Path | None = None) -> dict[str, Any]:
    ensure_index(root=root)
    with _db(root) as conn:
        def grouped(column: str) -> list[dict[str, Any]]:
            rows = conn.execute(
                f"SELECT {column} AS value, COUNT(*) AS count FROM images "
                f"WHERE missing = 0 AND {column} != '' GROUP BY {column} ORDER BY count DESC, value"
            ).fetchall()
            return [dict(row) for row in rows]

        producers = conn.execute(
            "SELECT producer_id AS value, producer_name AS label, COUNT(*) AS count "
            "FROM images WHERE missing = 0 AND producer_id != '' "
            "GROUP BY producer_id, producer_name ORDER BY count DESC, label"
        ).fetchall()
        bounds = conn.execute(
            "SELECT MIN(created_at) AS oldest, MAX(created_at) AS newest, COUNT(*) AS total "
            "FROM images WHERE missing = 0"
        ).fetchone()
        favorite_count = conn.execute(
            "SELECT COUNT(*) FROM favorites f JOIN images i ON i.id = f.image_id "
            "WHERE f.user_id = ? AND i.missing = 0",
            (int(user_id),),
        ).fetchone()[0]
        operations = grouped("operation")
        kinds = grouped("kind")
        backends = grouped("backend")
    return {
        "operations": operations,
        "kinds": kinds,
        "backends": backends,
        "producers": [dict(row) for row in producers],
        "oldest": bounds["oldest"] if bounds else None,
        "newest": bounds["newest"] if bounds else None,
        "total": int(bounds["total"] if bounds else 0),
        "favorites": int(favorite_count),
    }


def gallery_stats(user_id: int, *, root: Path | None = None) -> dict[str, int]:
    with _db(root) as conn:
        total = conn.execute("SELECT COUNT(*) FROM images WHERE missing = 0").fetchone()[0]
        complete = conn.execute(
            "SELECT COUNT(*) FROM images WHERE missing = 0 AND metadata_quality = 'complete'"
        ).fetchone()[0]
        favorites = 0
        if user_id:
            favorites = conn.execute(
                "SELECT COUNT(*) FROM favorites f JOIN images i ON i.id = f.image_id "
                "WHERE f.user_id = ? AND i.missing = 0",
                (int(user_id),),
            ).fetchone()[0]
    return {"total": int(total), "complete": int(complete), "favorites": int(favorites)}
