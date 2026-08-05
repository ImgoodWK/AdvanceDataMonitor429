#!/usr/bin/env python3
"""Validate TeXTech's public documents and machine-readable release inputs."""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

try:
    import yaml
except ImportError as exc:  # pragma: no cover - CI installs the pinned package
    raise SystemExit("PyYAML is required: python -m pip install PyYAML==6.0.2") from exc


ROOT = Path(__file__).resolve().parents[2]
IGNORED_PARTS = {
    ".git",
    ".gradle",
    ".workspace",
    "TeXTech",
    "build",
    "node_modules",
    "run",
}
MARKDOWN_LINK = re.compile(r"!?\[[^\]]*\]\((<[^>]+>|[^\s)]+)(?:\s+['\"][^'\"]*['\"])?\)")
HTML_LINK = re.compile(r"\b(?:href|src)\s*=\s*(['\"])(.*?)\1", re.IGNORECASE)


def public_files(*suffixes: str):
    # Prune ignored trees before traversal. ``Path.rglob`` still descends into
    # large runtime/build directories before the per-path filter can reject
    # them, which made this release gate unnecessarily slow on developer
    # workspaces.
    for directory, child_dirs, filenames in os.walk(ROOT, topdown=True):
        child_dirs[:] = sorted(name for name in child_dirs if name not in IGNORED_PARTS)
        base = Path(directory)
        for filename in sorted(filenames):
            path = base / filename
            if path.suffix.lower() in suffixes:
                yield path


def display(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def validate_json(errors: list[str]) -> int:
    count = 0
    for path in public_files(".json", ".json5", ".jsonc"):
        # JSON5/JSONC files are intentionally not parsed by the strict JSON
        # parser unless their content is also valid JSON.
        if path.suffix.lower() != ".json":
            continue
        count += 1
        try:
            json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{display(path)}: invalid JSON: {exc}")
    return count


def validate_yaml(errors: list[str]) -> int:
    count = 0
    for path in public_files(".yml", ".yaml", ".cff"):
        count += 1
        try:
            value = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
            if path.suffix.lower() == ".cff":
                if not isinstance(value, dict):
                    errors.append(f"{display(path)}: CFF root must be a mapping")
                else:
                    for field in ("cff-version", "message", "title", "version", "authors"):
                        if not value.get(field):
                            errors.append(f"{display(path)}: missing required CFF field {field!r}")
        except (OSError, UnicodeError, yaml.YAMLError) as exc:
            errors.append(f"{display(path)}: invalid YAML/CFF: {exc}")
    return count


def is_remote(target: str) -> bool:
    lowered = target.lower()
    return lowered.startswith(("http://", "https://", "mailto:", "data:")) or target.startswith("#")


def validate_local_link(source: Path, raw_target: str, errors: list[str]) -> None:
    target = raw_target.strip("<>")
    if not target or is_remote(target) or target.startswith("/") or "${{" in target:
        return
    parsed = urlsplit(target)
    relative = unquote(parsed.path)
    if not relative:
        return
    candidate = (source.parent / relative).resolve()
    try:
        candidate.relative_to(ROOT)
    except ValueError:
        errors.append(f"{display(source)}: link escapes repository: {target}")
        return
    if not candidate.exists():
        errors.append(f"{display(source)}: missing local link target: {target}")


def validate_markdown_links(errors: list[str]) -> int:
    count = 0
    for path in public_files(".md"):
        count += 1
        try:
            text = path.read_text(encoding="utf-8-sig")
        except (OSError, UnicodeError) as exc:
            errors.append(f"{display(path)}: cannot decode Markdown as UTF-8: {exc}")
            continue
        for match in MARKDOWN_LINK.finditer(text):
            validate_local_link(path, match.group(1), errors)
    return count


def validate_webae_bundle(errors: list[str]) -> int:
    bundle = ROOT / "src/main/resources/assets/textech/webae"
    index = bundle / "index.html"
    if not index.is_file():
        errors.append("src/main/resources/assets/textech/webae/index.html: missing generated WebAE entry point")
        return 0
    try:
        html = index.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{display(index)}: cannot decode generated entry point: {exc}")
        return 0
    references = []
    for _, raw_target in HTML_LINK.findall(html):
        if is_remote(raw_target) or raw_target.startswith(("#", "data:")):
            continue
        relative = unquote(urlsplit(raw_target).path).lstrip("./")
        if not relative:
            continue
        references.append(relative)
        if not (bundle / relative).is_file():
            errors.append(f"{display(index)}: missing generated asset: {raw_target}")
    if not any(item.endswith(".js") for item in references):
        errors.append(f"{display(index)}: generated entry point references no JavaScript bundle")
    if not any(item.endswith(".css") for item in references):
        errors.append(f"{display(index)}: generated entry point references no stylesheet bundle")
    return len(references)


def main() -> int:
    errors: list[str] = []
    json_count = validate_json(errors)
    yaml_count = validate_yaml(errors)
    markdown_count = validate_markdown_links(errors)
    bundle_refs = validate_webae_bundle(errors)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"Repository validation failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print(
        "Repository validation OK: "
        f"{json_count} JSON, {yaml_count} YAML/CFF, "
        f"{markdown_count} Markdown files, {bundle_refs} WebAE references."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
