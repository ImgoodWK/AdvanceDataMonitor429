#!/usr/bin/env python3
"""Make a review thumbnail so agents do not feed huge PNGs into the model.

Usage:
  python tools/agent-image-thumb.py <image> [--max-edge 512] [--out DIR]

Prints one JSON object to stdout with source/thumb metadata.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path, help="Source image path")
    parser.add_argument("--max-edge", type=int, default=512)
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(".workspace/agent-image-thumbs"),
        help="Output directory for thumbs",
    )
    args = parser.parse_args()

    src = args.image.resolve()
    if not src.is_file():
        print(json.dumps({"ok": False, "error": f"not a file: {src}"}), flush=True)
        return 1

    try:
        from PIL import Image
    except ImportError:
        print(
            json.dumps(
                {
                    "ok": False,
                    "error": "Pillow is required (pip install pillow)",
                }
            ),
            flush=True,
        )
        return 1

    with Image.open(src) as im:
        im.load()
        width, height = im.size
        mode = im.mode
        out_dir = args.out
        if not out_dir.is_absolute():
            out_dir = Path.cwd() / out_dir
        out_dir.mkdir(parents=True, exist_ok=True)

        max_edge = max(1, args.max_edge)
        scale = min(1.0, max_edge / float(max(width, height)))
        thumb_w = max(1, int(round(width * scale)))
        thumb_h = max(1, int(round(height * scale)))

        work = im.convert("RGBA") if im.mode not in ("RGB", "RGBA") else im.copy()
        if scale < 1.0:
            work = work.resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)

        stem = src.stem
        digest = sha256_file(src)[:12]
        thumb_path = out_dir / f"{stem}.{digest}.w{thumb_w}.png"
        work.save(thumb_path, format="PNG", optimize=True)

        payload = {
            "ok": True,
            "source": str(src),
            "source_width": width,
            "source_height": height,
            "source_mode": mode,
            "source_bytes": src.stat().st_size,
            "source_sha256": sha256_file(src),
            "thumb": str(thumb_path.resolve()),
            "thumb_width": thumb_w,
            "thumb_height": thumb_h,
            "thumb_bytes": thumb_path.stat().st_size,
            "downscaled": scale < 1.0,
            "max_edge": max_edge,
        }
        print(json.dumps(payload, ensure_ascii=False), flush=True)
        return 0


if __name__ == "__main__":
    sys.exit(main())
