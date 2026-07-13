#!/usr/bin/env python3
"""MCP server: TeXTech documentation checks (consistency / lang / manual)."""

from __future__ import print_function

import json
import os
import subprocess
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_COMMON = os.path.abspath(os.path.join(_HERE, "..", "common"))
ROOT = os.path.abspath(os.path.join(_HERE, "..", "..", ".."))
DOC_CHECK = os.path.join(ROOT, "tools", "doc-check", "doc-consistency-check.py")

if _COMMON not in sys.path:
    sys.path.insert(0, _COMMON)
if os.path.join(ROOT, "tools", "doc-check") not in sys.path:
    sys.path.insert(0, os.path.join(ROOT, "tools", "doc-check"))

import stdio_mcp  # noqa: E402

SERVER_NAME = "textech-doc-check"


def _run_script():
    proc = subprocess.Popen(
        [sys.executable, DOC_CHECK],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
    )
    out, err = proc.communicate()
    return {
        "ok": proc.returncode == 0,
        "exitCode": proc.returncode,
        "stdout": out or "",
        "stderr": err or "",
    }


def _import_checks():
    # Import module by path without relying on package name
    import importlib.util

    spec = importlib.util.spec_from_file_location("doc_consistency_check", DOC_CHECK)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


TOOLS = [
    {
        "name": "run_doc_consistency_check",
        "description": (
            "Runs tools/doc-check/doc-consistency-check.py (packet IDs, stale phrases, "
            "zh/en drift, worldMap docs, lang parity, manual chapters)."
        ),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "check_lang_parity",
        "description": "Compare en_US.lang vs zh_CN.lang key sets; report missing keys.",
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "check_manual_chapters",
        "description": "Check manual/index.json chapter ids vs chapters/*.json files.",
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
]


def dispatch(name, args):
    if name == "run_doc_consistency_check":
        data = _run_script()
        text = "exitCode=%s ok=%s\n\n--- stdout ---\n%s\n--- stderr ---\n%s" % (
            data["exitCode"],
            data["ok"],
            data["stdout"],
            data["stderr"],
        )
        return stdio_mcp.tool_text(text, is_error=not data["ok"], structured=data)
    if name == "check_lang_parity":
        mod = _import_checks()
        warnings = mod.check_lang_parity()
        data = {"ok": True, "warnings": warnings, "count": len(warnings)}
        return stdio_mcp.tool_text(json.dumps(data, ensure_ascii=False, indent=2), structured=data)
    if name == "check_manual_chapters":
        mod = _import_checks()
        warnings = mod.check_manual_chapters()
        data = {"ok": len(warnings) == 0, "warnings": warnings, "count": len(warnings)}
        return stdio_mcp.tool_text(
            json.dumps(data, ensure_ascii=False, indent=2),
            is_error=len(warnings) > 0,
            structured=data,
        )
    return None


def main():
    stdio_mcp.serve(SERVER_NAME, TOOLS, dispatch)


if __name__ == "__main__":
    main()
