#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MCP server: run TeXTech Gradle / WebAE npm build & test with truncated output."""

from __future__ import print_function

import json
import os
import subprocess
import sys
import time

_HERE = os.path.dirname(os.path.abspath(__file__))
_COMMON = os.path.abspath(os.path.join(_HERE, "..", "common"))
ROOT = os.path.abspath(os.path.join(_HERE, "..", "..", ".."))
if _COMMON not in sys.path:
    sys.path.insert(0, _COMMON)

import stdio_mcp  # noqa: E402

SERVER_NAME = "textech-build"
DEFAULT_TIMEOUT = 900
MAX_OUT = 24000


TOOLS = [
    {
        "name": "gradle_build",
        "description": (
            "Run ./gradlew build (or gradlew.bat on Windows) in the TeXTech repo root. "
            "Long-running; returns exit code and truncated stdout/stderr."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "args": {
                    "type": "string",
                    "description": "Extra Gradle args (default: build). Example: 'build -x test'",
                },
                "timeoutSec": {"type": "integer", "description": "Timeout seconds (default 900)"},
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_npm_build",
        "description": "Run npm run build in webae-frontend/ (produces assets/textech/webae/).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "timeoutSec": {"type": "integer"},
                "install": {
                    "type": "boolean",
                    "description": "If true, run npm ci (or npm install) first",
                },
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "webae_npm_test",
        "description": "Run npm test (vitest) in webae-frontend/.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "timeoutSec": {"type": "integer"},
            },
            "additionalProperties": False,
        },
    },
]


def _truncate(s):
    if s is None:
        return ""
    if len(s) <= MAX_OUT:
        return s
    return s[: MAX_OUT // 2] + "\n\n...[truncated]...\n\n" + s[-MAX_OUT // 2 :]


def _run(cmd, cwd, timeout):
    t0 = time.time()
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
            shell=False,
        )
        out, err = proc.communicate(timeout=timeout)
        return {
            "ok": proc.returncode == 0,
            "exitCode": proc.returncode,
            "seconds": round(time.time() - t0, 1),
            "cmd": cmd,
            "cwd": cwd,
            "stdout": _truncate(out),
            "stderr": _truncate(err),
        }
    except subprocess.TimeoutExpired:
        proc.kill()
        out, err = proc.communicate()
        return {
            "ok": False,
            "exitCode": -1,
            "seconds": round(time.time() - t0, 1),
            "cmd": cmd,
            "cwd": cwd,
            "stdout": _truncate(out),
            "stderr": _truncate(err) + "\nTIMEOUT after %ss" % timeout,
            "error": "timeout",
        }
    except OSError as e:
        return {
            "ok": False,
            "exitCode": 127,
            "seconds": round(time.time() - t0, 1),
            "cmd": cmd,
            "cwd": cwd,
            "stdout": "",
            "stderr": str(e),
            "error": "os_error",
        }


def _gradle_cmd(extra_args):
    if sys.platform == "win32":
        base = [os.path.join(ROOT, "gradlew.bat")]
    else:
        base = [os.path.join(ROOT, "gradlew")]
    if not extra_args:
        return base + ["build"]
    # split on spaces carefully enough for simple flags
    return base + extra_args.split()


def dispatch(name, args):
    args = args or {}
    timeout = int(args.get("timeoutSec") or DEFAULT_TIMEOUT)
    if name == "gradle_build":
        data = _run(_gradle_cmd(args.get("args")), ROOT, timeout)
        return stdio_mcp.tool_text(json.dumps(data, ensure_ascii=False, indent=2), is_error=not data["ok"], structured=data)
    if name == "webae_npm_build":
        frontend = os.path.join(ROOT, "webae-frontend")
        parts = []
        if args.get("install"):
            lock = os.path.join(frontend, "package-lock.json")
            install_cmd = ["npm", "ci"] if os.path.isfile(lock) else ["npm", "install"]
            parts.append(_run(install_cmd, frontend, timeout))
            if not parts[-1]["ok"]:
                data = {"ok": False, "steps": parts}
                return stdio_mcp.tool_text(
                    json.dumps(data, ensure_ascii=False, indent=2), is_error=True, structured=data
                )
        parts.append(_run(["npm", "run", "build"], frontend, timeout))
        data = {"ok": all(p["ok"] for p in parts), "steps": parts}
        return stdio_mcp.tool_text(
            json.dumps(data, ensure_ascii=False, indent=2), is_error=not data["ok"], structured=data
        )
    if name == "webae_npm_test":
        frontend = os.path.join(ROOT, "webae-frontend")
        data = _run(["npm", "test"], frontend, timeout)
        return stdio_mcp.tool_text(json.dumps(data, ensure_ascii=False, indent=2), is_error=not data["ok"], structured=data)
    return None


def main():
    stdio_mcp.serve(SERVER_NAME, TOOLS, dispatch)


if __name__ == "__main__":
    main()
