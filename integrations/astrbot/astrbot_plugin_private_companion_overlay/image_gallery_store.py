# -*- coding: utf-8 -*-
"""Persist successful Private Companion image generations for Persona Console."""
from __future__ import annotations

import json
import logging
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any


log = logging.getLogger("astrbot_plugin_private_companion.image_gallery")


def _short(value: Any, limit: int) -> str:
    return str(value or "").strip()[:limit]


class ImageGalleryStoreMixin:
    """Add durable gallery metadata without changing generation or delivery flow."""

    def _image_gallery_db(self) -> Path:
        root = Path(str(getattr(self, "data_dir", "") or ""))
        root.mkdir(parents=True, exist_ok=True)
        return root / "image_gallery.sqlite3"

    def _image_gallery_connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self._image_gallery_db()), timeout=8)
        conn.execute("PRAGMA busy_timeout = 8000")
        conn.execute("PRAGMA journal_mode = WAL")
        conn.execute("PRAGMA foreign_keys = ON")
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
        return conn

    @contextmanager
    def _image_gallery_session(self):
        conn = self._image_gallery_connect()
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    @staticmethod
    def _image_gallery_user_name(user_id: str, user: dict[str, Any]) -> str:
        observed = user.get("observed_display_names")
        candidates = (
            user.get("last_display_name"),
            user.get("nickname"),
            observed[0] if isinstance(observed, list) and observed else "",
        )
        for value in candidates:
            text = _short(value, 120)
            if text:
                return text
        if len(user_id) > 12:
            return f"{user_id[:6]}…{user_id[-4:]}"
        return user_id or "未知用户"

    def _image_gallery_producer(self, session_key: str) -> tuple[str, str, str]:
        session = _short(session_key, 160)
        if session == "daily_outfit":
            return "", "Bot · 每日穿搭", "bot"
        data = getattr(self, "data", {})
        users = data.get("users") if isinstance(data, dict) else {}
        if not isinstance(users, dict):
            users = {}
        for raw_id, raw_user in users.items():
            user_id = str(raw_id)
            user = raw_user if isinstance(raw_user, dict) else {}
            umo = _short(user.get("umo"), 200)
            candidates = {
                user_id,
                umo,
                f"natural_photo_{user_id}",
                f"command_photo_{user_id}",
                f"tool_photo_{user_id}",
                f"tool_photo_{umo}" if umo else "",
            }
            if session in candidates:
                return user_id, self._image_gallery_user_name(user_id, user), "user"
        if session and not session.startswith(("natural_photo_", "command_photo_", "tool_photo_")):
            return "", "Bot · 自动任务", "bot"
        return "", "未知来源", "unknown"

    @staticmethod
    def _image_gallery_trigger(session_key: str) -> str:
        if session_key.startswith("natural_photo_"):
            return "natural_language"
        if session_key.startswith("command_photo_"):
            return "command"
        if session_key.startswith("tool_photo_"):
            return "llm_tool"
        if session_key == "daily_outfit":
            return "daily_outfit"
        return "proactive" if session_key else "unknown"

    @staticmethod
    def _image_gallery_operation(intent_kind: str, trigger: str, has_reference: bool) -> str:
        if intent_kind == "edit":
            return "edit"
        if has_reference and trigger in {"natural_language", "command"}:
            return "edit"
        return "generate"

    def _image_gallery_store_generation(self, item: dict[str, Any]) -> None:
        source_path = Path(str(item.get("image_path") or ""))
        if not source_path.is_file():
            return
        gallery_root = (Path(str(getattr(self, "data_dir", "") or "")) / "generated_photos").resolve()
        resolved = source_path.resolve()
        if not resolved.is_relative_to(gallery_root):
            return
        filename = resolved.name
        session = _short(item.get("session_key"), 160)
        trigger = self._image_gallery_trigger(session)
        producer_id, producer_name, producer_type = self._image_gallery_producer(session)
        has_reference = bool(item.get("reference_image_path"))
        intent_kind = _short(item.get("intent_kind"), 40)
        now = time.time()
        values = (
            filename,
            filename,
            float(item.get("created_at") or now),
            now,
            _short(item.get("workflow_kind"), 40),
            self._image_gallery_operation(intent_kind, trigger, has_reference),
            _short(item.get("backend"), 120),
            str(item.get("prompt_text") or "")[:12000],
            _short(item.get("prompt_format"), 60),
            session,
            _short(producer_id, 200),
            _short(producer_name, 120),
            _short(producer_type, 20),
            int(has_reference),
            Path(str(item.get("reference_image_path") or "")).name[:260],
            _short(item.get("image_size"), 60),
            int(resolved.stat().st_size),
            _short(item.get("trace_id"), 80),
            json.dumps((item.get("presets") or [])[:12], ensure_ascii=False),
            trigger,
            intent_kind,
            now,
        )
        with self._image_gallery_session() as conn:
            conn.execute(
                """
                INSERT INTO images (
                    path, filename, created_at, recorded_at, kind, operation, backend,
                    prompt, prompt_format, session, producer_id, producer_name,
                    producer_type, has_reference, reference_path, image_size, file_size,
                    trace, presets, trigger, intent_kind, source, metadata_quality,
                    missing, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'astrbot', 'complete', 0, ?)
                ON CONFLICT(path) DO UPDATE SET
                    filename = excluded.filename,
                    created_at = excluded.created_at,
                    recorded_at = excluded.recorded_at,
                    kind = excluded.kind,
                    operation = excluded.operation,
                    backend = excluded.backend,
                    prompt = excluded.prompt,
                    prompt_format = excluded.prompt_format,
                    session = excluded.session,
                    producer_id = excluded.producer_id,
                    producer_name = excluded.producer_name,
                    producer_type = excluded.producer_type,
                    has_reference = excluded.has_reference,
                    reference_path = excluded.reference_path,
                    image_size = excluded.image_size,
                    file_size = excluded.file_size,
                    trace = excluded.trace,
                    presets = excluded.presets,
                    trigger = excluded.trigger,
                    intent_kind = excluded.intent_kind,
                    source = 'astrbot',
                    metadata_quality = 'complete',
                    missing = 0,
                    updated_at = excluded.updated_at
                """,
                values,
            )

    def _record_recent_photo_generation(
        self,
        *,
        trace_id: str,
        session_key: str,
        workflow_kind: str,
        backend: str,
        ok: bool,
        prompt_text: str,
        image_path: str = "",
        note: str = "",
        reference_image_path: str = "",
        image_size: str = "",
        elapsed_ms: int = 0,
        presets: list[str] | None = None,
    ) -> None:
        super()._record_recent_photo_generation(
            trace_id=trace_id,
            session_key=session_key,
            workflow_kind=workflow_kind,
            backend=backend,
            ok=ok,
            prompt_text=prompt_text,
            image_path=image_path,
            note=note,
            reference_image_path=reference_image_path,
            image_size=image_size,
            elapsed_ms=elapsed_ms,
            presets=presets,
        )
        if not ok or not image_path:
            return
        try:
            self._image_gallery_store_generation(
                {
                    "trace_id": trace_id,
                    "session_key": session_key,
                    "workflow_kind": workflow_kind,
                    "backend": backend,
                    "prompt_text": prompt_text,
                    "prompt_format": getattr(self, "_photo_generation_prompt_format_mode", lambda: "")(),
                    "image_path": image_path,
                    "reference_image_path": reference_image_path,
                    "image_size": image_size,
                    "presets": presets or [],
                }
            )
        except Exception as exc:
            log.warning("image gallery metadata write failed: %s", _short(exc, 180))

    def _annotate_recent_photo_generation(
        self,
        *,
        image_path: str = "",
        session_key: str = "",
        trigger: str = "",
        intent_kind: str = "",
        sent: bool | None = None,
        caption: str = "",
        scene_preset: str = "",
        tool_name: str = "",
    ) -> None:
        super()._annotate_recent_photo_generation(
            image_path=image_path,
            session_key=session_key,
            trigger=trigger,
            intent_kind=intent_kind,
            sent=sent,
            caption=caption,
            scene_preset=scene_preset,
            tool_name=tool_name,
        )
        try:
            filename = Path(str(image_path or "")).name
            session = _short(session_key, 160)
            if not filename and not session:
                return
            with self._image_gallery_session() as conn:
                row = conn.execute(
                    "SELECT id, has_reference FROM images "
                    "WHERE (? != '' AND path = ?) OR (? != '' AND session = ?) "
                    "ORDER BY id DESC LIMIT 1",
                    (filename, filename, session, session),
                ).fetchone()
                if not row:
                    return
                effective_trigger = _short(trigger, 60)
                effective_kind = _short(intent_kind, 40)
                operation = self._image_gallery_operation(
                    effective_kind,
                    effective_trigger or self._image_gallery_trigger(session),
                    bool(row[1]),
                )
                conn.execute(
                    "UPDATE images SET trigger = CASE WHEN ? != '' THEN ? ELSE trigger END, "
                    "intent_kind = CASE WHEN ? != '' THEN ? ELSE intent_kind END, "
                    "operation = ?, sent = COALESCE(?, sent), "
                    "caption = CASE WHEN ? != '' THEN ? ELSE caption END, "
                    "updated_at = ? WHERE id = ?",
                    (
                        effective_trigger,
                        effective_trigger,
                        effective_kind,
                        effective_kind,
                        operation,
                        None if sent is None else int(bool(sent)),
                        _short(caption, 1000),
                        _short(caption, 1000),
                        time.time(),
                        row[0],
                    ),
                )
        except Exception as exc:
            log.warning("image gallery metadata annotate failed: %s", _short(exc, 180))
