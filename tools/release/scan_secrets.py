#!/usr/bin/env python3
"""High-confidence secret scanner for tracked, staged, or publishable files."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAX_FILE_SIZE = 5 * 1024 * 1024
PATTERNS = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b"),
    "OpenAI-style key": re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
    "AWS access key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
}
ASSIGNMENT = re.compile(
    r"(?i)\b(?:api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|password|secret)\b"
    r"\s*[:=]\s*(?:['\"]([A-Za-z0-9_./+=:-]{20,})['\"]|([A-Za-z0-9_./+=:-]{20,}))"
    r"\s*(?:[,;#]|//|$)"
)
PLACEHOLDER_MARKERS = (
    "change-me",
    "changeme",
    "dummy",
    "example",
    "placeholder",
    "replace",
    "test-token",
    "your-",
    "your_",
)


def git_paths(args: list[str]) -> list[Path]:
    result = subprocess.run(
        ["git", *args, "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [ROOT / item.decode("utf-8", "surrogateescape") for item in result.stdout.split(b"\0") if item]


def candidates(mode: str) -> list[Path]:
    if mode == "staged":
        return git_paths(["diff", "--cached", "--name-only", "--diff-filter=ACMR"])
    if mode == "tracked":
        return git_paths(["ls-files"])
    return git_paths(["ls-files", "--cached", "--others", "--exclude-standard"])


def looks_like_placeholder(value: str) -> bool:
    lowered = value.lower()
    return any(marker in lowered for marker in PLACEHOLDER_MARKERS) or set(value) <= {"*", "x", "X", "0", "-", "_"}


def scan_file(path: Path) -> list[tuple[int, str]]:
    try:
        if not path.is_file() or path.stat().st_size > MAX_FILE_SIZE:
            return []
        raw = path.read_bytes()
        if b"\0" in raw:
            return []
        text = raw.decode("utf-8")
    except (OSError, UnicodeError):
        return []
    findings: list[tuple[int, str]] = []
    for number, line in enumerate(text.splitlines(), 1):
        for label, pattern in PATTERNS.items():
            if pattern.search(line):
                findings.append((number, label))
        assignment = ASSIGNMENT.search(line)
        value = next((group for group in assignment.groups() if group), "") if assignment else ""
        if value and not looks_like_placeholder(value):
            findings.append((number, "credential-like assignment"))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--tracked", action="store_true", help="scan tracked files (CI mode)")
    group.add_argument("--staged", action="store_true", help="scan only staged additions and modifications")
    options = parser.parse_args()
    mode = "staged" if options.staged else "tracked" if options.tracked else "publishable"
    findings = []
    for path in sorted(set(candidates(mode))):
        for line, label in scan_file(path):
            findings.append((path.relative_to(ROOT).as_posix(), line, label))
    if findings:
        for path, line, label in findings:
            print(f"{path}:{line}: potential {label}", file=sys.stderr)
        print(f"Secret scan failed with {len(findings)} high-confidence finding(s).", file=sys.stderr)
        return 1
    print(f"Secret scan OK ({mode} files; high-confidence patterns).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
