"""Multi-engine web search aligned with TeXTech WebSearchService (pre-search inject)."""

from __future__ import annotations

import html
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence
from urllib.parse import parse_qs, unquote, urlencode, urlparse

import httpx

AUTO_CHAIN = (
    "tavily_keyless",
    "duckduckgo",
    "tavily",
    "brave",
    "serper",
    "searxng",
)

DDG_LINK_RE = re.compile(
    r'class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>',
    re.IGNORECASE | re.DOTALL,
)
DDG_SNIPPET_RE = re.compile(
    r'class="result__snippet"[^>]*>(.*?)</(?:a|td)>',
    re.IGNORECASE | re.DOTALL,
)
HTML_TAG_RE = re.compile(r"<[^>]+>")
USER_AGENT = "Mozilla/5.0 (compatible; TeXTech-AstrBot-WebSearch/1.0)"


@dataclass
class SearchHit:
    title: str
    url: str
    snippet: str


@dataclass
class SearchData:
    provider: str
    results: List[SearchHit]
    context: str


def normalize_mode(mode: Optional[str]) -> str:
    value = (mode or "auto").strip().lower()
    if value in AUTO_CHAIN or value == "auto":
        return value
    return "auto"


def build_chain(mode: str, fallback: bool) -> List[str]:
    configured = normalize_mode(mode)
    if configured == "auto":
        return list(AUTO_CHAIN)
    chain = [configured]
    if fallback:
        for provider in AUTO_CHAIN:
            if provider != configured:
                chain.append(provider)
    return chain


def format_context(results: Sequence[SearchHit]) -> str:
    lines = ["以下是与用户问题相关的网页检索结果，请优先依据这些内容回答，并在回答中引用编号来源："]
    for index, item in enumerate(results, start=1):
        title = item.title or item.url or f"结果 {index}"
        lines.append(f"\n[{index}] {title}")
        if item.url:
            lines.append(f"链接: {item.url}")
        if item.snippet:
            lines.append(f"摘要: {item.snippet}")
    return "\n".join(lines)


def inject_user_text(user_text: str, data: Optional[SearchData]) -> str:
    if data is None or not data.context:
        return user_text or ""
    return f"{data.context}\n\n---\n\n用户问题：\n{user_text or ''}"


async def perform_web_search(
    query: str,
    *,
    mode: str = "auto",
    api_key: str = "",
    base_url: str = "",
    max_results: int = 5,
    fallback: bool = True,
    timeout_seconds: int = 12,
) -> SearchData:
    q = (query or "").strip()
    if not q:
        raise ValueError("empty query")
    limit = max(1, min(10, int(max_results or 5)))
    timeout = max(5, int(timeout_seconds or 12))
    last_error: Optional[Exception] = None
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True, headers={"User-Agent": USER_AGENT}) as client:
        for provider in build_chain(mode, fallback):
            if not _available(provider, api_key, base_url):
                continue
            try:
                results = await _search_provider(client, provider, q, limit, api_key, base_url)
                if not results:
                    raise RuntimeError(f"{provider} returned no results")
                return SearchData(provider=provider, results=results, context=format_context(results))
            except Exception as exc:  # noqa: BLE001 — soft failover like WebAE
                last_error = exc
                if not fallback:
                    break
    if last_error:
        raise last_error
    raise RuntimeError("All search providers failed or unavailable")


def _available(provider: str, api_key: str, base_url: str) -> bool:
    if provider in ("tavily_keyless", "duckduckgo"):
        return True
    if provider in ("tavily", "brave", "serper"):
        return bool((api_key or "").strip())
    if provider == "searxng":
        return bool((base_url or "").strip())
    return False


async def _search_provider(
    client: httpx.AsyncClient,
    provider: str,
    query: str,
    max_results: int,
    api_key: str,
    base_url: str,
) -> List[SearchHit]:
    if provider == "tavily_keyless":
        return await _tavily(client, query, max_results, api_key="", keyless=True)
    if provider == "tavily":
        return await _tavily(client, query, max_results, api_key=api_key, keyless=False)
    if provider == "duckduckgo":
        return await _duckduckgo(client, query, max_results)
    if provider == "brave":
        return await _brave(client, query, max_results, api_key)
    if provider == "serper":
        return await _serper(client, query, max_results, api_key)
    if provider == "searxng":
        return await _searxng(client, query, max_results, base_url)
    raise RuntimeError(f"unknown provider: {provider}")


