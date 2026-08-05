# -*- coding: utf-8 -*-
"""TeXTech ``tt`` routing helpers for Private Companion image generation."""

from __future__ import annotations

import re
from typing import Any


def starts_with_tt(value: Any) -> bool:
    """Match ``tt`` as a routing token, including compact Chinese forms.

    ``tt生图`` is valid, while an English word such as ``ttl`` is not.
    """
    raw = str(value or "").lstrip()
    if len(raw) < 2 or raw[:2].lower() != "tt":
        return False
    if len(raw) == 2:
        return True
    nxt = raw[2]
    return bool(nxt.isspace() or nxt in ":：/-|" or not nxt.isascii())


def explicit_tt_request(event: Any, *fallback_texts: Any) -> bool:
    """Return whether TeXTech routing explicitly assigned this event via ``tt``."""
    route = getattr(event, "textech_route", None)
    if isinstance(route, dict):
        prefix = str(route.get("prefix") or "").strip().lower()
        if (
            route.get("owner") == "astrbot"
            and bool(route.get("explicit"))
            and prefix == "tt"
        ):
            return True
        if starts_with_tt(route.get("original_text")):
            return True

    for value in fallback_texts:
        if starts_with_tt(value):
            return True

    try:
        message_obj = getattr(event, "message_obj", None)
        if starts_with_tt(getattr(message_obj, "message_str", "")):
            return True
    except Exception:
        pass
    return False


def resolve_reference_generation_intent(
    intent: dict[str, Any] | None,
    *,
    text: Any,
    has_reference: bool,
    explicit_tt: bool,
) -> dict[str, Any]:
    """Resolve explicit ``tt`` reference-generation intent.

    Existing text-to-image/selfie intent is upgraded to ``edit`` so the
    supplied image cannot be silently discarded.  If the host recognizer did
    not match, common reference/edit/style phrases can still form an edit
    request.
    """
    result = dict(intent or {})
    if not has_reference or not explicit_tt:
        return result
    raw = str(text or "").strip()
    raw = re.sub(r"^tt(?:\s+|[：:/\-|]+\s*|(?=[^\x00-\x7f]))", "", raw, count=1, flags=re.I).strip()
    compact = re.sub(r"\s+", "", raw)

    existing_prompt = str(result.get("prompt") or "").strip()
    if existing_prompt and starts_with_tt(existing_prompt):
        result["prompt"] = re.sub(
            r"^tt(?:\s+|[：:/\-|]+\s*|(?=[^\x00-\x7f]))",
            "",
            existing_prompt,
            count=1,
            flags=re.I,
        ).strip()

    if not result:
        reference_markers = (
            "参考", "照着", "基于", "按这张", "用这张", "依照", "仿照",
            "改成", "改为", "改图", "修图", "重绘", "p图", "P图",
            "换成", "变成", "做成", "制作成", "画成", "生成",
            "加上", "去掉", "去除", "背景", "滤镜", "色调", "风格",
            "二次元", "写实", "赛博", "像素风", "水彩", "油画",
        )
        question_only = bool(re.match(r"^(?:怎么|如何|为什么|为啥|是什么|图里有什么|你看到了什么)", compact))
        if not compact or question_only or not any(marker in compact for marker in reference_markers):
            return result
        generic = {
            "参考", "参考这张", "参考这张图", "照着这张", "照着这张图",
            "用这张", "用这张图", "按这张", "按这张图", "改图", "修图", "重绘",
        }
        result = {
            "kind": "edit",
            "prompt": "" if compact in generic else raw,
            "raw": raw,
        }
        if not result["prompt"]:
            result["needs_prompt"] = True
    if result.get("needs_prompt"):
        return result
    if str(result.get("kind") or "text2img") in {"text2img", "selfie", "sticker", "edit"}:
        result["kind"] = "edit"
        result["textech_reference_generation"] = True
    return result
