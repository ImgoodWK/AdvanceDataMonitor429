import shutil
from pathlib import Path

from ..config import settings

SAFE_NAME = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_./")


def ensure_knowledge_dir() -> Path:
    settings.knowledge_dir.mkdir(parents=True, exist_ok=True)
    seed = settings.seed_knowledge
    if seed.exists() and not any(settings.knowledge_dir.iterdir()):
        for p in seed.rglob("*"):
            if p.is_file():
                rel = p.relative_to(seed)
                dest = settings.knowledge_dir / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(p, dest)
    return settings.knowledge_dir


def _resolve(rel: str) -> Path:
    rel = rel.replace("\\", "/").lstrip("/")
    if ".." in rel.split("/"):
        raise ValueError("invalid path")
    if any(c not in SAFE_NAME for c in rel):
        raise ValueError("invalid path characters")
    path = (settings.knowledge_dir / rel).resolve()
    root = settings.knowledge_dir.resolve()
    if not str(path).startswith(str(root)):
        raise ValueError("path escape")
    return path


def list_docs() -> list[dict]:
    ensure_knowledge_dir()
    docs = []
    for p in sorted(settings.knowledge_dir.rglob("*.md")):
        rel = str(p.relative_to(settings.knowledge_dir)).replace("\\", "/")
        docs.append({"path": rel, "size": p.stat().st_size, "mtime": int(p.stat().st_mtime)})
    return docs


def read_doc(rel: str) -> str:
    ensure_knowledge_dir()
    path = _resolve(rel)
    if not path.exists():
        raise FileNotFoundError(rel)
    return path.read_text(encoding="utf-8")


def write_doc(rel: str, content: str) -> None:
    ensure_knowledge_dir()
    if not rel.endswith(".md"):
        raise ValueError("only .md allowed")
    path = _resolve(rel)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def export_all() -> dict[str, str]:
    ensure_knowledge_dir()
    out: dict[str, str] = {}
    for p in settings.knowledge_dir.rglob("*.md"):
        rel = str(p.relative_to(settings.knowledge_dir)).replace("\\", "/")
        out[rel] = p.read_text(encoding="utf-8")
    return out


def append_changelog(entry: str) -> None:
    ensure_knowledge_dir()
    path = settings.knowledge_dir / "40-change-log.md"
    if not path.exists():
        path.write_text("# Change Log\n\n", encoding="utf-8")
    with path.open("a", encoding="utf-8") as f:
        f.write("\n" + entry.rstrip() + "\n")
