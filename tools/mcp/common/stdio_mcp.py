# -*- coding: utf-8 -*-
"""Shared MCP stdio helpers (Content-Length framing, no third-party deps)."""

from __future__ import print_function

import json
import sys

PROTOCOL_VERSION = "2024-11-05"


def send(msg):
    body = json.dumps(msg, ensure_ascii=False)
    if sys.version_info[0] >= 3:
        data = body.encode("utf-8")
        header = ("Content-Length: %d\r\n\r\n" % len(data)).encode("ascii")
        sys.stdout.buffer.write(header)
        sys.stdout.buffer.write(data)
        sys.stdout.buffer.flush()
    else:
        data = body.encode("utf-8")
        sys.stdout.write("Content-Length: %d\r\n\r\n" % len(data))
        sys.stdout.write(data)
        sys.stdout.flush()


def result(req_id, value):
    send({"jsonrpc": "2.0", "id": req_id, "result": value})


def error(req_id, code, message):
    send({"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}})


def tool_text(text, is_error=False, structured=None):
    out = {
        "content": [{"type": "text", "text": text}],
        "isError": bool(is_error),
    }
    if structured is not None:
        out["structuredContent"] = structured
    return out


def read_message():
    if sys.version_info[0] >= 3:
        stdin = sys.stdin.buffer
    else:
        stdin = sys.stdin
    headers = {}
    while True:
        line = stdin.readline()
        if not line:
            return None
        if line in (b"\r\n", b"\n"):
            break
        if b":" in line:
            key, val = line.split(b":", 1)
            headers[key.strip().lower()] = val.strip()
    length = int(headers.get(b"content-length", b"0"))
    if length <= 0:
        return None
    body = stdin.read(length)
    if not body:
        return None
    return json.loads(body.decode("utf-8"))


def serve(server_name, tools, dispatch, version="1.0.0"):
    """Run MCP loop. dispatch(name, arguments) -> tool_text dict."""
    while True:
        try:
            msg = read_message()
        except ValueError as e:
            send(
                {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32700, "message": "Parse error: %s" % e},
                }
            )
            continue
        if msg is None:
            break
        if not isinstance(msg, dict):
            continue
        method = msg.get("method")
        req_id = msg.get("id")
        if method == "notifications/initialized":
            continue
        if method == "initialize":
            result(
                req_id,
                {
                    "protocolVersion": PROTOCOL_VERSION,
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": server_name, "version": version},
                },
            )
            continue
        if method == "ping":
            result(req_id, {})
            continue
        if method == "tools/list":
            result(req_id, {"tools": tools})
            continue
        if method == "tools/call":
            params = msg.get("params") or {}
            name = params.get("name")
            args = params.get("arguments") or {}
            try:
                out = dispatch(name, args)
            except Exception as e:
                result(req_id, tool_text("Tool error: %s" % e, is_error=True))
                continue
            if out is None:
                error(req_id, -32601, "Unknown tool: %s" % name)
            else:
                result(req_id, out)
            continue
        if req_id is not None:
            error(req_id, -32601, "Method not found: %s" % method)
