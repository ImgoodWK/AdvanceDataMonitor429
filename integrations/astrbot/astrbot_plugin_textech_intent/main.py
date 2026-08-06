"""Shared-bot intent handoff: when WebAE owns the message, stop AstrBot silently."""

from __future__ import annotations

import html
import inspect
import json
import re
from collections.abc import Iterable, Mapping
from typing import List, Optional, Tuple
from urllib.parse import urlsplit

from astrbot.api import logger
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.api.star import Context, Star, register

DEFAULT_WEBAE_PREFIXES = ["webae", "游戏", "mc", "gtnh", "服务器"]
DEFAULT_ASTRBOT_PREFIXES = ["tt"]
DEFAULT_WEBAE_KEYWORDS = [
    "webae", "textech", "gtnh", "tps", "mspt", "仪表盘", "告警", "在线玩家",
    "服务器状态", "adm", "高级数据", "监视器", "内存", "开服", "谁在线",
]
WEBAE_COMMAND_VERBS = {
    "help", "menu", "commands", "帮助", "菜单", "功能", "命令",
    "ping", "在吗", "测试",
    "status", "server", "report", "状态", "服务器", "概况", "播报",
    "players", "online", "人数", "在线", "在线人数",
    "list", "who", "名单", "谁在线", "玩家列表",
    "tps", "mspt", "性能", "延迟",
    "memory", "mem", "内存", "ram",
    "uptime", "运行时间", "开服时间",
    "about", "version", "关于", "版本",
    "reset", "forget", "clear", "重置对话", "忘记对话", "清空对话",
    "ai", "ask", "chat", "问", "对话",
    "botstatus", "机器人状态",
}


# Search WebAE keywords only in prose outside URLs.  In particular, the
# protocol spelling ``https`` contains the keyword ``tps``.
_URL_RE = re.compile(r"https?://\S+", re.IGNORECASE)
_RICH_CARD_COMPONENT_TYPES = {
    "app",
    "appmessage",
    "app_message",
    "ark",
    "miniapp",
    "mini-program",
    "miniprogram",
    "richcard",
    "rich_card",
}
_CARD_COMPONENT_TYPES = {"json", "xml"} | _RICH_CARD_COMPONENT_TYPES
_CARD_COMPONENT_DATA_FIELDS = ("data", "content", "json", "xml", "payload", "ark")
_FORWARD_COMPONENT_TYPES = {"forward", "node", "nodes"}
_MAX_ROUTE_COMPONENTS = 32
_MAX_ROUTE_CARD_DEPTH = 6
_MAX_ROUTE_CARD_NODES = 128
_MAX_ROUTE_CARD_CHARS = 256 * 1024
_BILIBILI_VIDEO_HOSTS = {"www.bilibili.com", "m.bilibili.com"}
_BILIBILI_SHORT_HOSTS = {"b23.tv", "www.b23.tv"}
_BILIBILI_VIDEO_PATH_RE = re.compile(
    r"^/video/(?:BV[0-9A-Za-z]{10}|av[0-9]+)(?:/|$)",
    re.IGNORECASE,
)
_CARD_NAVIGATION_KEYS = {
    "href",
    "jumpurl",
    "legacyurl",
    "link",
    "pcjumpurl",
    "qqdocurl",
    "shareurl",
    "targeturl",
    "url",
    "weburl",
}
_CARD_MEDIA_KEYS = {
    "attachment",
    "attachments",
    "audio",
    "avatar",
    "cover",
    "coverurl",
    "file",
    "icon",
    "image",
    "imageurl",
    "img",
    "logo",
    "media",
    "medias",
    "pic",
    "poster",
    "preview",
    "previewurl",
    "record",
    "sourceicon",
    "sourcelogo",
    "src",
    "thumb",
    "thumbnail",
    "thumbnailurl",
    "video",
    "voice",
}
_MEDIA_COMPONENT_TYPES = {
    "audio",
    "file",
    "image",
    "music",
    "record",
    "video",
    "voice",
}
_CARD_XML_TAG_RE = re.compile(
    r"<(?P<tag>[A-Za-z_][A-Za-z0-9_.:-]*)\b(?P<attrs>[^<>]{0,8192})>",
    re.IGNORECASE,
)
_CARD_XML_URL_ATTR_RE = re.compile(
    r"\b(?:jumpurl|qqdocurl|pcjumpurl|legacyurl|weburl|shareurl|targeturl|href|link|url)"
    r"\s*=\s*(?P<quote>[\"'])(?P<value>.*?)(?P=quote)",
    re.IGNORECASE,
)


