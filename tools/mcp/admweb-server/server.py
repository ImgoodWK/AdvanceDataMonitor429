#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MCP server: read-only TeXTech /admweb local files (tokens + cfg). No RCON."""

from __future__ import print_function

import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_COMMON = os.path.abspath(os.path.join(_HERE, "..", "common"))
if _COMMON not in sys.path:
    sys.path.insert(0, _COMMON)

import stdio_mcp  # noqa: E402
import webae_paths  # noqa: E402

SERVER_NAME = "textech-admweb"


def _mask_token(tok, reveal):
    if reveal or not tok:
        return tok
    if len(tok) <= 8:
        return "***"
    return tok[:4] + "..." + tok[-4:]


TOOLS = [
    {
        "name": "read_web_tokens",
        "description": (
            "List TeXTech/WebAE/web-tokens.json entries (local instance). "
            "Tokens are masked by default; set reveal=true or WEBAE_REVEAL_TOKENS=1 to show full values. "
            "Read-only; does not call /admweb issue."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "reveal": {
                    "type": "boolean",
                    "description": "If true, return full token strings (local dev only)",
                }
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "read_webae_config",
        "description": (
            "Read [webConsole] section from config/textech/textech.cfg under the instance root. "
            "Same discovery as webae_config (run/ or WEBAE_INSTANCE_ROOT)."
        ),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
]


def dispatch(name, args):
    args = args or {}
    if name == "read_web_tokens":
        reveal = bool(args.get("reveal")) or os.environ.get("WEBAE_REVEAL_TOKENS", "").strip() in (
            "1",
            "true",
            "TRUE",
            "yes",
        )
        tokens, path = webae_paths.load_tokens()
        entries = []
        for t in tokens:
            if not isinstance(t, dict):
                continue
            entries.append(
                {
                    "type": t.get("type") or "owner",
                    "ownerUuid": t.get("ownerUuid") or t.get("playerUuid"),
                    "actorUuid": t.get("actorUuid"),
                    "actorName": t.get("actorName"),
                    "issuedAt": t.get("issuedAt"),
                    "token": _mask_token(t.get("token"), reveal),
                }
            )
        data = {
            "path": path,
            "count": len(entries),
            "revealed": reveal,
            "tokens": entries,
            "hint": "Issue tokens in-game with /admweb issue; set WEBAE_TOKEN for API MCP.",
        }
        return stdio_mcp.tool_text(json.dumps(data, ensure_ascii=False, indent=2), structured=data)
    if name == "read_webae_config":
        path = webae_paths.cfg_path()
        cfg = {}
        if path:
            with open(path, "r", encoding="utf-8", errors="replace") as f:
                cfg = webae_paths.parse_web_console_cfg(f.read())
        data = {
            "cfgPath": path,
            "webConsole": cfg,
            "baseUrl": webae_paths.effective_base_url(cfg),
            "instanceRootsTried": webae_paths.instance_roots(),
        }
        return stdio_mcp.tool_text(json.dumps(data, ensure_ascii=False, indent=2), structured=data)
    return None


def main():
    stdio_mcp.serve(SERVER_NAME, TOOLS, dispatch)


if __name__ == "__main__":
    main()
