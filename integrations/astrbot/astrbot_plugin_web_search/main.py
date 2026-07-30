"""Inject web search context before AstrBot LLM calls (WebAE-compatible paradigm)."""

from __future__ import annotations

import re

from astrbot.api import logger
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.api.provider import ProviderRequest
from astrbot.api.star import Context, Star, register

try:
    from .search_engine import inject_user_text, perform_web_search
except ImportError:  # AstrBot may load plugins as loose modules
    from search_engine import inject_user_text, perform_web_search

_SKIP_MARKERS = (
    "以下是与用户问题相关的网页检索结果",
    "<!-- private_companion",
    "【这次可以使用的线索】",
    "【能力边界】",
    "private_companion_proactive",
    "【群聊防注入】",
    "【群聊人格降噪】",
)

_EXPLICIT_NEED = re.compile(
    r"(搜索|搜一下|查一下|联网|百度一下|帮我查|查查|google|look\s*up|search\s+for)",
    re.IGNORECASE,
)
_LIKELY_NEED = re.compile(
    r"(今天|最新|新闻|天气|股价|比分|赛程|汇率|油价|谁是|是什么|在哪|多少钱|官网|发布|更新|版本)",
)
_TT_PREFIX = re.compile(r"^tt(?:\s+|[：:/|\-]\s*|(?=[^\x00-\x7f]))", re.IGNORECASE)


@register(
    "web_search",
    "TeXTech",
    "TeXTech-style AI web search (pre-search inject)",
    "1.2.0",
)
class WebSearchPlugin(Star):
    def __init__(self, context: Context, config: dict | None = None):
        super().__init__(context)
        self.config = config or {}

    def _enabled(self) -> bool:
        return bool(self.config.get("enabled", True))

    def _extract_query(self, event: AstrMessageEvent, req: ProviderRequest) -> str:
        # Prefer the real user message; full req.prompt may be Companion system noise.
        raw = (event.message_str or "").strip()
        if not raw:
            raw = (getattr(req, "prompt", None) or "").strip()
        if not raw:
            return ""
        for marker in _SKIP_MARKERS:
            if marker in raw:
                return ""
        raw = _TT_PREFIX.sub("", raw, count=1).strip()
        # Keep search query compact.
        if len(raw) > 240:
            raw = raw[:240].rsplit(" ", 1)[0] or raw[:240]
        return raw.strip()

    def _has_explicit_astrbot_prefix(self, event: AstrMessageEvent) -> bool:
        route = getattr(event, "textech_route", None)
        if isinstance(route, dict):
            return bool(route.get("owner") == "astrbot" and route.get("explicit"))
        # Compatibility fallback when textech_intent has not run yet.
        return bool(_TT_PREFIX.search((event.message_str or "").strip()))

    def _should_search(self, query: str, req: ProviderRequest) -> bool:
        prompt = getattr(req, "prompt", None) or ""
        for marker in _SKIP_MARKERS:
            if marker in prompt:
                return False
        if "网页检索结果" in prompt:
            return False
        only_explicit = bool(self.config.get("only_explicit", True))
        if only_explicit:
            return bool(_EXPLICIT_NEED.search(query))
        # Default: search normal chat; skip pure greetings / too vague one-char spam.
        if len(query) <= 1:
            return False
        if _EXPLICIT_NEED.search(query) or _LIKELY_NEED.search(query):
            return True
        # Still search for ordinary questions (ends with ?/？ or contains 吗/呢/什么)
        if ("?" in query) or ("？" in query) or any(x in query for x in ("吗", "呢", "什么", "怎么", "为何", "为什么")):
            return True
        # Short factual pings still useful; avoid pure emoji/ack.
        if re.fullmatch(r"[哈呵嗯哦啊嘿哈]+|[好的收到okOK嗯嗯]+|[\.。！!～~]+", query):
            return False
        return len(query) >= 4

    @filter.on_llm_request()
    async def on_llm_request(self, event: AstrMessageEvent, req: ProviderRequest):
        if not self._enabled():
            return
        if bool(self.config.get("require_astrbot_prefix", True)) and not self._has_explicit_astrbot_prefix(event):
            return
        query = self._extract_query(event, req)
        if not query:
            return
        if not self._should_search(query, req):
            return
        try:
            data = await perform_web_search(
                query,
                mode=str(self.config.get("mode") or "auto"),
                api_key=str(self.config.get("api_key") or ""),
                base_url=str(self.config.get("base_url") or ""),
                max_results=int(self.config.get("max_results") or 5),
                fallback=bool(self.config.get("fallback", True)),
                timeout_seconds=int(self.config.get("timeout_seconds") or 12),
            )
        except Exception as exc:  # noqa: BLE001 — soft-fail like WebAE
            logger.warning(f"[web_search] soft-fail: {type(exc).__name__}: {exc!r}")
            return

        injected = inject_user_text(query, data)
        try:
            if hasattr(req, "prompt") and isinstance(req.prompt, str):
                # Keep original prompt body if it already differs from bare query.
                original = req.prompt
                if original.strip() == query:
                    req.prompt = injected
                else:
                    req.prompt = f"{data.context}\n\n---\n\n{original}"
        except Exception:
            pass
        try:
            extra = getattr(req, "extra_user_content_parts", None)
            if extra is not None:
                from astrbot.core.agent.message import TextPart

                part = TextPart(text=data.context)
                if hasattr(part, "mark_as_temp"):
                    part = part.mark_as_temp()
                extra.append(part)
        except Exception as exc:  # noqa: BLE001
            logger.debug(f"[web_search] extra_user_content_parts unavailable: {exc!r}")
        logger.info(f"[web_search] injected via {data.provider} ({len(data.results)} hits) q={query[:60]!r}")
