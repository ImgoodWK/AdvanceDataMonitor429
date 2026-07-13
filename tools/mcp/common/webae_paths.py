# -*- coding: utf-8 -*-
"""Shared paths for TeXTech WebAE local files (tokens / cfg)."""

from __future__ import print_function

import json
import os
import re

# tools/mcp/common -> repo root
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))


def instance_roots():
    roots = []
    env = os.environ.get("WEBAE_INSTANCE_ROOT") or os.environ.get("TEXTECH_INSTANCE_ROOT")
    if env:
        roots.append(os.path.abspath(env))
    roots.append(os.path.join(REPO_ROOT, "run"))
    roots.append(REPO_ROOT)
    # de-dupe preserve order
    seen = set()
    out = []
    for r in roots:
        if r and r not in seen:
            seen.add(r)
            out.append(r)
    return out


def find_file(*rel_parts):
    explicit = os.environ.get("WEBAE_TOKENS_FILE") if rel_parts[-1] == "web-tokens.json" else None
    if rel_parts[-1] == "textech.cfg":
        explicit = os.environ.get("WEBAE_CFG_FILE") or os.environ.get("TEXTECH_CFG_FILE")
    if explicit and os.path.isfile(explicit):
        return explicit
    for root in instance_roots():
        path = os.path.join(root, *rel_parts)
        if os.path.isfile(path):
            return path
    return None


def tokens_path():
    return find_file("TeXTech", "WebAE", "web-tokens.json")


def cfg_path():
    return find_file("config", "textech", "textech.cfg")


def load_tokens():
    path = tokens_path()
    if not path:
        return [], None
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        data = []
    return data, path


def pick_owner_token(prefer_owner_uuid=None):
    """Return a usable token string: WEBAE_TOKEN env, else first owner token on disk."""
    env = os.environ.get("WEBAE_TOKEN")
    if env:
        return env.strip(), "env:WEBAE_TOKEN"
    tokens, path = load_tokens()
    if not tokens:
        return None, path
    owners = []
    guests = []
    for t in tokens:
        if not isinstance(t, dict):
            continue
        tok = t.get("token")
        if not tok:
            continue
        typ = (t.get("type") or "owner").lower()
        owner = t.get("ownerUuid") or t.get("playerUuid") or ""
        if prefer_owner_uuid and owner != prefer_owner_uuid:
            continue
        if typ == "guest":
            guests.append(tok)
        else:
            owners.append(tok)
    if owners:
        return owners[0], path
    if guests:
        return guests[0], path
    return None, path


def parse_web_console_cfg(text):
    """Parse Forge Configuration webConsole / webconsole { } block into a dict."""
    # Forge lowercases category names on disk (webconsole).
    m = re.search(r"(?ims)^\s*webconsole\s*\{(.*?)^\}", text)
    if not m:
        return {}
    block = m.group(1)
    out = {}
    for line in block.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        # B:key=value / I:key=value / S:key=value / D:key=value
        km = re.match(r"^[BISD]:([^=]+)=(.*)$", line)
        if not km:
            continue
        key = km.group(1).strip()
        raw = km.group(2).strip()
        if raw.lower() in ("true", "false"):
            out[key] = raw.lower() == "true"
        else:
            try:
                if "." in raw:
                    out[key] = float(raw)
                else:
                    out[key] = int(raw)
            except ValueError:
                out[key] = raw
    return out


def effective_base_url(cfg=None):
    env = os.environ.get("WEBAE_BASE_URL")
    if env:
        return env.rstrip("/")
    bind = "127.0.0.1"
    port = 8090
    if cfg:
        bind = str(cfg.get("bindAddress") or bind)
        port = int(cfg.get("port") or port)
    if bind in ("0.0.0.0", "::"):
        bind = "127.0.0.1"
    return "http://%s:%s" % (bind, port)
