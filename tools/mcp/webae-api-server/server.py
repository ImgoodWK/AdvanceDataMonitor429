#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MCP server: call TeXTech WebAE REST APIs with Bearer token."""

from __future__ import print_function

import json
import os
import sys

try:
    from urllib.error import HTTPError, URLError
    from urllib.parse import urlencode
    from urllib.request import Request, urlopen
except ImportError:
    from urllib import urlencode  # type: ignore
    from urllib2 import HTTPError, Request, URLError, urlopen  # type: ignore

_HERE = os.path.dirname(os.path.abspath(__file__))
_COMMON = os.path.abspath(os.path.join(_HERE, "..", "common"))
if _COMMON not in sys.path:
    sys.path.insert(0, _COMMON)

import stdio_mcp  # noqa: E402
import webae_paths  # noqa: E402

SERVER_NAME = "textech-webae-api"


def _http_get(path, query=None, token=None, timeout=20):
    cfg_text = ""
    cfg_file = webae_paths.cfg_path()
    cfg = {}
    if cfg_file:
        with open(cfg_file, "r", encoding="utf-8", errors="replace") as f:
            cfg_text = f.read()
        cfg = webae_paths.parse_web_console_cfg(cfg_text)

    base = webae_paths.effective_base_url(cfg)
    tok, tok_src = (token, "argument") if token else webae_paths.pick_owner_token()
    if not tok:
        return {
            "ok": False,
            "error": "No token. Set WEBAE_TOKEN or issue one via /admweb issue (web-tokens.json).",
            "tokenSource": tok_src,
            "baseUrl": base,
        }

    q = ""
    if query:
        q = "?" + urlencode(query)
    url = base + path + q
    req = Request(url)
    req.add_header("Authorization", "Bearer " + tok)
    req.add_header("Accept", "application/json")
    try:
        resp = urlopen(req, timeout=timeout)
        body = resp.read()
        if isinstance(body, bytes):
            body = body.decode("utf-8", "replace")
        status = getattr(resp, "status", None) or resp.getcode()
        try:
            parsed = json.loads(body)
        except ValueError:
            parsed = None
        return {
            "ok": 200 <= int(status) < 300,
            "status": int(status),
            "url": url,
            "tokenSource": tok_src,
            "baseUrl": base,
            "body": parsed if parsed is not None else body,
        }
    except HTTPError as e:
        err_body = e.read()
        if isinstance(err_body, bytes):
            err_body = err_body.decode("utf-8", "replace")
        return {
            "ok": False,
            "status": int(e.code),
            "url": url,
            "tokenSource": tok_src,
            "baseUrl": base,
            "body": err_body,
        }
    except URLError as e:
        return {
            "ok": False,
            "error": "Connection failed: %s (is WebAE running on %s?)" % (e.reason, base),
            "url": url,
            "tokenSource": tok_src,
            "baseUrl": base,
        }


def _dump(data):
    return json.dumps(data, ensure_ascii=False, indent=2)


TOOLS = [
    {
        "name": "webae_health",
        "description": "GET /api/server/health — TPS/MSPT/uptime snapshot from local WebAE.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "token": {"type": "string", "description": "Optional Bearer token override"},
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_diagnostics",
        "description": "GET /api/server/diagnostics — WebAE perf snapshot (routes/tick/collects).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "token": {"type": "string"},
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_networks",
        "description": "GET /api/networks — list AE networks visible to the token owner.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "token": {"type": "string"},
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_storage",
        "description": "GET /api/storage?network= — AE storage snapshot (cache read).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "network": {"type": "string", "description": "Network id (required)"},
                "token": {"type": "string"},
            },
            "required": ["network"],
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_recipes_search",
        "description": "GET /api/recipes/search?q= — fuzzy recipe search in WebAE cache.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "q": {"type": "string", "description": "Search query"},
                "limit": {"type": "integer", "description": "Optional limit"},
                "token": {"type": "string"},
            },
            "required": ["q"],
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_config",
        "description": (
            "Read effective [webConsole] settings from textech.cfg (and env overrides). "
            "Does not call HTTP."
        ),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
]


def dispatch(name, args):
    args = args or {}
    token = args.get("token")
    if name == "webae_health":
        data = _http_get("/api/server/health", token=token)
        return stdio_mcp.tool_text(_dump(data), is_error=not data.get("ok"), structured=data)
    if name == "webae_diagnostics":
        data = _http_get("/api/server/diagnostics", token=token)
        return stdio_mcp.tool_text(_dump(data), is_error=not data.get("ok"), structured=data)
    if name == "webae_networks":
        data = _http_get("/api/networks", token=token)
        return stdio_mcp.tool_text(_dump(data), is_error=not data.get("ok"), structured=data)
    if name == "webae_storage":
        network = args.get("network")
        if not network:
            return stdio_mcp.tool_text("network is required", is_error=True)
        data = _http_get("/api/storage", query={"network": str(network)}, token=token)
        return stdio_mcp.tool_text(_dump(data), is_error=not data.get("ok"), structured=data)
    if name == "webae_recipes_search":
        q = args.get("q")
        if not q:
            return stdio_mcp.tool_text("q is required", is_error=True)
        query = {"q": str(q)}
        if args.get("limit") is not None:
            query["limit"] = str(args.get("limit"))
        data = _http_get("/api/recipes/search", query=query, token=token)
        return stdio_mcp.tool_text(_dump(data), is_error=not data.get("ok"), structured=data)
    if name == "webae_config":
        path = webae_paths.cfg_path()
        cfg = {}
        if path:
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                cfg = webae_paths.parse_web_console_cfg(f.read())
        data = {
            "cfgPath": path,
            "webConsole": cfg,
            "baseUrl": webae_paths.effective_base_url(cfg),
            "env": {
                "WEBAE_BASE_URL": os.environ.get("WEBAE_BASE_URL"),
                "WEBAE_TOKEN_set": bool(os.environ.get("WEBAE_TOKEN")),
                "WEBAE_INSTANCE_ROOT": os.environ.get("WEBAE_INSTANCE_ROOT")
                or os.environ.get("TEXTECH_INSTANCE_ROOT"),
            },
        }
        return stdio_mcp.tool_text(_dump(data), structured=data)
    return None


def main():
    stdio_mcp.serve(SERVER_NAME, TOOLS, dispatch)


if __name__ == "__main__":
    main()
