import argparse
import shutil
import time
from pathlib import Path


IMPORT_ANCHOR = "from .proactive_message import ProactiveMessageMixin\n"
IMPORT_LINE = "from .image_gallery_store import ImageGalleryStoreMixin\n"
BASE_ANCHOR = "    ProactiveMessageMixin,\n"
BASE_LINES = "    ImageGalleryStoreMixin,\n    ProactiveMessageMixin,\n"


def apply(target_dir: Path, *, check: bool = False) -> bool:
    main_path = target_dir / "main.py"
    if not main_path.is_file():
        raise FileNotFoundError(main_path)
    text = main_path.read_text(encoding="utf-8")
    changed = False
    if IMPORT_LINE not in text:
        if IMPORT_ANCHOR not in text:
            raise RuntimeError("main.py import anchor not found")
        text = text.replace(IMPORT_ANCHOR, IMPORT_ANCHOR + IMPORT_LINE, 1)
        changed = True
    if "    ImageGalleryStoreMixin," not in text:
        if BASE_ANCHOR not in text:
            raise RuntimeError("main.py class base anchor not found")
        text = text.replace(BASE_ANCHOR, BASE_LINES, 1)
        changed = True
    if check:
        if changed:
            raise RuntimeError("image gallery overlay is not applied")
        return False
    if changed:
        backup = main_path.with_name(f"main.py.bak.image_gallery_{int(time.time())}")
        shutil.copy2(main_path, backup)
        tmp = main_path.with_suffix(".py.image_gallery.tmp")
        tmp.write_text(text, encoding="utf-8")
        tmp.replace(main_path)
    return changed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("target", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    changed = apply(args.target, check=args.check)
    print("applied" if changed else "already-applied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