async def _tavily(
    client: httpx.AsyncClient,
    query: str,
    max_results: int,
    *,
    api_key: str,
    keyless: bool,
) -> List[SearchHit]:
    body: Dict[str, Any] = {"query": query, "max_results": max_results}
    headers = {"Content-Type": "application/json"}
    if keyless:
        headers["X-Tavily-Access-Mode"] = "keyless"
    else:
        body["api_key"] = api_key
    resp = await client.post("https://api.tavily.com/search", json=body, headers=headers)
    resp.raise_for_status()
    items = resp.json().get("results") or []
    return [
        SearchHit(
            title=str(item.get("title") or ""),
            url=str(item.get("url") or ""),
            snippet=str(item.get("content") or ""),
        )
        for item in items[:max_results]
    ]


async def _duckduckgo(client: httpx.AsyncClient, query: str, max_results: int) -> List[SearchHit]:
    resp = await client.post(
        "https://html.duckduckgo.com/html/",
        content=urlencode({"q": query, "b": "", "kl": "", "df": ""}),
        headers={"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
    )
    resp.raise_for_status()
    page = resp.text
    urls: List[str] = []
    titles: List[str] = []
    for match in DDG_LINK_RE.finditer(page):
        if len(urls) >= max_results:
            break
        urls.append(_decode_ddg_url(match.group(1)))
        titles.append(_strip_html(match.group(2)))
    snippets = [_strip_html(m.group(1)) for m in DDG_SNIPPET_RE.finditer(page)][:max_results]
    results: List[SearchHit] = []
    for i, url in enumerate(urls):
        results.append(
            SearchHit(
                title=titles[i] if i < len(titles) else url,
                url=url,
                snippet=snippets[i] if i < len(snippets) else "",
            )
        )
    return results


async def _brave(client: httpx.AsyncClient, query: str, max_results: int, api_key: str) -> List[SearchHit]:
    resp = await client.get(
        "https://api.search.brave.com/res/v1/web/search",
        params={"q": query, "count": max_results},
        headers={"Accept": "application/json", "X-Subscription-Token": api_key},
    )
    resp.raise_for_status()
    items = ((resp.json().get("web") or {}).get("results")) or []
    return [
        SearchHit(
            title=str(item.get("title") or ""),
            url=str(item.get("url") or ""),
            snippet=str(item.get("description") or ""),
        )
        for item in items[:max_results]
    ]


async def _serper(client: httpx.AsyncClient, query: str, max_results: int, api_key: str) -> List[SearchHit]:
    resp = await client.post(
        "https://google.serper.dev/search",
        json={"q": query, "num": max_results},
        headers={"Content-Type": "application/json", "X-API-KEY": api_key},
    )
    resp.raise_for_status()
    items = resp.json().get("organic") or []
    return [
        SearchHit(
            title=str(item.get("title") or ""),
            url=str(item.get("link") or ""),
            snippet=str(item.get("snippet") or ""),
        )
        for item in items[:max_results]
    ]


async def _searxng(client: httpx.AsyncClient, query: str, max_results: int, base_url: str) -> List[SearchHit]:
    root = (base_url or "").rstrip("/")
    resp = await client.get(f"{root}/search", params={"q": query, "format": "json"}, headers={"Accept": "application/json"})
    resp.raise_for_status()
    items = resp.json().get("results") or []
    return [
        SearchHit(
            title=str(item.get("title") or ""),
            url=str(item.get("url") or ""),
            snippet=str(item.get("content") or ""),
        )
        for item in items[:max_results]
    ]


def _strip_html(value: str) -> str:
    return html.unescape(HTML_TAG_RE.sub("", value or "")).strip()


def _decode_ddg_url(raw: str) -> str:
    href = html.unescape(raw or "")
    if "uddg=" in href:
        try:
            parsed = urlparse(href)
            qs = parse_qs(parsed.query)
            if "uddg" in qs and qs["uddg"]:
                return unquote(qs["uddg"][0])
        except Exception:
            pass
    return href
