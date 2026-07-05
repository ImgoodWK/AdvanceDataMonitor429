#!/usr/bin/env python3
"""
Convert IconExporter / blockexporter PNG names to WebAE icon cache keys.

Usage:
  python convert_icon_exporter.py --input item_exports --output web-icons-nei

Input naming (IconExporter):  namespace_item_path.png  or  namespace_item_123.png (meta)
Output naming (WebAE):        namespace_item_path.png  or  namespace_item_path_123.png
                              colons in registry names become underscores on disk.
"""
from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path

META_SUFFIX = re.compile(r"^(.+)_(\d+)$")


def to_webae_filename(stem: str) -> str:
    stem = stem.replace(":", "_")
    m = META_SUFFIX.match(stem)
    if not m:
        return stem + ".png"
    base, meta = m.group(1), m.group(2)
    if base.count("_") >= 1 and meta.isdigit():
        # namespace_item_meta -> namespace:item:meta on disk as namespace_item_meta
        return f"{base}_{meta}.png"
    return stem + ".png"


def main() -> None:
    ap = argparse.ArgumentParser(description="Convert IconExporter PNG folder for WebAE import")
    ap.add_argument("--input", required=True, help="Source folder (item_exports)")
    ap.add_argument("--output", required=True, help="Destination folder (flat PNGs for /admweb icons import)")
    args = ap.parse_args()
    src = Path(args.input)
    dst = Path(args.output)
    dst.mkdir(parents=True, exist_ok=True)
    count = 0
    for png in src.glob("*.png"):
        out_name = to_webae_filename(png.stem)
        shutil.copy2(png, dst / out_name)
        count += 1
    print(f"Converted {count} icons -> {dst}")


if __name__ == "__main__":
    main()