def _text_outside_urls(value: str) -> str:
    return _URL_RE.sub(" ", value)


def _component_type(value: object) -> str:
    if isinstance(value, Mapping):
        raw = value.get("type") or value.get("kind") or value.get("element_type") or ""
    else:
        raw = (
            getattr(value, "type", None)
            or getattr(value, "kind", None)
            or getattr(value, "element_type", None)
        )
        if raw is None:
            raw = type(value).__name__
    enum_value = getattr(raw, "value", None)
    if enum_value is not None:
        raw = enum_value
    elif not isinstance(raw, (str, bytes)):
        raw = getattr(raw, "name", None) or raw
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8", "replace")
    return str(raw or "").strip().lower()


def _component_value(value: object, key: str, default: object = None) -> object:
    if isinstance(value, Mapping):
        return value.get(key, default)
    return getattr(value, key, default)


def _normalized_card_key(value: object) -> str:
    return re.sub(r"[^a-z0-9]", "", str(value).casefold())


def _contains_bilibili_card_url(value: str) -> bool:
    for match in _URL_RE.finditer(value):
        try:
            parsed = urlsplit(match.group(0))
            host = (parsed.hostname or "").casefold().rstrip(".")
        except ValueError:
            continue
        if host in _BILIBILI_SHORT_HOSTS or (
            host in _BILIBILI_VIDEO_HOSTS
            and _BILIBILI_VIDEO_PATH_RE.match(parsed.path or "") is not None
        ):
            return True
    return False


def _card_has_navigation(value: object) -> bool:
    """Recognize only semantic card navigation, excluding media transport URLs."""

    seen_objects: set[int] = set()
    node_count = 0

    def walk(item: object, *, depth: int = 0, allow_url: bool = False) -> bool:
        nonlocal node_count
        if item is None or depth > _MAX_ROUTE_CARD_DEPTH or node_count >= _MAX_ROUTE_CARD_NODES:
            return False
        if isinstance(item, bytes):
            item = item.decode("utf-8", "replace")
        if isinstance(item, str):
            bounded = item[:_MAX_ROUTE_CARD_CHARS]
            variants = (html.unescape(bounded), bounded.replace(r"\/", "/"), bounded)
            visited_text: set[str] = set()
            for candidate in variants:
                if not candidate or candidate in visited_text:
                    continue
                visited_text.add(candidate)
                if allow_url and _URL_RE.search(candidate):
                    return True
                try:
                    decoded = json.loads(candidate)
                except (TypeError, ValueError):
                    decoded = candidate
                if decoded != candidate and walk(decoded, depth=depth + 1, allow_url=allow_url):
                    return True
                for tag_match in _CARD_XML_TAG_RE.finditer(candidate):
                    tag = _normalized_card_key(tag_match.group("tag").rsplit(":", 1)[-1])
                    if tag in _CARD_MEDIA_KEYS:
                        continue
                    for attr_match in _CARD_XML_URL_ATTR_RE.finditer(tag_match.group("attrs")):
                        if _URL_RE.search(html.unescape(attr_match.group("value"))):
                            return True
                if not allow_url and _contains_bilibili_card_url(candidate):
                    return True
            return False
        if isinstance(item, Mapping):
            object_id = id(item)
            if object_id in seen_objects:
                return False
            seen_objects.add(object_id)
            node_count += 1
            normalized_keys = {_normalized_card_key(key) for key in item}
            embedded_type = _normalized_card_key(item.get("type") or item.get("kind") or "")
            if (
                embedded_type in _MEDIA_COMPONENT_TYPES
                and not normalized_keys.intersection(_CARD_NAVIGATION_KEYS - {"url"})
            ):
                return False
            for key, child in item.items():
                normalized_key = _normalized_card_key(key)
                if normalized_key in _CARD_MEDIA_KEYS:
                    continue
                if walk(
                    child,
                    depth=depth + 1,
                    allow_url=normalized_key in _CARD_NAVIGATION_KEYS,
                ):
                    return True
            return False
        if isinstance(item, (list, tuple, set)):
            object_id = id(item)
            if object_id in seen_objects:
                return False
            seen_objects.add(object_id)
            node_count += 1
            return any(walk(child, depth=depth + 1, allow_url=allow_url) for child in item)
        return False

    return walk(value)


