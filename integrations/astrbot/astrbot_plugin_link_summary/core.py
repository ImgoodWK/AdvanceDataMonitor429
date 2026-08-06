"""Safe URL handling, lightweight page extraction and Bilibili helpers.

The module intentionally has no AstrBot imports.  Keeping the network and parsing
code here makes it possible to test the security boundary without booting AstrBot.
"""

from __future__ import annotations

import asyncio
import html
import ipaddress
import json
import re
import socket
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from html.parser import HTMLParser
from typing import Any, Awaitable, Callable, Iterable, Mapping
from urllib.parse import parse_qs, urlencode, urljoin, urlsplit, urlunsplit

import httpx


DEFAULT_HEADERS = {
    # A fixed, browser-compatible identifier improves the chance that public
    # content sites return their ordinary HTML instead of their bot-block page.
    # This remains deliberately stateless: no cookies, credentials, proxy
    # inheritance, referer, or user-specific headers are ever attached.
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,text/plain,application/json;q=0.9,*/*;q=0.1",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.6",
}
BILI_HEADERS = {
    **DEFAULT_HEADERS,
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.6",
}
BILI_VIDEO_HOSTS = {"www.bilibili.com", "m.bilibili.com"}
BILI_SHORT_HOSTS = {"b23.tv", "www.b23.tv"}
BILI_COVER_HOSTS = {"i0.hdslb.com", "i1.hdslb.com", "i2.hdslb.com"}
BILI_API_HOST = "api.bilibili.com"
ZHIHU_HOSTS = {"www.zhihu.com"}
ZHIHU_HEADERS = {
    **DEFAULT_HEADERS,
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.6",
}
CHINA_STANDARD_TIME = timezone(timedelta(hours=8))

# QQ messages often append Chinese sentence punctuation without a space.  Keep
# the delimiter list here as part of the match (rather than only trimming the
# final character), otherwise ``https://example.com。然后`` is captured with
# the Chinese text as part of the host/path.
_URL_RE = re.compile(r"https?://[^\s<>\"'`，。！？；：、）】》」』]+", re.IGNORECASE)
_TRAILING_URL_CHARS = ",.;:!?)]}>、，。！？；：）〉】》」』】"
_BVID_RE = re.compile(r"(?i)^BV[0-9A-Za-z]{10}$")
_BILI_TEXT_REF_RE = re.compile(
    r"(?<![0-9A-Za-z])(?:BV[0-9A-Za-z]{10}|av[0-9]{1,20})(?![0-9A-Za-z])",
    re.IGNORECASE,
)
_VIDEO_PATH_RE = re.compile(r"^/video/(BV[0-9A-Za-z]{10}|av[0-9]+)(?:/|$)", re.IGNORECASE)
_ZHIHU_ANSWER_PATH_RE = re.compile(r"^/question/[0-9]+/answer/([0-9]+)/?$")
_ZHIHU_FETCH_ATTEMPTS = 3
_ZHIHU_RETRY_DELAYS = (0.35, 1.0)
_PUBLIC_FETCH_ATTEMPTS = 2
_PUBLIC_FETCH_RETRY_DELAY = 0.35
_TRANSIENT_HTTP_STATUSES = {408, 425, 429, *range(500, 600)}
_REDIRECT_CODES = {301, 302, 303, 307, 308}
_HTML_TYPES = {"text/html", "application/xhtml+xml", "text/plain"}


class LinkSummaryError(RuntimeError):
    """Expected, user-facing failure while processing a link."""


class UnsafeURL(LinkSummaryError):
    """The URL is malformed or resolves to an unsafe network target."""


class FetchError(LinkSummaryError):
    """A remote resource could not be fetched within policy."""


class _TransientFetchError(FetchError):
    """A public GET failed in a way that is safe to retry once."""


class PageSummaryUnavailable(LinkSummaryError):
    """The page was fetched, but no LLM summary was available."""


class PageSummaryRejected(LinkSummaryError):
    """The LLM output was too close to the source to be a real summary."""


def extract_urls(text: str, *, max_urls: int = 1) -> list[str]:
    """Extract and normalize HTTP(S) URLs from a QQ message.

    Markdown/Chinese punctuation commonly follows pasted URLs.  It is removed
    conservatively while preserving query strings and encoded punctuation.
    """

    if not text or max_urls <= 0:
        return []
    result: list[str] = []
    seen: set[str] = set()
    for match in _URL_RE.finditer(text):
        value = match.group(0).rstrip(_TRAILING_URL_CHARS)
        # A closing parenthesis is part of a URL only when it is balanced.  In
        # a normal chat message it is overwhelmingly a sentence delimiter.
        while value.endswith(")") and value.count("(") < value.count(")"):
            value = value[:-1]
        if not value:
            continue
        key = value.strip()
        if key not in seen:
            seen.add(key)
            result.append(key)
        if len(result) >= max_urls:
            break
    return result


def extract_bilibili_text_refs(text: str, *, max_urls: int = 1) -> list[str]:
    """Turn standalone BV/av references in visible chat text into video URLs.

    This intentionally does not inspect JSON/XML cards or arbitrary object
    fields.  Callers use it only for Plain/replied/forwarded text after normal
    HTTP(S) extraction.  References that are already inside an HTTP(S) URL are
    skipped so query strings and canonical links keep their original form.
    """

    if not text or max_urls <= 0:
        return []
    url_spans = tuple(match.span() for match in _URL_RE.finditer(text))
    result: list[str] = []
    seen: set[str] = set()
    for match in _BILI_TEXT_REF_RE.finditer(text):
        start, end = match.span()
        if any(url_start <= start and end <= url_end for url_start, url_end in url_spans):
            continue
        value = match.group(0)
        if value[:2].casefold() == "bv":
            value = "BV" + value[2:]
        else:
            value = "av" + value[2:]
        url = f"https://www.bilibili.com/video/{value}"
        if url in seen:
            continue
        seen.add(url)
        result.append(url)
        if len(result) >= max_urls:
            break
    return result


def _normalized_hostname(hostname: str | None) -> str:
    if not hostname:
        raise UnsafeURL("链接缺少主机名")
    try:
        # IDNA conversion rejects malformed labels and gives stable matching.
        return hostname.rstrip(".").encode("idna").decode("ascii").lower()
    except (UnicodeError, ValueError) as exc:
        raise UnsafeURL("链接主机名无效") from exc


def _is_bad_ip(address: ipaddress.IPv4Address | ipaddress.IPv6Address) -> bool:
    # is_global is deliberately included: documentation/reserved ranges and
    # future special-use ranges must not become an SSRF bypass by omission.
    mapped = address.ipv4_mapped if isinstance(address, ipaddress.IPv6Address) else None
    return bool(
        (mapped is not None and _is_bad_ip(mapped))
        or
        address.is_loopback
        or address.is_private
        or address.is_link_local
        or address.is_multicast
        or address.is_unspecified
        or address.is_reserved
        or not address.is_global
    )


def validate_url_syntax(raw_url: str, *, allowed_hosts: set[str] | None = None) -> str:
    """Validate URL syntax and return a normalized URL.

    DNS validation is intentionally separate so unit tests and callers that have
    already pinned an address can inject their own resolver.  ``allowed_hosts``
    is an exact allow-list, never a suffix match.
    """

    if not isinstance(raw_url, str) or len(raw_url) > 8192:
        raise UnsafeURL("链接格式不受支持")
    try:
        parsed = urlsplit(raw_url.strip())
    except ValueError as exc:
        raise UnsafeURL("链接格式不受支持") from exc
    scheme = parsed.scheme.lower()
    if scheme not in {"http", "https"}:
        raise UnsafeURL("仅支持 http/https 链接")
    if parsed.username is not None or parsed.password is not None or "@" in parsed.netloc:
        raise UnsafeURL("不支持带账号信息的链接")
    try:
        port = parsed.port
    except ValueError as exc:
        raise UnsafeURL("链接端口无效") from exc
    if port not in (None, 80, 443):
        raise UnsafeURL("不支持非标准端口")
    hostname = _normalized_hostname(parsed.hostname)
    if allowed_hosts is not None and hostname not in {_normalized_hostname(x) for x in allowed_hosts}:
        raise UnsafeURL("链接目标不受支持")
    # Reject unbracketed/odd numeric forms before DNS.  Standard dotted IPs are
    # checked directly; other numeric forms are left to getaddrinfo and then
    # checked against every returned address.
    try:
        literal = ipaddress.ip_address(hostname)
    except ValueError:
        literal = None
    if literal is not None and _is_bad_ip(literal):
        raise UnsafeURL("链接目标不安全")
    normalized_netloc = hostname
    if ":" in hostname:
        normalized_netloc = f"[{hostname}]"
    if port is not None:
        normalized_netloc = f"{normalized_netloc}:{port}"
    return urlunsplit((scheme, normalized_netloc, parsed.path or "/", parsed.query, ""))


async def validate_url_network(
    url: str,
    *,
    resolver: Callable[[str, int], Awaitable[Iterable[str]]] | None = None,
) -> None:
    """Resolve a URL host and reject private, local or special-use addresses."""

    parsed = urlsplit(url)
    hostname = _normalized_hostname(parsed.hostname)
    try:
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
    except ValueError as exc:
        raise UnsafeURL("链接端口无效") from exc
    if resolver is None:
        loop = asyncio.get_running_loop()

        async def resolver(host: str, target_port: int) -> Iterable[str]:
            infos = await loop.run_in_executor(
                None,
                lambda: socket.getaddrinfo(host, target_port, type=socket.SOCK_STREAM),
            )
            return {str(item[4][0]) for item in infos}

    try:
        addresses = list(await resolver(hostname, port))
    except (OSError, socket.gaierror) as exc:
        raise UnsafeURL("链接目标无法解析") from exc
    if not addresses:
        raise UnsafeURL("链接目标无法解析")
    for raw_address in addresses:
        try:
            address = ipaddress.ip_address(raw_address)
        except ValueError as exc:
            raise UnsafeURL("链接目标解析异常") from exc
        if _is_bad_ip(address):
            raise UnsafeURL("链接目标不安全")


@dataclass(slots=True)
class FetchResult:
    url: str
    status_code: int
    headers: Mapping[str, str]
    content: bytes


class SafeHttpFetcher:
    """Small, bounded HTTP client with manual, revalidated redirects."""

    def __init__(
        self,
        *,
        timeout_seconds: float = 12,
        max_redirects: int = 3,
        max_bytes: int = 1024 * 1024,
        resolver: Callable[[str, int], Awaitable[Iterable[str]]] | None = None,
        client_factory: Callable[..., httpx.AsyncClient] | None = None,
    ) -> None:
        self.timeout_seconds = max(1.0, float(timeout_seconds))
        self.max_redirects = max(0, int(max_redirects))
        self.max_bytes = max(16 * 1024, int(max_bytes))
        self.resolver = resolver
        self.client_factory = client_factory or httpx.AsyncClient

    def _client(self, headers: Mapping[str, str] | None = None) -> httpx.AsyncClient:
        merged = dict(DEFAULT_HEADERS)
        if headers:
            merged.update(headers)
        return self.client_factory(
            headers=merged,
            follow_redirects=False,
            trust_env=False,
            timeout=httpx.Timeout(self.timeout_seconds),
        )

    @staticmethod
    def _clear_cookies(client: Any) -> None:
        """Do not replay cookies supplied by one response on a later hop."""

        cookies = getattr(client, "cookies", None)
        clear = getattr(cookies, "clear", None)
        if callable(clear):
            clear()

    async def _validate(self, url: str, *, allowed_hosts: set[str] | None = None) -> str:
        normalized = validate_url_syntax(url, allowed_hosts=allowed_hosts)
        await validate_url_network(normalized, resolver=self.resolver)
        return normalized

    async def resolve(self, url: str, *, allowed_hosts: set[str] | None = None) -> str:
        """Resolve redirects without downloading the final response body."""

        last_error: _TransientFetchError | None = None
        for attempt in range(_PUBLIC_FETCH_ATTEMPTS):
            try:
                return await self._resolve_once(url, allowed_hosts=allowed_hosts)
            except _TransientFetchError as exc:
                last_error = exc
                if attempt + 1 >= _PUBLIC_FETCH_ATTEMPTS:
                    raise
                await asyncio.sleep(_PUBLIC_FETCH_RETRY_DELAY)
        raise last_error or FetchError("链接请求失败")

    async def _resolve_once(
        self,
        url: str,
        *,
        allowed_hosts: set[str] | None = None,
    ) -> str:
        """Perform one stateless, fully revalidated redirect resolution."""

        current = await self._validate(url, allowed_hosts=allowed_hosts)
        async with self._client() as client:
            for redirect_count in range(self.max_redirects + 1):
                self._clear_cookies(client)
                try:
                    async with client.stream("GET", current) as response:
                        if response.status_code not in _REDIRECT_CODES:
                            if response.status_code in _TRANSIENT_HTTP_STATUSES:
                                raise _TransientFetchError(
                                    f"远程链接返回 HTTP {response.status_code}"
                                )
                            if response.status_code >= 400:
                                raise FetchError(f"远程链接返回 HTTP {response.status_code}")
                            return str(response.url)
                        location = response.headers.get("location")
                except httpx.TransportError as exc:
                    raise _TransientFetchError("链接请求超时或网络不可用") from exc
                except httpx.HTTPError as exc:
                    raise FetchError("链接请求失败") from exc
                if not location:
                    raise FetchError("链接重定向缺少目标")
                if redirect_count >= self.max_redirects:
                    raise FetchError("链接重定向次数过多")
                current = await self._validate(urljoin(current, location), allowed_hosts=allowed_hosts)
        raise FetchError("链接重定向次数过多")

    async def fetch(
        self,
        url: str,
        *,
        allowed_hosts: set[str] | None = None,
        headers: Mapping[str, str] | None = None,
        accept_types: set[str] | None = None,
        max_bytes: int | None = None,
    ) -> FetchResult:
        """Fetch one public resource, retrying one transient stateless GET."""

        last_error: _TransientFetchError | None = None
        for attempt in range(_PUBLIC_FETCH_ATTEMPTS):
            try:
                return await self._fetch_once(
                    url,
                    allowed_hosts=allowed_hosts,
                    headers=headers,
                    accept_types=accept_types,
                    max_bytes=max_bytes,
                )
            except _TransientFetchError as exc:
                last_error = exc
                if attempt + 1 >= _PUBLIC_FETCH_ATTEMPTS:
                    raise
                await asyncio.sleep(_PUBLIC_FETCH_RETRY_DELAY)
        raise last_error or FetchError("链接请求失败")

    async def _fetch_once(
        self,
        url: str,
        *,
        allowed_hosts: set[str] | None = None,
        headers: Mapping[str, str] | None = None,
        accept_types: set[str] | None = None,
        max_bytes: int | None = None,
    ) -> FetchResult:
        """Perform one stateless fetch with every redirect revalidated."""

        current = await self._validate(url, allowed_hosts=allowed_hosts)
        requested_limit = self.max_bytes if max_bytes is None else max(16 * 1024, int(max_bytes))
        byte_limit = min(self.max_bytes, requested_limit)
        async with self._client(headers) as client:
            for redirect_count in range(self.max_redirects + 1):
                self._clear_cookies(client)
                try:
                    async with client.stream("GET", current) as response:
                        if response.status_code in _REDIRECT_CODES:
                            location = response.headers.get("location")
                            if not location:
                                raise FetchError("链接重定向缺少目标")
                            if redirect_count >= self.max_redirects:
                                raise FetchError("链接重定向次数过多")
                            current = await self._validate(urljoin(current, location), allowed_hosts=allowed_hosts)
                            continue
                        if response.status_code in _TRANSIENT_HTTP_STATUSES:
                            raise _TransientFetchError(
                                f"远程链接返回 HTTP {response.status_code}"
                            )
                        if response.status_code >= 400:
                            raise FetchError(f"远程链接返回 HTTP {response.status_code}")
                        content_type = response.headers.get("content-type", "").split(";", 1)[0].strip().lower()
                        accepted = accept_types or _HTML_TYPES
                        if not content_type or content_type not in accepted:
                            raise FetchError("暂不支持该链接类型")
                        declared = response.headers.get("content-length")
                        if declared and declared.isdigit() and int(declared) > byte_limit:
                            raise FetchError("链接内容过大")
                        chunks: list[bytes] = []
                        total = 0
                        async for chunk in response.aiter_bytes():
                            total += len(chunk)
                            if total > byte_limit:
                                raise FetchError("链接内容过大")
                            chunks.append(chunk)
                        return FetchResult(str(response.url), response.status_code, dict(response.headers), b"".join(chunks))
                except httpx.TransportError as exc:
                    raise _TransientFetchError("链接请求超时或网络不可用") from exc
                except httpx.HTTPError as exc:
                    raise FetchError("链接请求失败") from exc
        raise FetchError("链接重定向次数过多")

    async def json(
        self,
        url: str,
        *,
        headers: Mapping[str, str] | None = None,
        allowed_hosts: set[str] | None = None,
    ) -> dict[str, Any]:
        result = await self.fetch(
            url,
            headers=headers,
            allowed_hosts=allowed_hosts,
            accept_types={"application/json", "text/json", "text/plain"},
            max_bytes=512 * 1024,
        )
        try:
            value = json.loads(result.content.decode("utf-8", "replace"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise FetchError("接口返回内容无效") from exc
        if not isinstance(value, dict):
            raise FetchError("接口返回内容无效")
        return value


@dataclass(slots=True)
class PageData:
    url: str
    title: str = ""
    description: str = ""
    text: str = ""
    truncated: bool = False


class _PageParser(HTMLParser):
    # HTMLParser does not synthesize end tags for HTML void elements.  Keeping
    # one of these on the stack (especially a skipped <input> or <embed>) would
    # incorrectly suppress every readable node that follows it.
    _VOID_TAGS = {
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "param",
        "source",
        "track",
        "wbr",
    }
    _SKIP_TAGS = {
        "script",
        "style",
        "nav",
        "header",
        "footer",
        "aside",
        "form",
        "template",
        "noscript",
        "svg",
        "canvas",
        "iframe",
        "object",
        "embed",
        "audio",
        "video",
        "button",
        "input",
        "select",
        "textarea",
        "dialog",
        "menu",
    }
    _BREAK_TAGS = {
        "p",
        "div",
        "article",
        "main",
        "section",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "li",
        "blockquote",
        "pre",
        "br",
        "tr",
        "td",
        "th",
        "dt",
        "dd",
    }
    _NOISE_ROLES = {"navigation", "banner", "contentinfo", "complementary", "dialog"}
    _NOISE_TOKENS = {
        "ad",
        "ads",
        "advert",
        "advertisement",
        "breadcrumb",
        "comment",
        "comments",
        "cookie",
        "login",
        "menu",
        "modal",
        "pagination",
        "popup",
        "recommend",
        "recommendation",
        "recommendations",
        "related",
        "share",
        "sidebar",
        "toolbar",
    }

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title_parts: list[str] = []
        self.meta: dict[str, str] = {}
        self.body_parts: list[str] = []
        self.article_parts: list[str] = []
        self._stack: list[tuple[str, bool, bool]] = []
        self._skip_depth = 0
        self._article_depth = 0

    @classmethod
    def _should_skip(cls, tag: str, values: Mapping[str, str]) -> bool:
        if tag in cls._SKIP_TAGS:
            return True
        if "hidden" in values or values.get("aria-hidden", "").strip().lower() == "true":
            return True
        style = values.get("style", "")
        if re.search(r"(?:^|;)\s*(?:display\s*:\s*none|visibility\s*:\s*hidden)(?:\s*!important)?\s*(?:;|$)", style, re.I):
            return True
        role = values.get("role", "").strip().lower()
        if role in cls._NOISE_ROLES:
            return True
        class_id = f"{values.get('class', '')} {values.get('id', '')}".lower()
        tokens = set(re.findall(r"[a-z0-9]+", class_id))
        return bool(tokens & cls._NOISE_TOKENS)

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        values = {str(key).lower(): (value or "") for key, value in attrs}
        inherited_skip = self._skip_depth > 0
        skip_here = self._should_skip(tag, values)
        if tag == "meta" and not inherited_skip and not skip_here:
            key = (values.get("name") or values.get("property") or "").lower()
            content = values.get("content", "").strip()
            if key and content:
                self.meta[key] = html.unescape(content)
        if tag in self._VOID_TAGS:
            if tag in self._BREAK_TAGS and not inherited_skip and not skip_here:
                self.body_parts.append("\n")
                if self._article_depth:
                    self.article_parts.append("\n")
            return
        article_here = bool(
            not inherited_skip
            and not skip_here
            and (tag in {"article", "main"} or values.get("role", "").strip().lower() == "main")
        )
        self._stack.append((tag, skip_here, article_here))
        if skip_here:
            self._skip_depth += 1
        if article_here:
            self._article_depth += 1
        if tag in self._BREAK_TAGS and self._skip_depth == 0:
            self.body_parts.append("\n")
            if self._article_depth:
                self.article_parts.append("\n")

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        match_index: int | None = None
        for index in range(len(self._stack) - 1, -1, -1):
            if self._stack[index][0] == tag:
                match_index = index
                break
        if match_index is None:
            return
        popped = self._stack[match_index:]
        del self._stack[match_index:]
        for _popped_tag, skipped, article in reversed(popped):
            if article and self._article_depth:
                self._article_depth -= 1
            if skipped and self._skip_depth:
                self._skip_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._skip_depth:
            return
        value = re.sub(r"\s+", " ", data).strip()
        if not value:
            return
        if any(tag == "title" for tag, _skipped, _article in self._stack):
            self.title_parts.append(value)
            return
        self.body_parts.append(value)
        if self._article_depth:
            self.article_parts.append(value)


def _clean_text(value: str, *, limit: int = 12000) -> str:
    value = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]", "", value)
    value = re.sub(r"[ \t\r\f\v]+", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    value = re.sub(r" *\n *", "\n", value).strip()
    return value[:limit].rstrip()


def _summary_fingerprint(value: str) -> str:
    """Normalize prose for content-overlap checks without retaining it."""

    normalized = unicodedata.normalize("NFKC", str(value or "")).casefold()
    return "".join(char for char in normalized if char.isalnum())


def _sample_shingles(value: str, *, width: int = 18, limit: int = 64) -> tuple[str, ...]:
    """Return evenly distributed, de-duplicated character shingles."""

    if width <= 0 or len(value) < width or limit <= 0:
        return ()
    last_start = len(value) - width
    sample_count = min(limit, last_start + 1)
    if sample_count == 1:
        positions = (0,)
    else:
        positions = tuple(
            round(index * last_start / (sample_count - 1))
            for index in range(sample_count)
        )
    return tuple(dict.fromkeys(value[position : position + width] for position in positions))


def page_summary_rejection_reason(summary: str, source: str) -> str | None:
    """Classify page output that is empty or insufficiently distilled.

    Only normalized in-memory fingerprints are compared.  Callers should log
    the returned category, never either input.  Conservative length thresholds
    keep ordinary short summaries from being rejected merely because they must
    reuse a title or a factual phrase from the page.
    """

    summary_value = _summary_fingerprint(summary)
    source_value = _summary_fingerprint(source)
    if not summary_value:
        return "empty"
    if not source_value:
        return None
    if len(summary_value) >= 80 and summary_value in source_value:
        return "verbatim"
    if len(summary_value) >= 140:
        shingles = _sample_shingles(summary_value)
        if shingles:
            match_ratio = sum(item in source_value for item in shingles) / len(shingles)
            if match_ratio >= 0.72:
                return "near_copy"
    if (
        len(source_value) >= 800
        and len(summary_value) >= 480
        and len(summary_value) / len(source_value) > 0.55
    ):
        return "insufficient_compression"
    return None


def _clean_readable_blocks(parts: Iterable[str], *, limit: int) -> str:
    """Normalize, de-duplicate and bound extracted human-readable blocks."""

    working_limit = max(limit, min(200_000, limit * 4))
    value = _clean_text(" ".join(parts), limit=working_limit)
    result: list[str] = []
    seen_long_blocks: set[str] = set()
    for raw_block in value.splitlines():
        block = re.sub(r"https?://\S{80,}", "[链接]", raw_block, flags=re.I)
        block = re.sub(r"\s+", " ", block).strip()
        if not block or re.fullmatch(r"https?://\S+", block, re.I):
            continue
        fingerprint = block.casefold()
        if len(fingerprint) >= 24:
            if fingerprint in seen_long_blocks:
                continue
            seen_long_blocks.add(fingerprint)
        result.append(block)
    return _clean_text("\n".join(result), limit=limit)


def extract_page(url: str, content: bytes, *, content_type: str = "", limit: int = 10000) -> PageData:
    """Extract title/description and readable text without executing HTML."""

    charset = "utf-8"
    match = re.search(r"charset\s*=\s*[\"']?([a-zA-Z0-9._-]+)", content_type, re.IGNORECASE)
    if match:
        charset = match.group(1)
    try:
        source = content.decode(charset, "replace")
    except LookupError:
        source = content.decode("utf-8", "replace")
    parser = _PageParser()
    try:
        parser.feed(source)
        parser.close()
    except Exception:  # HTMLParser should be forgiving; keep partial text on malformed pages.
        pass
    title = _clean_text(" ".join(parser.title_parts), limit=500)
    title = parser.meta.get("og:title") or title
    description = parser.meta.get("description") or parser.meta.get("og:description", "")
    article_text = _clean_readable_blocks(parser.article_parts, limit=limit)
    body_text = _clean_readable_blocks(parser.body_parts, limit=limit)
    # A tiny <main> is frequently only a heading or application shell.  Keep
    # the semantic candidate when it is substantial; otherwise use the fully
    # cleaned body so short articles do not suppress the actual page text.
    if article_text and (len(article_text) >= 200 or len(body_text) <= len(article_text)):
        body = article_text
    else:
        body = body_text
    # JSON-LD is useful for pages whose visible content is mostly navigation.
    for raw in re.findall(r"<script[^>]+type=[\"']application/ld\+json[\"'][^>]*>(.*?)</script>", source, re.I | re.S):
        try:
            data = json.loads(html.unescape(raw.strip()))
        except (ValueError, TypeError):
            continue
        values = data if isinstance(data, list) else [data]
        for item in values:
            if not isinstance(item, dict):
                continue
            title = title or str(item.get("headline") or item.get("name") or "")
            description = description or str(item.get("description") or "")
            article_body = item.get("articleBody")
            if isinstance(article_body, str) and len(article_body) > len(body):
                body = _clean_readable_blocks([article_body], limit=limit)
    return PageData(url=url, title=_clean_text(title, limit=500), description=_clean_text(description, limit=1500), text=body)


def compact_page_data(page: PageData, *, limit: int = 8000) -> PageData:
    """Fit title, description and body into one bounded LLM content budget."""

    total_limit = max(1, int(limit))
    remaining = total_limit
    title = _clean_text(page.title, limit=min(500, remaining)) if remaining else ""
    remaining -= len(title)
    description_cap = min(1200, max(200, total_limit // 5), remaining)
    description = _clean_text(page.description, limit=description_cap) if description_cap else ""
    remaining -= len(description)
    text = _clean_text(page.text, limit=remaining) if remaining else ""
    return PageData(
        url=page.url,
        title=title,
        description=description,
        text=text,
        truncated=page.truncated or len(page.title) + len(page.description) + len(page.text) > total_limit,
    )


def parse_zhihu_answer_url(url: str) -> str | None:
    """Return an answer id for an exact public Zhihu answer-page URL."""

    try:
        normalized = validate_url_syntax(url, allowed_hosts=ZHIHU_HOSTS)
        path = urlsplit(normalized).path or ""
    except (UnsafeURL, ValueError):
        return None
    match = _ZHIHU_ANSWER_PATH_RE.fullmatch(path)
    return match.group(1) if match else None


class ZhihuClient:
    """Fetch only the public answer fields needed by the page summarizer."""

    def __init__(self, fetcher: SafeHttpFetcher) -> None:
        self.fetcher = fetcher

    async def fetch_answer(self, url: str, *, text_limit: int = 8000) -> PageData:
        normalized = validate_url_syntax(url, allowed_hosts=ZHIHU_HOSTS)
        answer_id = parse_zhihu_answer_url(normalized)
        if answer_id is None:
            raise LinkSummaryError("不是受支持的知乎回答链接")
        include = "content,excerpt,question.title,content_need_truncated"
        endpoint = f"https://www.zhihu.com/api/v4/answers/{answer_id}?{urlencode({'include': include})}"
        payload: dict[str, Any] | None = None
        for attempt in range(_ZHIHU_FETCH_ATTEMPTS):
            try:
                payload = await self.fetcher.json(
                    endpoint,
                    headers=ZHIHU_HEADERS,
                    allowed_hosts=ZHIHU_HOSTS,
                )
                break
            except FetchError:
                # The public endpoint occasionally produces a transient
                # network/edge failure.  A short, increasing backoff keeps
                # this credential-free GET bounded without changing host
                # policy or falling back to the 403-prone browser page.
                if attempt + 1 >= _ZHIHU_FETCH_ATTEMPTS:
                    raise
                await asyncio.sleep(_ZHIHU_RETRY_DELAYS[attempt])
        if payload is None:  # Defensive guard for custom fetcher implementations.
            raise FetchError("知乎公开正文请求失败")

        # Deliberately do not read author, member, avatar, id or other identity
        # fields that may coexist in the public response.
        content = payload.get("content") if isinstance(payload.get("content"), str) else ""
        excerpt = payload.get("excerpt") if isinstance(payload.get("excerpt"), str) else ""
        question = payload.get("question") if isinstance(payload.get("question"), dict) else {}
        question_title = question.get("title") if isinstance(question.get("title"), str) else ""
        page = extract_page(
            normalized,
            content.encode("utf-8"),
            content_type="text/html; charset=utf-8",
            limit=max(1, int(text_limit)),
        )
        page.title = _clean_text(question_title or page.title, limit=500)
        page.description = _clean_text(excerpt or page.description, limit=1500)
        page.truncated = bool(payload.get("content_need_truncated"))
        if not page.text and not page.description:
            raise FetchError("知乎回答没有可总结的公开正文")
        return page


@dataclass(slots=True)
class BiliComment:
    message: str
    likes: int = 0


@dataclass(slots=True)
class BiliVideo:
    url: str
    bvid: str = ""
    aid: int | None = None
    cover_url: str = ""
    title: str = ""
    description: str = ""
    published_at: str = ""
    owner: str = ""
    duration: int | None = None
    stats: dict[str, int] = field(default_factory=dict)
    comments: list[BiliComment] = field(default_factory=list)
    comments_error: str = ""


def parse_bilibili_video_url(url: str) -> tuple[str, str] | None:
    """Return (kind, value), where kind is ``bvid`` or ``aid``."""

    try:
        parsed = urlsplit(url)
        host = _normalized_hostname(parsed.hostname)
    except (UnsafeURL, ValueError):
        return None
    if host not in BILI_VIDEO_HOSTS:
        return None
    match = _VIDEO_PATH_RE.match(parsed.path or "")
    if not match:
        return None
    value = match.group(1)
    if value.lower().startswith("bv") and _BVID_RE.fullmatch(value):
        # BVID's ``BV`` prefix is conventionally uppercase while the ten
        # payload characters are case-sensitive.  Normalize only the prefix so
        # a user-pasted ``bv...`` URL still reaches the API correctly.
        return "bvid", "BV" + value[2:]
    if value.lower().startswith("av") and value[2:].isdigit():
        return "aid", value[2:]
    return None


def _safe_cover_url(value: Any) -> str:
    if not isinstance(value, str) or not value:
        return ""
    try:
        parsed = urlsplit(value if value.startswith(("http://", "https://")) else f"https:{value}")
        host = _normalized_hostname(parsed.hostname)
    except (UnsafeURL, ValueError):
        return ""
    if parsed.scheme not in {"http", "https"} or host not in BILI_COVER_HOSTS:
        return ""
    return urlunsplit(("https", host, parsed.path or "/", parsed.query, ""))


class BilibiliClient:
    def __init__(self, fetcher: SafeHttpFetcher) -> None:
        self.fetcher = fetcher

    async def fetch_video(self, url: str, *, max_comments: int = 15) -> BiliVideo:
        parsed = parse_bilibili_video_url(url)
        if not parsed:
            raise LinkSummaryError("不是 B 站视频链接")
        kind, value = parsed
        params = {kind: value}
        endpoint = f"https://{BILI_API_HOST}/x/web-interface/view?{urlencode(params)}"
        data = await self.fetcher.json(
            endpoint,
            headers={**BILI_HEADERS, "Referer": url},
            allowed_hosts={BILI_API_HOST},
        )
        if data.get("code") != 0 or not isinstance(data.get("data"), dict):
            raise FetchError("B 站视频详情暂时不可用")
        payload = data["data"]
        stat = payload.get("stat") if isinstance(payload.get("stat"), dict) else {}
        owner = payload.get("owner") if isinstance(payload.get("owner"), dict) else {}
        pubdate = payload.get("pubdate")
        published = ""
        if isinstance(pubdate, (int, float)) and pubdate > 0:
            published = datetime.fromtimestamp(pubdate, timezone.utc).astimezone(CHINA_STANDARD_TIME).strftime("%Y-%m-%d %H:%M")
        video = BiliVideo(
            url=url,
            bvid=str(payload.get("bvid") or (value if kind == "bvid" else "")),
            aid=int(payload["aid"]) if str(payload.get("aid", "")).isdigit() else None,
            cover_url=_safe_cover_url(payload.get("pic")),
            title=_clean_text(str(payload.get("title") or "未知标题"), limit=500),
            description=_clean_text(str(payload.get("desc") or ""), limit=2000),
            published_at=published or "未知",
            owner=_clean_text(str(owner.get("name") or "未知"), limit=100),
            duration=int(payload["duration"]) if str(payload.get("duration", "")).isdigit() else None,
            stats={key: int(value) for key, value in stat.items() if key in {"view", "reply", "like", "favorite", "coin", "share", "danmaku"} and str(value).isdigit()},
        )
        if video.aid is None:
            video.comments_error = "缺少视频 aid"
            return video
        comment_limit = max(0, int(max_comments))
        if comment_limit == 0:
            return video
        try:
            comment_params = {"next": "0", "type": "1", "oid": str(video.aid), "mode": "3", "plat": "1", "web_location": "1315875"}
            comments_url = f"https://{BILI_API_HOST}/x/v2/reply/main?{urlencode(comment_params)}"
            comment_data = await self.fetcher.json(
                comments_url,
                headers={**BILI_HEADERS, "Referer": f"https://www.bilibili.com/video/{video.bvid}"},
                allowed_hosts={BILI_API_HOST},
            )
            if comment_data.get("code") != 0:
                raise FetchError("B 站热评接口返回错误")
            reply_data = comment_data.get("data")
            replies = reply_data.get("replies") if isinstance(reply_data, dict) else None
            if not isinstance(replies, list):
                raise FetchError("B 站热评数据为空")
            seen_messages: set[str] = set()
            for item in replies:
                if not isinstance(item, dict):
                    continue
                content = item.get("content") if isinstance(item.get("content"), dict) else {}
                message = _clean_text(str(content.get("message") or ""), limit=500)
                if not message:
                    continue
                message_key = message.casefold()
                if message_key in seen_messages:
                    continue
                seen_messages.add(message_key)
                likes = int(item.get("like")) if str(item.get("like", "")).isdigit() else 0
                video.comments.append(BiliComment(message=message, likes=likes))
                if len(video.comments) >= comment_limit:
                    break
        except Exception as exc:  # Comments are an optional enrichment.
            video.comments_error = str(exc)[:120]
        return video


def format_count(value: int | None) -> str:
    if value is None:
        return "未知"
    if value >= 100_000_000:
        return f"{value / 100_000_000:.1f}亿"
    if value >= 10_000:
        return f"{value / 10_000:.1f}万"
    return f"{value:,}"


def format_duration(value: int | None) -> str:
    if value is None or value < 0:
        return "未知"
    return f"{value // 60}:{value % 60:02d}"


def fallback_bili_comment_digest(comments: list[BiliComment], *, limit: int = 900) -> str:
    if not comments:
        return "网友怎么说：热评暂不可用。"
    snippets = [f"{item.message}（赞 {item.likes:,}）" for item in comments[:3]]
    return _clean_text("网友怎么说：\n" + "\n".join(f"- {item}" for item in snippets), limit=limit)


def fallback_page_summary(page: PageData, *, limit: int = 1200) -> str:
    pieces = [piece for piece in (page.description, page.text) if piece]
    if not pieces:
        return "未能提取到可总结的正文。"
    text = "。".join(pieces)
    sentences = re.split(r"(?<=[。！？!?])\s*", text)
    selected = "。".join(sentence.strip("。 ") for sentence in sentences if sentence.strip())
    return _clean_text(selected or text, limit=limit)
