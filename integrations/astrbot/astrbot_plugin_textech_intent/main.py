"""Shared-bot intent handoff: when WebAE owns the message, stop AstrBot silently."""

from __future__ import annotations

from typing import List, Optional, Tuple

from astrbot.api import logger
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.api.star import Context, Star, register

DEFAULT_WEBAE_PREFIXES = ["webae", "游戏", "mc", "gtnh", "服务器"]
DEFAULT_ASTRBOT_PREFIXES = ["云", "助手", "bot", "astr"]
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


def _tokens(values: Optional[List[str]], defaults: List[str]) -> List[str]:
    if not values:
        return list(defaults)
    cleaned = [str(v).strip() for v in values if str(v).strip()]
    return cleaned or list(defaults)


def _starts_with_token(raw: str, token: str) -> bool:
    if not token or len(raw) < len(token):
        return False
    if not raw.lower().startswith(token.lower()):
        return False
    if len(raw) == len(token):
        return True
    nxt = raw[len(token)]
    return nxt.isspace() or nxt in ":：/-|"


def _strip_leading_token(raw: str, token_len: int) -> str:
    if len(raw) <= token_len:
        return ""
    rest = raw[token_len:].strip()
    if rest[:1] in ":：-|":
        return rest[1:].strip()
    return rest


def _match_leading(raw: str, tokens: List[str]) -> Optional[Tuple[str, str]]:
    best: Optional[Tuple[str, str]] = None
    for token in tokens:
        if not _starts_with_token(raw, token):
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
) -> Tuple[str, str, str]:
    """Return (owner, text_for_handler, reason) where owner is webae|astrbot."""
    text = (raw or "").strip()
    hit = _match_leading(text, webae_prefixes)
    if hit:
        return "webae", hit[1], f"explicit_webae:{hit[0]}"
    hit = _match_leading(text, astr_prefixes)
    if hit:
        return "astrbot", hit[1], f"explicit_astrbot:{hit[0]}"

    cmd = text
    prefix = (command_prefix or "").strip()
    if prefix and cmd.startswith(prefix):
        cmd = cmd[len(prefix):].strip()
    verb = _first_word(cmd)
    if verb in WEBAE_COMMAND_VERBS:
        return "webae", text, f"webae_command:{verb}"

    lower = text.lower()
    for keyword in keywords:
        if keyword and keyword.lower() in lower:
            return "webae", text, f"webae_keyword:{keyword}"
    return "astrbot", text, "default_astrbot"


@register(
    "textech_intent",
    "TeXTech",
    "TeXTech/WebAE shared-bot intent handoff",
    "1.0.0",
)
class TextechIntentPlugin(Star):
    def __init__(self, context: Context, config: dict | None = None):
        super().__init__(context)
        self.config = config or {}

    def _enabled(self) -> bool:
        return bool(self.config.get("enabled", True))

    @filter.event_message_type(filter.EventMessageType.ALL, priority=100)
    async def on_message(self, event: AstrMessageEvent):
        if not self._enabled():
            return
        raw = (event.message_str or "").strip()
        if not raw:
            return
        owner, remainder, reason = classify(
            raw,
            webae_prefixes=_tokens(self.config.get("webae_explicit_prefixes"), DEFAULT_WEBAE_PREFIXES),
            astr_prefixes=_tokens(self.config.get("astrbot_explicit_prefixes"), DEFAULT_ASTRBOT_PREFIXES),
            keywords=_tokens(self.config.get("webae_intent_keywords"), DEFAULT_WEBAE_KEYWORDS),
            command_prefix=str(self.config.get("command_prefix") or "/"),
        )
        if owner == "webae":
            logger.info(f"[textech_intent] handoff to WebAE ({reason}): {raw[:80]}")
            event.stop_event()
            event.should_call_llm(False)
            return
        if reason.startswith("explicit_astrbot:") and remainder and remainder != raw:
            # Prefer stripped text for downstream AstrBot LLM when user used an explicit prefix.
            try:
                event.message_str = remainder
                if event.message_obj is not None:
                    event.message_obj.message_str = remainder
            except Exception:
                pass
            logger.info(f"[textech_intent] AstrBot explicit prefix stripped ({reason})")
