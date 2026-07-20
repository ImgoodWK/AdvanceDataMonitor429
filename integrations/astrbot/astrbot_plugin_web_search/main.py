"""Inject web search context before AstrBot LLM calls (WebAE-compatible paradigm)."""

from __future__ import annotations

from astrbot.api import logger
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.api.provider import ProviderRequest
from astrbot.api.star import Context, Star, register

try:
    from .search_engine import inject_user_text, perform_web_search
except ImportError:  # AstrBot may load plugins as loose modules
    from search_engine import inject_user_text, perform_web_search


@register(
    "web_search",
    "TeXTech",
    "TeXTech-style AI web search (pre-search inject)",
    "1.0.0",
)
class WebSearchPlugin(Star):
    def __init__(self, context: Context, config: dict | None = None):
        super().__init__(context)
        self.config = config or {}

    def _enabled(self) -> bool:
        return bool(self.config.get("enabled", True))

    @filter.on_llm_request()
    async def on_llm_request(self, event: AstrMessageEvent, req: ProviderRequest):
        if not self._enabled():
            return
        query = (getattr(req, "prompt", None) or event.message_str or "").strip()
        if not query:
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
            logger.warning(f"[web_search] soft-fail: {exc}")
            return

        injected = inject_user_text(query, data)
        # Prefer mutating the current user prompt; also append as extra context when supported.
        try:
            if hasattr(req, "prompt") and isinstance(req.prompt, str):
                req.prompt = injected
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
            logger.debug(f"[web_search] extra_user_content_parts unavailable: {exc}")
        logger.info(f"[web_search] injected via {data.provider} ({len(data.results)} hits)")