def _card_component_has_navigation(value: object) -> bool:
    """Inspect only explicit rich-card payload surfaces."""

    data = _component_value(value, "data")
    candidates: list[object] = []
    if isinstance(value, Mapping):
        candidates.append(value)
    for source in (value, data):
        if source is None:
            continue
        for key in _CARD_COMPONENT_DATA_FIELDS:
            candidate = _component_value(source, key)
            if candidate is not None:
                candidates.append(candidate)
    return any(_card_has_navigation(candidate) for candidate in candidates)


def _top_level_components(value: object) -> Iterable[object]:
    if isinstance(value, Mapping):
        yield value
        return
    if isinstance(value, (str, bytes)):
        yield value
        return
    if isinstance(value, Iterable):
        try:
            for index, item in enumerate(value):
                if index >= _MAX_ROUTE_COMPONENTS:
                    break
                yield item
        except Exception:
            return
        return
    if value is not None:
        yield value


def _component_is_link_or_forward(value: object) -> bool:
    component_type = _component_type(value)
    if component_type in _FORWARD_COMPONENT_TYPES:
        return True
    if component_type == "share":
        data = _component_value(value, "data")
        candidates = [_component_value(value, "url")]
        if isinstance(data, Mapping):
            candidates.append(data.get("url"))
        return any(
            isinstance(candidate, (str, bytes))
            and _URL_RE.search(
                candidate.decode("utf-8", "replace")
                if isinstance(candidate, bytes)
                else candidate
            )
            for candidate in candidates
        )
    if component_type in _CARD_COMPONENT_TYPES:
        return _card_component_has_navigation(value)
    message_type = str(_component_value(value, "message_type", "")).strip().lower()
    raw_type = str(_component_value(value, "type", "")).strip().lower()
    if message_type in {"103", "forward", "node", "nodes"} or raw_type in (
        _FORWARD_COMPONENT_TYPES
    ):
        return True
    if not isinstance(value, Mapping):
        return False
    for key in ("content", "text", "message_str", "json", "xml"):
        content = value.get(key)
        if key in {"json", "xml"} and _card_has_navigation(content):
            return True
        if isinstance(content, bytes):
            content = content.decode("utf-8", "replace")
        if key not in {"json", "xml"} and isinstance(content, str):
            bounded = content[:_MAX_ROUTE_CARD_CHARS]
            probe = html.unescape(bounded).lstrip()
            if probe.startswith(("{", "[", "<")):
                if _card_has_navigation(bounded):
                    return True
            elif _URL_RE.search(bounded):
                return True
    return False


