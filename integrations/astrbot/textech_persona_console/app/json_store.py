import json
import os
import re
import shutil
import threading
import time
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any

from .config import settings

_lock = threading.RLock()


def data_path(rel: str) -> Path:
    return settings.astrbot_data / rel


def backup_paths(paths: list[Path]) -> str:
    stamp = time.strftime("%Y%m%d_%H%M%S")
    backup_parent = settings.astrbot_data / "backups"
    backup_root = backup_parent / f"console_{stamp}"
    suffix = 2
    while backup_root.exists():
        backup_root = backup_parent / f"console_{stamp}_{suffix:02d}"
        suffix += 1
    backup_root.mkdir(parents=True, exist_ok=True)
    for p in paths:
        if not p.exists():
            continue
        rel = p.relative_to(settings.astrbot_data) if p.is_relative_to(settings.astrbot_data) else Path(p.name)
        dest = backup_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, dest)
    return str(backup_root)


def read_json(rel: str, default: Any = None) -> Any:
    path = data_path(rel)
    with _lock:
        if not path.exists():
            return {} if default is None else default
        text = path.read_text(encoding="utf-8-sig")
        if not text.strip():
            return {} if default is None else default
        return json.loads(text)


def write_json(rel: str, data: Any, *, do_backup: bool = True) -> str | None:
    path = data_path(rel)
    with _lock:
        backup = None
        if do_backup and path.exists():
            backup = backup_paths([path])
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(path)
        return backup


BACKUP_NAME_RE = re.compile(r"console_\d{8}_\d{6}(?:_\d{2})?")


def _backup_dir(name: str) -> Path:
    if not BACKUP_NAME_RE.fullmatch(str(name or "").strip()):
        raise ValueError("invalid backup name")
    root = (settings.astrbot_data / "backups").resolve()
    path = (root / name).resolve()
    if not path.is_relative_to(root):
        raise ValueError("backup path escapes backup root")
    if not path.is_dir():
        raise FileNotFoundError(name)
    return path


def _backup_files(path: Path) -> list[Path]:
    return sorted(
        (
            item
            for item in path.rglob("*")
            if item.is_file() and not item.is_symlink() and item.suffix.lower() == ".json"
        ),
        key=lambda item: item.relative_to(path).as_posix(),
    )


def list_backups(limit: int = 100) -> dict[str, Any]:
    root = settings.astrbot_data / "backups"
    if not root.is_dir():
        return {"items": [], "total": 0}
    candidates = [
        path
        for path in root.iterdir()
        if path.is_dir() and not path.is_symlink() and BACKUP_NAME_RE.fullmatch(path.name)
    ]
    candidates.sort(key=lambda path: path.stat().st_mtime, reverse=True)
    total = len(candidates)
    items: list[dict[str, Any]] = []
    for path in candidates[: max(1, min(int(limit), 500))]:
        files = _backup_files(path)
        stat = path.stat()
        items.append(
            {
                "name": path.name,
                "created_at": datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat(),
                "file_count": len(files),
                "total_bytes": sum(item.stat().st_size for item in files),
                "files": [
                    {
                        "path": item.relative_to(path).as_posix(),
                        "bytes": item.stat().st_size,
                    }
                    for item in files[:500]
                ],
                "files_truncated": len(files) > 500,
            }
        )
    return {"items": items, "total": total}


def _normalize_backup_file(raw: str) -> Path:
    text = str(raw or "").strip().replace("\\", "/")
    rel = PurePosixPath(text)
    if not text or rel.is_absolute() or any(part in {"", ".", ".."} for part in rel.parts):
        raise ValueError(f"invalid backup file: {raw}")
    return Path(*rel.parts)


def restore_backup(snapshot: str, files: list[str] | None = None) -> dict[str, Any]:
    """Restore validated JSON files and first snapshot the current destinations."""
    backup = _backup_dir(snapshot)
    available = {
        item.relative_to(backup).as_posix(): item
        for item in _backup_files(backup)
    }
    if files is not None and not files:
        raise ValueError("files must not be empty")
    requested = list(available) if files is None else [str(item) for item in files]
    if not requested:
        raise ValueError("backup contains no restorable JSON files")

    data_root = settings.astrbot_data.resolve()
    plans: list[tuple[Path, Path, str]] = []
    seen: set[str] = set()
    for raw in requested:
        rel = _normalize_backup_file(raw)
        rel_posix = rel.as_posix()
        if rel_posix in seen:
            continue
        seen.add(rel_posix)
        source = available.get(rel_posix)
        if source is None:
            raise FileNotFoundError(rel_posix)
        # Parse everything before touching live files; malformed backups never restore.
        json.loads(source.read_text(encoding="utf-8-sig"))
        target = (data_root / rel).resolve()
        if not target.is_relative_to(data_root) or target.suffix.lower() != ".json":
            raise ValueError(f"restore target is not an AstrBot JSON file: {rel_posix}")
        plans.append((source, target, rel_posix))

    with _lock:
        current = [target for _source, target, _rel in plans if target.exists()]
        safety_backup = backup_paths(current) if current else None
        staged: list[tuple[Path, Path]] = []
        try:
            for source, target, _rel in plans:
                target.parent.mkdir(parents=True, exist_ok=True)
                tmp = target.with_name(f".{target.name}.restore.tmp")
                shutil.copy2(source, tmp)
                staged.append((tmp, target))
            for tmp, target in staged:
                os.replace(tmp, target)
        except Exception:
            for tmp, _target in staged:
                try:
                    tmp.unlink(missing_ok=True)
                except OSError:
                    pass
            raise

    return {
        "ok": True,
        "snapshot": backup.name,
        "restored": [rel for _source, _target, rel in plans],
        "safety_backup": Path(safety_backup).name if safety_backup else None,
        "requires_restart": True,
    }
