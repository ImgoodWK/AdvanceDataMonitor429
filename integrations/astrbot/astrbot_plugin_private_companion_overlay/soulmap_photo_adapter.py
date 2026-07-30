# -*- coding: utf-8 -*-
"""Persona / SoulMap visual notes -> photo prompt, plus optional LLM rewrite for upstream safety."""

from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Any

from astrbot.api import logger
from astrbot.api.event import AstrMessageEvent

from .helpers import _single_line

DEFAULT_PHOTO_PROMPT_REWRITE_SYSTEM = """You rewrite image-generation prompts so upstream T2I APIs are less likely to refuse them for policy wording, while preserving the user's intent.

Rules:
- Keep the subject, scene, composition, art style, and any character appearance exactly.
- Prefer clear English (or bilingual) visual description suitable for text-to-image.
- Soften slang / suggestive / policy-sensitive phrasing into neutral artistic wording.
- Do NOT add NSFW, sexual content, violence, gore, or underage implications.
- Do NOT replace a named character with a different person.
- If the draft already has "Positive prompt:" / "Negative prompt:", keep that structure.
- Output ONLY the rewritten prompt text. No explanation, no markdown fences.
""".strip()

_PERSONA_PROFILES = Path("/AstrBot/data/plugin_data/astrbot_plugin_persona_lib/personas.json")
_SOULMAP_PROFILES = Path("/AstrBot/data/plugin_data/astrbot_plugin_soulmap/user_profiles.json")
_SOULMAP_CACHE: dict[str, Any] = {"ts": 0.0, "data": None, "path": ""}
_SOULMAP_CACHE_TTL = 8.0