def _qqofficial_raw_sources(event: AstrMessageEvent) -> tuple[object, ...]:
    try:
        raw_message = getattr(getattr(event, "message_obj", None), "raw_message", None)
    except Exception:
        return ()
    if raw_message is None:
        return ()
    if isinstance(raw_message, Mapping):
        raw_data = raw_message
        msg_elements = raw_message.get("msg_elements")
    else:
        try:
            raw_data = getattr(raw_message, "raw_data", None)
            msg_elements = getattr(raw_message, "msg_elements", None)
        except Exception:
            return ()
    sources: list[object] = []
    if isinstance(msg_elements, (Mapping, list, tuple)) and msg_elements:
        sources.append(msg_elements)
    if isinstance(raw_data, Mapping) and raw_data:
        sources.append(raw_data)
    return tuple(sources)


async def _is_link_or_forward_request(event: AstrMessageEvent, raw: str) -> bool:
    """Use only bounded, explicit message surfaces to reserve link requests."""

    if _URL_RE.search(raw or ""):
        return True
    chain = None
    getter = getattr(event, "get_messages", None)
    if callable(getter):
        try:
            chain = getter()
            if inspect.isawaitable(chain):
                chain = await chain
        except Exception:
            chain = None
    if chain is None:
        try:
            chain = getattr(getattr(event, "message_obj", None), "message", None)
        except Exception:
            chain = None
    try:
        for item in _top_level_components(chain):
            try:
                if _component_is_link_or_forward(item):
                    return True
            except Exception:
                continue
    except Exception:
        pass
    for source in _qqofficial_raw_sources(event):
        try:
            for item in _top_level_components(source):
                try:
                    if _component_is_link_or_forward(item):
                        return True
                except Exception:
                    continue
        except Exception:
            continue
    return False


def _is_unambiguous_webae_request(raw: str, reason: str, command_prefix: str) -> bool:
    if reason.startswith("explicit_webae:"):
        return reason.split(":", 1)[1].strip().casefold() == "webae"
    prefix = (command_prefix or "").strip()
    return bool(
        reason.startswith("webae_command:")
        and prefix
        and raw.lstrip().startswith(prefix)
    )


def _tokens(values: Optional[List[str]], defaults: List[str]) -> List[str]:
    if not values:
        return list(defaults)
    cleaned = [str(v).strip() for v in values if str(v).strip()]
    return cleaned or list(defaults)


def _starts_with_token(raw: str, token: str, *, allow_compact: bool = False) -> bool:
    if not token or len(raw) < len(token):
        return False
    if not raw.lower().startswith(token.lower()):
        return False
    if len(raw) == len(token):
        return True
    nxt = raw[len(token)]
    if nxt.isspace() or nxt in ":：/-|":
        return True
    # `tt生图` / `tt搜索` are common in QQ.  Do not treat an English word such
    # as `ttl` as a compact prefix hit.
    return bool(allow_compact and not nxt.isascii())


def _strip_leading_token(raw: str, token_len: int) -> str:
    if len(raw) <= token_len:
        return ""
    rest = raw[token_len:].strip()
    if rest[:1] in ":：-|":
        return rest[1:].strip()
    return rest


def _match_leading(
    raw: str,
    tokens: List[str],
    *,
    allow_compact: bool = False,
) -> Optional[Tuple[str, str]]:
    best: Optional[Tuple[str, str]] = None
    for token in tokens:
        if not _starts_with_token(raw, token, allow_compact=allow_compact):
            continue
        if best is None or len(token) >= len(best[0]):
            best = (token, _strip_leading_token(raw, len(token)))
    return best


def _first_word(text: str) -> str:
    parts = text.strip().split(None, 1)
    return parts[0].lower() if parts else ""


