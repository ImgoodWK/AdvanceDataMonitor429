#!/usr/bin/env python3
"""Search GitHub's public code index for disclosed TeXTech provenance markers."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG = ROOT / ".github/provenance-monitor.json"
API_URL = "https://api.github.com/search/code"
API_VERSION = "2022-11-28"


class MonitorError(RuntimeError):
    pass


@dataclass(frozen=True)
class Finding:
    query_ids: tuple[str, ...]
    repository: str
    path: str
    url: str
    blob_sha: str

    def key(self) -> tuple[str, str, str]:
        return (self.repository.casefold(), self.path, self.blob_sha)


def load_config(path: Path) -> tuple[dict[str, Any], str]:
    raw = path.read_bytes()
    try:
        config = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise MonitorError(f"Invalid monitor config {path}: {exc}") from exc
    if config.get("schema_version") != 1:
        raise MonitorError("Unsupported provenance monitor schema_version")
    if not isinstance(config.get("official_repository"), str):
        raise MonitorError("official_repository must be a repository name")
    queries = config.get("queries")
    if not isinstance(queries, list) or not queries:
        raise MonitorError("queries must be a non-empty list")
    ids = set()
    for query in queries:
        if not isinstance(query, dict) or not all(isinstance(query.get(k), str) for k in ("id", "label", "query")):
            raise MonitorError("each query requires string id, label, and query fields")
        if query["id"] in ids:
            raise MonitorError(f"duplicate query id: {query['id']}")
        ids.add(query["id"])
    return config, hashlib.sha256(raw).hexdigest()


def request_json(url: str, token: str, opener: Callable[..., Any] = urlopen) -> dict[str, Any]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "TeXTech-public-provenance-monitor/1.0",
        "X-GitHub-Api-Version": API_VERSION,
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, headers=headers)
    try:
        with opener(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:1000]
        raise MonitorError(f"GitHub search returned HTTP {exc.code}: {detail}") from exc
    except (URLError, TimeoutError, UnicodeError, json.JSONDecodeError) as exc:
        raise MonitorError(f"GitHub search failed: {exc}") from exc


def search_query(
    query: str,
    token: str,
    opener: Callable[..., Any] = urlopen,
    max_pages: int = 10,
) -> list[dict[str, Any]]:
    # is:public is both a visible policy statement and a server-side boundary.
    public_query = query if "is:public" in query else f"{query} is:public"
    items: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        params = urlencode({"q": public_query, "per_page": 100, "page": page})
        payload = request_json(f"{API_URL}?{params}", token, opener)
        page_items = payload.get("items", [])
        if not isinstance(page_items, list):
            raise MonitorError("GitHub search response has no items list")
        items.extend(item for item in page_items if isinstance(item, dict))
        if len(page_items) < 100 or len(items) >= int(payload.get("total_count", 0)):
            break
        time.sleep(1)
    return items


def public_finding(item: dict[str, Any], query_id: str, allowlist: set[str]) -> Finding | None:
    repository = item.get("repository")
    if not isinstance(repository, dict):
        return None
    full_name = repository.get("full_name")
    if not isinstance(full_name, str) or full_name.casefold() in allowlist:
        return None
    # Never report or retain a non-public result, even if a token unexpectedly exposes one.
    if repository.get("private") is True:
        return None
    visibility = repository.get("visibility")
    if visibility not in (None, "public"):
        return None
    path = item.get("path")
    url = item.get("html_url")
    sha = item.get("sha")
    if not all(isinstance(value, str) and value for value in (path, url, sha)):
        return None
    return Finding((query_id,), full_name, path, url, sha)


def merge_findings(findings: list[Finding]) -> list[Finding]:
    merged: dict[tuple[str, str, str], Finding] = {}
    for finding in findings:
        previous = merged.get(finding.key())
        if previous is None:
            merged[finding.key()] = finding
            continue
        query_ids = tuple(sorted(set(previous.query_ids + finding.query_ids)))
        merged[finding.key()] = Finding(query_ids, finding.repository, finding.path, finding.url, finding.blob_sha)
    return sorted(merged.values(), key=lambda item: (item.repository.casefold(), item.path, item.blob_sha))


def build_report(
    config: dict[str, Any],
    config_sha256: str,
    query_counts: dict[str, int],
    findings: list[Finding],
) -> dict[str, Any]:
    return {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "official_repository": config["official_repository"],
        "config_sha256": config_sha256,
        "public_index_only": True,
        "query_result_counts_before_allowlist": query_counts,
        "unallowed_public_findings": [
            {
                "query_ids": list(item.query_ids),
                "repository": item.repository,
                "path": item.path,
                "url": item.url,
                "blob_sha": item.blob_sha,
            }
            for item in findings
        ],
        "limitations": config.get("limitations", []),
    }


def write_reports(output_dir: Path, report: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "provenance-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    lines = [
        "# TeXTech public provenance monitor report",
        "",
        f"- Generated: `{report['generated_at']}`",
        f"- Official repository: `{report['official_repository']}`",
        f"- Config SHA-256: `{report['config_sha256']}`",
        "- Scope: GitHub public code index only",
        f"- Unallowed public findings: **{len(report['unallowed_public_findings'])}**",
        "",
    ]
    if report["unallowed_public_findings"]:
        lines.extend(["## Findings requiring human review", ""])
        for item in report["unallowed_public_findings"]:
            lines.extend(
                [
                    f"- [{item['repository']} / `{item['path']}`]({item['url']})",
                    f"  - Blob SHA: `{item['blob_sha']}`",
                    f"  - Query IDs: `{', '.join(item['query_ids'])}`",
                ]
            )
        lines.extend(
            [
                "",
                "> These matches are investigation leads, not proof of copying or infringement.",
                "> Do not contact or accuse a repository owner without manual evidence review.",
                "",
            ]
        )
    else:
        lines.extend(["No unallowed public matches were returned by this run.", ""])
    lines.extend(["## Limitations", ""])
    lines.extend(f"- {item}" for item in report.get("limitations", []))
    lines.extend(
        [
            "",
            "## Manual evidence checklist",
            "",
            "Preserve the public URL, repository and blob commit SHA, relevant tag/release times,",
            "a dated page snapshot, original files, a focused side-by-side diff, the displayed",
            "license, and the access date. Record verifiable facts only.",
            "",
        ]
    )
    (output_dir / "provenance-report.md").write_text("\n".join(lines), encoding="utf-8")


def run(config_path: Path, output_dir: Path, token: str, opener: Callable[..., Any] = urlopen) -> int:
    config, config_sha256 = load_config(config_path)
    allowlist = {name.casefold() for name in config.get("allowlist", []) if isinstance(name, str)}
    allowlist.add(config["official_repository"].casefold())
    findings: list[Finding] = []
    counts: dict[str, int] = {}
    for query in config["queries"]:
        items = search_query(query["query"], token, opener)
        counts[query["id"]] = len(items)
        for item in items:
            finding = public_finding(item, query["id"], allowlist)
            if finding is not None:
                findings.append(finding)
    findings = merge_findings(findings)
    report = build_report(config, config_sha256, counts, findings)
    write_reports(output_dir, report)
    print(f"Public provenance monitor completed: {len(findings)} unallowed finding(s).")
    return 2 if findings else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--output-dir", type=Path, default=ROOT / ".workspace/provenance-monitor")
    options = parser.parse_args()
    try:
        return run(options.config, options.output_dir, os.environ.get("GITHUB_TOKEN", ""))
    except MonitorError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