class SoulmapPhotoAdapterMixin:
    """Read persona_lib / SoulMap visual notes and rewrite photo prompts via the configured chat LLM."""

    def _soulmap_profiles_path(self) -> Path:
        configured = _single_line(getattr(self, "soulmap_profiles_path", ""), 300)
        if configured:
            return Path(configured)
        # Prefer persona_lib; fall back to legacy SoulMap file if new file missing.
        if _PERSONA_PROFILES.exists():
            return _PERSONA_PROFILES
        return _SOULMAP_PROFILES

    def _soulmap_load_profiles(self) -> dict[str, Any]:
        now = time.time()
        path = self._soulmap_profiles_path()
        path_s = str(path)
        cached = _SOULMAP_CACHE.get("data")
        if (
            cached is not None
            and _SOULMAP_CACHE.get("path") == path_s
            and (now - float(_SOULMAP_CACHE.get("ts") or 0.0)) < _SOULMAP_CACHE_TTL
        ):
            return cached if isinstance(cached, dict) else {}
        try:
            raw = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:
            logger.debug("[PrivateCompanion] persona/SoulMap profiles unread: %s", _single_line(exc, 120))
            raw = {}
        data = raw if isinstance(raw, dict) else {}
        _SOULMAP_CACHE["ts"] = now
        _SOULMAP_CACHE["data"] = data
        _SOULMAP_CACHE["path"] = path_s
        return data

    @staticmethod
    def _is_persona_lib_entry(entry: dict[str, Any]) -> bool:
        return any(k in entry for k in ("names", "appearance", "personality", "attributes", "tags"))

    @staticmethod
    def _is_soulmap_entry(entry: dict[str, Any]) -> bool:
        return any(k in entry for k in ("备注", "对用户的称呼", "性别", "爱好"))

    def _soulmap_resolve_profile(
        self,
        user_id: str,
        *,
        event: AstrMessageEvent | None = None,
    ) -> dict[str, Any]:
        uid = _single_line(user_id, 120)
        if not uid:
            return {}
        profiles = self._soulmap_load_profiles()
        entry = profiles.get(uid)
        if isinstance(entry, dict):
            if self._is_persona_lib_entry(entry) or self._is_soulmap_entry(entry):
                return entry
            session_id = ""
            if event is not None:
                session_id = _single_line(getattr(event, "unified_msg_origin", ""), 180) or _single_line(
                    getattr(event, "session_id", ""), 120
                )
            if session_id and isinstance(entry.get(session_id), dict):
                return entry[session_id]
            best: dict[str, Any] = {}
            best_score = -1
            for value in entry.values():
                if not isinstance(value, dict):
                    continue
                score = sum(1 for v in value.values() if str(v or "").strip())
                if score > best_score:
                    best = value
                    best_score = score
            if best:
                return best
        if event is not None:
            session_id = _single_line(getattr(event, "unified_msg_origin", ""), 180)
            nested = profiles.get(session_id)
            if isinstance(nested, dict):
                return nested
        return {}

    @staticmethod
    def _soulmap_parse_visual_notes(notes: str) -> dict[str, Any]:
        text = str(notes or "").strip()
        styles: list[str] = []
        characters: dict[str, str] = {}
        if not text:
            return {"styles": styles, "characters": characters}

        chunks = re.split(r"[；;]+", text)
        for raw in chunks:
            part = _single_line(raw, 240)
            if not part:
                continue
            part = re.sub(r"^\d+[.、．]\s*", "", part).strip()
            style_match = re.match(r"^(?:默认)?画风\s*[：:]\s*(.+)$", part)
            if style_match:
                style = _single_line(style_match.group(1), 160)
                if style and style not in styles:
                    styles.append(style)
                continue
            img_match = re.match(r"^([^=：:]{1,24})\s*=\s*img\s*=\s*(.+)$", part, flags=re.I)
            if img_match:
                alias = _single_line(img_match.group(1), 24)
                desc = _single_line(img_match.group(2), 200)
                if alias and desc:
                    prev = characters.get(alias, "")
                    if not prev or len(desc) >= len(prev):
                        characters[alias] = desc
                    if len(desc) >= len(characters.get("img", "")):
                        characters["img"] = desc
                continue
            alias_match = re.match(r"^([^=：:]{1,24})\s*=\s*(.+)$", part)
            if alias_match:
                alias = _single_line(alias_match.group(1), 24)
                desc = _single_line(alias_match.group(2), 200)
                if not alias or not desc:
                    continue
                if alias in {"默认画风", "画风"}:
                    if desc not in styles:
                        styles.append(desc)
                    continue
                if any(tok in desc for tok in ("apikey", "API", "可以正常发", "备份", "Kopia")):
                    continue
                prev = characters.get(alias, "")
                if not prev or len(desc) >= len(prev):
                    characters[alias] = desc
        return {"styles": styles, "characters": characters}

    def _persona_lib_visual_parts(self, profile: dict[str, Any], user_prompt: str) -> list[str]:
        """Build visual context from persona_lib fields, including arbitrary presets."""
        parts: list[str] = []
        names = profile.get("names") or []
        if isinstance(names, str):
            names = [n.strip() for n in re.split(r"[|｜,/，、\s]+", names) if n.strip()]
        elif not isinstance(names, list):
            names = []
        names = [str(n).strip() for n in names if str(n).strip()]

        appearance = _single_line(profile.get("appearance") or "", 200)
        personality = _single_line(profile.get("personality") or "", 120)
        tags = profile.get("tags") or []
        if isinstance(tags, str):
            tags = [item.strip() for item in re.split(r"[|｜,/，、]+", tags) if item.strip()]
        elif not isinstance(tags, list):
            tags = []
        attributes = profile.get("attributes") or {}
        if not isinstance(attributes, dict):
            attributes = {}
        extra = str(profile.get("extra") or "").strip()

        prompt = str(user_prompt or "")
        prompt_compact = re.sub(r"\s+", "", prompt).lower()

        matched_names: list[str] = []
        for alias in sorted(names, key=len, reverse=True):
            alias_l = alias.lower()
            if alias_l in prompt_compact or alias in prompt:
                matched_names.append(alias)

        # If no alias hit but profile has appearance, still inject as default look
        if appearance:
            if matched_names:
                for alias in matched_names[:3]:
                    parts.append(f"角色{alias}: {appearance}")
            else:
                label = matched_names[0] if matched_names else (names[0] if names else "角色")
                parts.append(f"角色{label}: {appearance}")
        if personality and (matched_names or not parts):
            parts.append(f"性格: {personality}")
        clean_tags = [_single_line(tag, 48) for tag in tags if _single_line(tag, 48)]
        if clean_tags:
            parts.append("标签: " + " / ".join(clean_tags[:10]))
        clean_attributes: list[str] = []
        for raw_key, raw_value in attributes.items():
            key = _single_line(raw_key, 48)
            value = _single_line(raw_value, 120)
            if key and value:
                clean_attributes.append(f"{key}={value}")
            if len(clean_attributes) >= 16:
                break
        if clean_attributes:
            parts.append("预设属性: " + "；".join(clean_attributes))

        # Also parse legacy visual notes that may live in extra after migration
        if extra:
            parsed = self._soulmap_parse_visual_notes(extra)
            styles: list[str] = list(parsed.get("styles") or [])
            characters: dict[str, str] = dict(parsed.get("characters") or {})
            if styles:
                uniq: list[str] = []
                for style in styles:
                    if style not in uniq:
                        uniq.append(style)
                parts.append("默认画风: " + " / ".join(uniq[-2:]))
            for alias, desc in sorted(characters.items(), key=lambda item: len(item[0]), reverse=True):
                alias_key = alias.strip()
                if not alias_key:
                    continue
                alias_l = alias_key.lower()
                if alias_l in prompt_compact or alias_key in prompt or not matched_names:
                    parts.append(f"角色{alias_key}: {desc}")
                    if len(parts) >= 5:
                        break
        return parts

    @staticmethod
    def _persona_alias_hit(profile: dict[str, Any], user_prompt: str) -> bool:
        names = profile.get("names") or []
        if isinstance(names, str):
            names = [item.strip() for item in re.split(r"[|｜,/，、\s]+", names) if item.strip()]
        if not isinstance(names, list):
            return False
        prompt = re.sub(r"\s+", "", str(user_prompt or "")).lower()
        return any(str(name or "").strip().lower() in prompt for name in names if str(name or "").strip())

    def _persona_lib_named_visual_context(self, user_prompt: str) -> str:
        """Resolve every shared persona whose alias is named in the image request."""
        parts: list[str] = []
        for _subject_id, profile in self._soulmap_load_profiles().items():
            if not isinstance(profile, dict) or not self._is_persona_lib_entry(profile):
                continue
            if str(profile.get("scope") or "shared") == "private":
                continue
            if not self._persona_alias_hit(profile, user_prompt):
                continue
            for item in self._persona_lib_visual_parts(profile, user_prompt):
                if item and item not in parts:
                    parts.append(item)
                if len(parts) >= 12:
                    break
            if len(parts) >= 12:
                break
        return _single_line("；".join(parts), 1200) if parts else ""

    def _soulmap_compose_photo_visual_context(
        self,
        user_id: str,
        user_prompt: str,
        *,
        event: AstrMessageEvent | None = None,
    ) -> str:
        named_context = self._persona_lib_named_visual_context(user_prompt)
        if named_context:
            return named_context

        profile = self._soulmap_resolve_profile(user_id, event=event)
        if not profile:
            return ""

        # New persona_lib shape
        if self._is_persona_lib_entry(profile):
            parts = self._persona_lib_visual_parts(profile, user_prompt)
            return _single_line("；".join(parts), 1200) if parts else ""

        # Legacy SoulMap shape
        notes = str(profile.get("备注") or "").strip()
        parsed = self._soulmap_parse_visual_notes(notes)
        styles: list[str] = list(parsed.get("styles") or [])
        characters: dict[str, str] = dict(parsed.get("characters") or {})
        prompt = str(user_prompt or "")
        prompt_compact = re.sub(r"\s+", "", prompt).lower()

        matched: list[tuple[str, str]] = []
        for alias, desc in sorted(characters.items(), key=lambda item: len(item[0]), reverse=True):
            alias_key = alias.strip()
            if not alias_key or len(alias_key) < 1:
                continue
            alias_l = alias_key.lower()
            if alias_l in prompt_compact or alias_key in prompt:
                matched.append((alias_key, desc))

        parts: list[str] = []
        if styles:
            uniq: list[str] = []
            for style in styles:
                if style not in uniq:
                    uniq.append(style)
            parts.append("默认画风: " + " / ".join(uniq[-2:]))
        seen_desc: set[str] = set()
        for alias, desc in matched:
            key = f"{alias}|{desc}"
            if key in seen_desc:
                continue
            seen_desc.add(key)
            parts.append(f"角色{alias}: {desc}")
            if len(parts) >= 4:
                break
        return _single_line("；".join(parts), 520)

    async def _rewrite_photo_prompt_for_upstream(
        self,
        prompt_text: str,
        *,
        user_intent: str = "",
        soulmap_visual: str = "",
        event: AstrMessageEvent | None = None,
    ) -> str:
        draft = str(prompt_text or "").strip()
        if not draft:
            return draft
        if not bool(getattr(self, "enable_photo_prompt_llm_rewrite", True)):
            return draft

        system_prompt = str(
            getattr(self, "photo_prompt_llm_rewrite_system_prompt", "") or DEFAULT_PHOTO_PROMPT_REWRITE_SYSTEM
        ).strip()
        timeout = float(getattr(self, "photo_prompt_llm_rewrite_timeout_seconds", 10.0) or 10.0)
        timeout = max(4.0, min(timeout, 20.0))

        provider_id = None
        task_provider = getattr(self, "_task_provider", None)
        if callable(task_provider):
            try:
                provider_id = task_provider(
                    getattr(self, "response_review_provider_id", ""),
                    getattr(self, "mai_style_provider_id", ""),
                    getattr(self, "llm_provider_id", ""),
                )
            except Exception:
                provider_id = None

        user_block = (
            "User intent:\n"
            f"{_single_line(user_intent, 500) or '(unspecified)'}\n\n"
            "Character visual notes (must preserve if present):\n"
            f"{_single_line(soulmap_visual, 520) or '(none)'}\n\n"
            "Draft image prompt to rewrite:\n"
            f"{_single_line(draft, 6500)}"
        )

        llm_call = getattr(self, "_llm_call", None)
        if not callable(llm_call):
            return draft

        try:
            raw = await llm_call(
                user_block,
                max_tokens=900,
                provider_id=provider_id,
                task="photo_prompt_rewrite",
                system_prompt=system_prompt,
                timeout_seconds=timeout,
            )
        except Exception as exc:
            logger.warning(
                "[PrivateCompanion] 生图提示词LLM改写失败，回退原文: %s",
                _single_line(exc, 160),
            )
            return draft

        cleaned = str(raw or "").strip()
        cleaned = re.sub(r"^```(?:\w+)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned).strip()
        cleaned = _single_line(cleaned, 6500)
        if not cleaned or len(cleaned) < 24:
            logger.info("[PrivateCompanion] 生图提示词LLM改写为空，回退原文")
            return draft

        logger.info(
            "[PrivateCompanion] 生图提示词LLM改写: before=%s after=%s",
            _single_line(draft, 180),
            _single_line(cleaned, 180),
        )
        return cleaned

    async def _prepare_photo_prompt_with_soulmap(
        self,
        *,
        user_id: str,
        user_prompt: str,
        kind: str,
        has_reference: bool,
        memory_context: str = "",
        event: AstrMessageEvent | None = None,
    ) -> tuple[str, str]:
        """Build prompt with persona/SoulMap visual notes and optional LLM rewrite.

        Returns (final_prompt, soulmap_visual).
        """
        soulmap_visual = ""
        try:
            soulmap_visual = self._soulmap_compose_photo_visual_context(
                user_id,
                user_prompt,
                event=event,
            )
        except Exception as exc:
            logger.debug("[PrivateCompanion] persona visual compose failed: %s", _single_line(exc, 120))
            soulmap_visual = ""

        builder = getattr(self, "_build_natural_language_photo_prompt", None)
        if callable(builder):
            try:
                prompt_text = builder(
                    prompt=user_prompt,
                    kind=kind,
                    has_reference=has_reference,
                    memory_context=memory_context,
                    soulmap_visual=soulmap_visual,
                )
            except TypeError:
                merged_memory = memory_context
                if soulmap_visual:
                    merged_memory = _single_line(f"{memory_context}\n{soulmap_visual}", 1200)
                prompt_text = builder(
                    prompt=user_prompt,
                    kind=kind,
                    has_reference=has_reference,
                    memory_context=merged_memory,
                )
        else:
            prompt_text = user_prompt

        rewritten = await self._rewrite_photo_prompt_for_upstream(
            prompt_text,
            user_intent=user_prompt,
            soulmap_visual=soulmap_visual,
            event=event,
        )
        return rewritten, soulmap_visual