def classify(
    raw: str,
    *,
    webae_prefixes: List[str],
    astr_prefixes: List[str],
    keywords: List[str],
    command_prefix: str,
    allow_compact_astrbot_prefix: bool = True,
) -> Tuple[str, str, str]:
    """Return (owner, text_for_handler, reason) where owner is webae|astrbot."""
    text = (raw or "").strip()
    hit = _match_leading(text, webae_prefixes)
    if hit:
        return "webae", hit[1], f"explicit_webae:{hit[0]}"
    hit = _match_leading(
        text,
        astr_prefixes,
        allow_compact=allow_compact_astrbot_prefix,
    )
    if hit:
        return "astrbot", hit[1], f"explicit_astrbot:{hit[0]}"

    cmd = text
    prefix = (command_prefix or "").strip()
    if prefix and cmd.startswith(prefix):
        cmd = cmd[len(prefix):].strip()
    verb = _first_word(cmd)
    if verb in WEBAE_COMMAND_VERBS:
        return "webae", text, f"webae_command:{verb}"

    lower = _text_outside_urls(text).lower()
    for keyword in keywords:
        if keyword and keyword.lower() in lower:
            return "webae", text, f"webae_keyword:{keyword}"
    return "astrbot", text, "default_astrbot"


@register(
    "textech_intent",
    "TeXTech",
    "TeXTech/WebAE shared-bot intent handoff",
    "1.2.1",
)
class TextechIntentPlugin(Star):
    def __init__(self, context: Context, config: dict | None = None):
        super().__init__(context)
        self.config = config or {}

    def _enabled(self) -> bool:
        return bool(self.config.get("enabled", True))

    @staticmethod
    def _mark_route(
        event: AstrMessageEvent,
        owner: str,
        reason: str,
        prefix: str = "",
        *,
        original_text: str = "",
        routed_text: str = "",
    ) -> None:
        """Expose one routing decision to later LLM hooks without changing AstrBot APIs."""
        try:
            setattr(
                event,
                "textech_route",
                {
                    "owner": owner,
                    "reason": reason,
                    "explicit": reason.startswith("explicit_astrbot:"),
                    "prefix": prefix,
                    # Downstream feature plugins may need to enforce an explicit
                    # prefix after this plugin has stripped it from message_str.
                    "original_text": original_text,
                    "routed_text": routed_text,
                },
            )
        except Exception:
            pass

    @filter.event_message_type(filter.EventMessageType.ALL, priority=100)
    async def on_message(self, event: AstrMessageEvent):
        if not self._enabled():
            return
        raw = (event.message_str or "").strip()
        if not raw:
            return
        command_prefix = str(self.config.get("command_prefix") or "/")
        owner, remainder, reason = classify(
            raw,
            webae_prefixes=_tokens(self.config.get("webae_explicit_prefixes"), DEFAULT_WEBAE_PREFIXES),
            astr_prefixes=_tokens(self.config.get("astrbot_explicit_prefixes"), DEFAULT_ASTRBOT_PREFIXES),
            keywords=_tokens(self.config.get("webae_intent_keywords"), DEFAULT_WEBAE_KEYWORDS),
            command_prefix=command_prefix,
            allow_compact_astrbot_prefix=bool(self.config.get("allow_compact_astrbot_prefix", True)),
        )
        if (
            owner == "webae"
            and not _is_unambiguous_webae_request(raw, reason, command_prefix)
            and await _is_link_or_forward_request(event, raw)
        ):
            owner, remainder, reason = "astrbot", raw, "link_summary_request"
            logger.info("[textech_intent] reserved link/forward for AstrBot")
        if owner == "webae":
            self._mark_route(
                event,
                owner,
                reason,
                original_text=raw,
                routed_text=remainder,
            )
            logger.info(f"[textech_intent] handoff to WebAE ({reason}): {raw[:80]}")
            event.stop_event()
            event.should_call_llm(False)
            return
        prefix = reason.split(":", 1)[1] if reason.startswith("explicit_astrbot:") else ""
        self._mark_route(
            event,
            owner,
            reason,
            prefix,
            original_text=raw,
            routed_text=remainder,
        )
        if prefix and remainder and remainder != raw:
            # Prefer stripped text for downstream AstrBot LLM when user used an explicit prefix.
            try:
                event.message_str = remainder
                if event.message_obj is not None:
                    event.message_obj.message_str = remainder
            except Exception:
                pass
            logger.info(f"[textech_intent] AstrBot explicit prefix stripped ({reason})")
