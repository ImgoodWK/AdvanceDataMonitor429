import argparse
import shutil
import time
from pathlib import Path
from typing import Tuple


IMPORT_LINE = "from .textech_photo_route import explicit_tt_request, resolve_reference_generation_intent"
IMPORT_ANCHOR = "from .constants import DEFAULT_NATURAL_LANGUAGE_PHOTO_EXTRA_PROMPT\n"

INTENT_OLD = """        intent = self._natural_language_photo_intent(text, has_reference=has_reference, directed=directed)
        if not intent:
"""
INTENT_NEW = """        explicit_tt = explicit_tt_request(event, text)
        intent = self._natural_language_photo_intent(
            text,
            has_reference=has_reference,
            directed=bool(directed or explicit_tt),
        )
        intent = resolve_reference_generation_intent(
            intent,
            text=text,
            has_reference=has_reference,
            explicit_tt=explicit_tt,
        )
        if not intent:
"""

GATE_START = "        # >>> [tt-gate] 群聊生图必须 tt 开头,否则提醒并拦截 >>>\n"
GATE_END = "        # <<< [tt-gate] 群聊生图必须 tt 开头 <<<\n"
REFERENCE_GATE_END = "        # <<< [tt-reference-gate] 参考图生图必须显式 tt <<<\n"
GATE_NEW = """        # >>> [tt-gate] 群聊生图必须 tt 开头,否则提醒并拦截 >>>
        try:
            _grp_id = ""
            _gid_getter = getattr(event, "get_group_id", None)
            if callable(_gid_getter):
                _grp_id = str(_gid_getter() or "")
        except Exception:
            _grp_id = ""
        if _grp_id and not explicit_tt:
            logger.info(
                "[PrivateCompanion] 群聊生图未以tt开头,已拦截并提醒: group=%s text=%s",
                _single_line(_grp_id, 40),
                _single_line(text, 120),
            )
            await self._reply(event, "想生图的话，用「tt」开头跟我说哦，比如「tt 画一只小狐狸」～")
            try:
                event.stop_event()
            except Exception:
                pass
            return True
        # <<< [tt-gate] 群聊生图必须 tt 开头 <<<
        if has_reference and str(intent.get("kind") or "") == "edit" and not explicit_tt:
            logger.info(
                "[PrivateCompanion] 参考图生图未以tt开头,已拦截: user=%s text=%s",
                _single_line(user_id, 40),
                _single_line(text, 120),
            )
            await self._reply(event, "要用参考图生图，请用「tt」开头并带上提示词，例如「tt 把背景改成星空」～")
            try:
                event.stop_event()
            except Exception:
                pass
            return True
        # <<< [tt-reference-gate] 参考图生图必须显式 tt <<<
"""


def apply_overlay(source: str) -> Tuple[str, bool]:
    text = source.replace("\r\n", "\n")
    changed = False
    if IMPORT_LINE not in text:
        if text.count(IMPORT_ANCHOR) != 1:
            raise ValueError("command_handlers import anchor not found exactly once")
        text = text.replace(IMPORT_ANCHOR, IMPORT_ANCHOR + IMPORT_LINE + "\n", 1)
        changed = True
    if "explicit_tt = explicit_tt_request(event, text)" not in text:
        if text.count(INTENT_OLD) != 1:
            raise ValueError("natural-language photo intent anchor not found exactly once")
        text = text.replace(INTENT_OLD, INTENT_NEW, 1)
        changed = True
    gate_start = text.find(GATE_START)
    gate_end = text.find(REFERENCE_GATE_END, gate_start + len(GATE_START)) if gate_start >= 0 else -1
    gate_end_marker = REFERENCE_GATE_END
    if gate_end < 0 and gate_start >= 0:
        gate_end = text.find(GATE_END, gate_start + len(GATE_START))
        gate_end_marker = GATE_END
    if gate_start < 0 or gate_end < 0:
        raise ValueError("tt gate markers not found")
    gate_end += len(gate_end_marker)
    if text[gate_start:gate_end] != GATE_NEW:
        text = text[:gate_start] + GATE_NEW + text[gate_end:]
        changed = True
    return text, changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Apply TeXTech tt reference-generation overlay")
    parser.add_argument("target", type=Path)
    parser.add_argument("--apply", action="store_true", help="write the patched file and create a timestamped backup")
    args = parser.parse_args()
    original = args.target.read_text(encoding="utf-8-sig")
    patched, changed = apply_overlay(original)
    if not args.apply:
        print("would-change" if changed else "already-applied")
        return 0
    if not changed:
        print("already-applied")
        return 0
    backup = args.target.with_name(f"{args.target.name}.bak.tt_reference_{int(time.time())}")
    shutil.copy2(args.target, backup)
    with args.target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(patched)
    print(f"applied backup={backup.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
