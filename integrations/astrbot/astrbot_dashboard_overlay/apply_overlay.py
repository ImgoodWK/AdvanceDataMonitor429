"""Idempotently install the TeXTech portal SSO/bootstrap theme into AstrBot dist."""

import argparse
import shutil
from datetime import datetime
from pathlib import Path
from typing import Tuple

START = "<!-- TEXTECH_DASHBOARD_OVERLAY_START -->"
END = "<!-- TEXTECH_DASHBOARD_OVERLAY_END -->"
BLOCK = """<!-- TEXTECH_DASHBOARD_OVERLAY_START -->
  <link rel=\"stylesheet\" href=\"/textech-theme.css\">
  <script src=\"/textech-portal-sso.js\"></script>
<!-- TEXTECH_DASHBOARD_OVERLAY_END -->"""


def patched_index(source: str) -> Tuple[str, bool]:
    if START in source and END in source:
        before, rest = source.split(START, 1)
        _old, after = rest.split(END, 1)
        updated = before + BLOCK + after
        return updated, updated != source
    marker = "</head>"
    if marker not in source:
        raise ValueError("AstrBot dashboard index.html has no </head> marker")
    return source.replace(marker, f"  {BLOCK}\n{marker}", 1), True


def install(dist: Path, *, apply: bool) -> bool:
    index = dist if dist.name == "index.html" else dist / "index.html"
    root = index.parent
    source = index.read_text(encoding="utf-8")
    updated, changed = patched_index(source)
    if not apply:
        return changed

    script_dir = Path(__file__).resolve().parent
    if changed:
        stamp = datetime.now().strftime("%Y%m%dT%H%M%S")
        shutil.copy2(index, index.with_name(f"index.html.bak.{stamp}"))
        index.write_text(updated, encoding="utf-8")
    shutil.copy2(script_dir / "portal-sso.js", root / "textech-portal-sso.js")
    shutil.copy2(script_dir / "textech-theme.css", root / "textech-theme.css")
    return changed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dist", type=Path, help="AstrBot dashboard dist directory or index.html")
    parser.add_argument("--apply", action="store_true", help="write files after a successful dry-run check")
    args = parser.parse_args()
    changed = install(args.dist, apply=args.apply)
    print("overlay update required" if changed else "overlay already installed")


if __name__ == "__main__":
    main()
