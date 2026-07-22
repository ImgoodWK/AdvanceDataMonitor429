"""Migrate SoulMap user_profiles.json -> persona_lib personas.json."""

from __future__ import annotations

import json
import re
from datetime import datetime
from pathlib import Path
from typing import Any

SOULMAP_SKIP_FIELDS = {"_last_updated"}
APPEARANCE_ALIAS_RE = re.compile(
    r"^([^=：:]{1,24})\s*=\s*(?:img\s*=\s*)?(.+)$",
    re.IGNORECASE,
)
STYLE_RE = re.compile(r"^(?:默认)?画风\s*[：:]\s*(.+)$")
META_TOKENS = ("apikey", "API", "可以正常发", "备份", "Kopia", "插件")


def _split_notes(notes: str) -> list[str]:
    return [n.strip() for n in re.split(r"[；;]+", notes or "") if n.strip()]


def _extract_from_notes(notes: str) -> tuple[list[str], str, list[str], list[str]]:
    """Return (alias_names, appearance, styles, leftover_notes)."""
    aliases: list[str] = []
    appearance = ""
    styles: list[str] = []
    leftover: list[str] = []
    for raw in _split_notes(notes):
        part = re.sub(r"^\d+[.、．]\s*", "", raw).strip()
        if not part:
            continue
        sm = STYLE_RE.match(part)
        if sm:
            styles.append(sm.group(1).strip())
            leftover.append(part)
            continue
        am = APPEARANCE_ALIAS_RE.match(part)
        if am:
            alias = am.group(1).strip()
            desc = am.group(2).strip()
            if alias in {"默认画风", "画风"}:
                styles.append(desc)
                leftover.append(part)
                continue
            if any(tok in desc for tok in META_TOKENS):
                leftover.append(part)
                continue
            if alias and alias not in aliases:
                aliases.append(alias)
            if desc and (not appearance or len(desc) > len(appearance)):
                appearance = desc
            leftover.append(part)  # keep original visual notes in extra for companion
            continue
        leftover.append(part)
    return aliases, appearance, styles, leftover


def migrate_entry(uid: str, profile: dict[str, Any]) -> dict[str, Any]:
    names: list[str] = []
    call = str(profile.get("对用户的称呼") or "").strip()
    if call:
        names.append(call)

    notes = str(profile.get("备注") or "")
    aliases, appearance, styles, leftover = _extract_from_notes(notes)
    for a in aliases:
        if a not in names:
            names.append(a)

    extra_bits: list[str] = []
    # Preserve leftover notes (incl. visual alias lines) for companion compatibility
    if leftover:
        extra_bits.append("；".join(leftover))

    # Dump remaining soulmap fields
    legacy: list[str] = []
    for k, v in profile.items():
        if k in SOULMAP_SKIP_FIELDS or k in {"对用户的称呼", "备注"}:
            continue
        vs = str(v or "").strip()
        if vs:
            legacy.append(f"{k}={vs}")
    if legacy:
        extra_bits.append("原soulmap: " + "; ".join(legacy))

    entry = {
        "kind": "persona",
        "scope": "shared",
        "owner_id": "",
        "names": names,
        "appearance": appearance,
        "personality": "",
        "extra": "；".join([b for b in extra_bits if b]),
        "contributors": [],
        "_last_updated": str(profile.get("_last_updated") or datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
        "_migrated_from": "soulmap",
    }
    return entry


def migrate(src: Path, dst: Path) -> dict[str, Any]:
    raw = json.loads(src.read_text(encoding="utf-8-sig"))
    if not isinstance(raw, dict):
        raise ValueError("soulmap source is not an object")
    out: dict[str, Any] = {}
    if dst.exists():
        try:
            existing = json.loads(dst.read_text(encoding="utf-8-sig"))
            if isinstance(existing, dict):
                out.update(existing)
        except Exception:
            pass
    for uid, profile in raw.items():
        if not isinstance(profile, dict):
            continue
        # Don't overwrite richer existing persona_lib entries unless empty
        if uid in out and isinstance(out[uid], dict) and out[uid].get("appearance"):
            continue
        out[uid] = migrate_entry(uid, profile)
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    return out


if __name__ == "__main__":
    import sys

    src = Path(sys.argv[1] if len(sys.argv) > 1 else "user_profiles.json")
    dst = Path(sys.argv[2] if len(sys.argv) > 2 else "personas.json")
    result = migrate(src, dst)
    print(f"migrated {len(result)} entries -> {dst}")
