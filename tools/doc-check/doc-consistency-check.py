#!/usr/bin/env python3
"""TeXTech documentation consistency checker.

Checks:
  1. LoaderNetwork packet IDs vs network-packets.mdc table
  2. Stale "Phase B pending" phrases in docs/ and lang/
  3. zh/en doc file list parity (line count drift >30% warns)
  4. Config.java worldMap* fields mentioned in WebAE dev guide §4 (basic grep)

Exit code 0 = pass or warnings only; 1 = errors found.
"""

from __future__ import print_function

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LOADER = os.path.join(ROOT, "src", "main", "java", "com", "imgood", "textech", "loader", "LoaderNetwork.java")
PACKETS_MDC = os.path.join(ROOT, ".cursor", "rules", "network-packets.mdc")
CONFIG_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "imgood", "textech", "Config.java")
DEV_GUIDE_ZH = os.path.join(ROOT, "docs", "zh", "webae", "开发者手册.md")
DOCS_ZH = os.path.join(ROOT, "docs", "zh")
DOCS_EN = os.path.join(ROOT, "docs", "en")
LANG_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "textech", "lang")

STALE_PATTERNS = [
    re.compile(r"Phase B 待实现"),
    re.compile(r"pending Phase B", re.I),
]

EXCLUDE_LINE_DRIFT = {
    "design/future-development-vision.md",
    "design/未来开发愿景.md",
}

LANG_EN = os.path.join(LANG_DIR, "en_US.lang")
LANG_ZH = os.path.join(LANG_DIR, "zh_CN.lang")
MANUAL_INDEX = os.path.join(ROOT, "src", "main", "resources", "assets", "textech", "manual", "index.json")
MANUAL_CHAPTERS = os.path.join(ROOT, "src", "main", "resources", "assets", "textech", "manual", "chapters")


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def check_loader_vs_mdc():
    errors = []
    loader = read(LOADER)
    mdc = read(PACKETS_MDC)

    reg_ids = sorted(int(m.group(1)) for m in re.finditer(r"registerMessage\([^,]+,\s*[^,]+,\s*(\d+),", loader))
    table_ids = sorted(int(m.group(1)) for m in re.finditer(r"^\|\s*(\d+)\s*\|", mdc, re.M))

    missing_in_mdc = set(reg_ids) - set(table_ids)
    extra_in_mdc = set(table_ids) - set(reg_ids)
    extra_in_mdc.discard(3)  # ID 3 reserved unused in LoaderNetwork
    if missing_in_mdc:
        errors.append("IDs in LoaderNetwork but missing from network-packets.mdc: %s" % sorted(missing_in_mdc))
    if extra_in_mdc:
        errors.append("IDs in network-packets.mdc but not registered in LoaderNetwork: %s" % sorted(extra_in_mdc))

    next_m = re.search(r"\*\*下一个可用 ID = (\d+)\*\*", mdc)
    if next_m:
        expected_next = max(reg_ids) + 1 if reg_ids else 0
        if int(next_m.group(1)) != expected_next:
            errors.append("network-packets.mdc next ID = %s but expected %s" % (next_m.group(1), expected_next))

    return errors


def check_stale_phrases():
    errors = []
    scan_dirs = [
        os.path.join(ROOT, "docs"),
        LANG_DIR,
    ]
    for base in scan_dirs:
        for dirpath, _, filenames in os.walk(base):
            for name in filenames:
                if not (name.endswith(".md") or name.endswith(".lang")):
                    continue
                path = os.path.join(dirpath, name)
                text = read(path)
                for pat in STALE_PATTERNS:
                    if pat.search(text):
                        errors.append("Stale phrase %r in %s" % (pat.pattern, os.path.relpath(path, ROOT)))
    return errors


def rel_md_files(base):
    out = []
    for dirpath, _, filenames in os.walk(base):
        for name in filenames:
            if name.endswith(".md"):
                out.append(os.path.relpath(os.path.join(dirpath, name), base).replace("\\", "/"))
    return sorted(out)


def check_worldmap_config_docs():
    warnings = []
    cfg = read(CONFIG_JAVA)
    guide = read(DEV_GUIDE_ZH)
    for m in re.finditer(r"public static \w+ (webWorldMap\w+)", cfg):
        field = m.group(1)
        # Config field webWorldMapEnabled -> cfg key worldMapEnabled
        key = "worldMap" + field[len("webWorldMap"):]
        if key not in guide and field not in guide:
            warnings.append("worldMap config %s not found in WebAE dev guide §4" % key)
    return warnings


def zh_en_doc_pairs():
    """Map docs/zh/*.md to docs/en/*.md by docs/README.md convention."""
    pairs = []
    for zpath in rel_md_files(DOCS_ZH):
        if zpath.startswith("archive/"):
            continue
        # Same relative path when both trees mirror (webae, developer, etc.)
        epath = zpath
        if zpath == "player/用户手册.md":
            epath = "player/player-guide.md"
        elif zpath == "developer/技术文档.md":
            epath = "developer/technical-documentation.md"
        elif zpath == "developer/Gradle工作流.md":
            epath = "developer/gradle-workflow.md"
        elif zpath == "developer/GTNH版本兼容说明.md":
            epath = "developer/gtnh-version-compatibility.md"
        elif zpath == "developer/临时材质清单.md":
            epath = "developer/temporary-textures.md"
        elif zpath == "developer/未引用lang键清单.md":
            epath = "developer/unreferenced-lang-keys.md"
        elif zpath == "developer/ae-compat-plan-e-remove-legacy.md":
            epath = "developer/ae-compat-plan-e-remove-legacy.md"
        elif zpath == "ai-assistant/开发指南.md":
            epath = "ai-assistant/development-guide.md"
        elif zpath == "design/品牌视觉设计指南.md":
            epath = "design/brand-visual-design-guide.md"
        elif zpath == "design/未来开发愿景.md":
            epath = "design/future-development-vision.md"
        elif zpath == "subsystems/挂索节点系统设计.md":
            epath = "subsystems/grapple-system-design.md"
        elif zpath == "webae/开发者手册.md":
            epath = "webae/developer-guide.md"
        elif zpath == "webae/用户手册.md":
            epath = "webae/user-guide.md"
        elif zpath == "webae/oc-integration.md":
            epath = "webae/oc-integration.md"
        elif zpath == "developer/documentation-map.md":
            epath = "developer/documentation-map.md"
        ep = os.path.join(DOCS_EN, epath)
        if os.path.isfile(ep):
            pairs.append((zpath, epath))
    return pairs


def check_zh_en_parity():
    warnings = []
    for zrel, erel in zh_en_doc_pairs():
        if erel in EXCLUDE_LINE_DRIFT:
            continue
        zp = os.path.join(DOCS_ZH, zrel)
        ep = os.path.join(DOCS_EN, erel)
        zl = len(read(zp).splitlines())
        el = len(read(ep).splitlines())
        if zl == 0:
            continue
        drift = abs(zl - el) / float(zl)
        if drift > 0.30:
            warnings.append("Line drift %.0f%% for zh:%s vs en:%s (zh=%d en=%d)" % (drift * 100, zrel, erel, zl, el))
    return warnings


def parse_lang_keys(path):
    keys = set()
    if not os.path.isfile(path):
        return keys
    for line in read(path).splitlines():
        s = line.strip()
        if not s or s.startswith("#") or "=" not in s:
            continue
        keys.add(s.split("=", 1)[0].strip())
    return keys


def check_lang_parity():
    """en_US.lang vs zh_CN.lang key set parity (warnings)."""
    warnings = []
    if not os.path.isfile(LANG_EN) or not os.path.isfile(LANG_ZH):
        warnings.append("Missing lang file(s): en_US=%s zh_CN=%s" % (os.path.isfile(LANG_EN), os.path.isfile(LANG_ZH)))
        return warnings
    en = parse_lang_keys(LANG_EN)
    zh = parse_lang_keys(LANG_ZH)
    only_en = sorted(en - zh)
    only_zh = sorted(zh - en)
    if only_en:
        sample = ", ".join(only_en[:12])
        more = "" if len(only_en) <= 12 else " ... (+%d)" % (len(only_en) - 12)
        warnings.append("Lang keys in en_US but missing in zh_CN (%d): %s%s" % (len(only_en), sample, more))
    if only_zh:
        sample = ", ".join(only_zh[:12])
        more = "" if len(only_zh) <= 12 else " ... (+%d)" % (len(only_zh) - 12)
        warnings.append("Lang keys in zh_CN but missing in en_US (%d): %s%s" % (len(only_zh), sample, more))
    return warnings


def check_manual_chapters():
    """manual/index.json chapter ids vs chapters/*.json files."""
    import json

    warnings = []
    if not os.path.isfile(MANUAL_INDEX):
        warnings.append("Missing manual index: %s" % MANUAL_INDEX)
        return warnings
    if not os.path.isdir(MANUAL_CHAPTERS):
        warnings.append("Missing manual chapters dir: %s" % MANUAL_CHAPTERS)
        return warnings
    try:
        data = json.loads(read(MANUAL_INDEX))
    except ValueError as e:
        warnings.append("Invalid manual index.json: %s" % e)
        return warnings
    chapters = data.get("chapters") or []
    ids = []
    for ch in chapters:
        if isinstance(ch, dict) and ch.get("id"):
            ids.append(ch["id"])
    id_set = set(ids)
    files = set()
    for name in os.listdir(MANUAL_CHAPTERS):
        if name.endswith(".json"):
            files.add(name[:-5])
    missing_files = sorted(id_set - files)
    orphans = sorted(files - id_set)
    if missing_files:
        warnings.append("index.json chapters missing files: %s" % ", ".join(missing_files))
    if orphans:
        warnings.append("chapters/*.json not listed in index.json: %s" % ", ".join(orphans))
    # duplicate ids
    seen = set()
    dups = []
    for i in ids:
        if i in seen:
            dups.append(i)
        seen.add(i)
    if dups:
        warnings.append("Duplicate chapter ids in index.json: %s" % ", ".join(sorted(set(dups))))
    return warnings


def main():
    errors = []
    warnings = []

    print("=== TeXTech doc-consistency-check ===\n")

    errors.extend(check_loader_vs_mdc())
    errors.extend(check_stale_phrases())
    warnings.extend(check_zh_en_parity())
    warnings.extend(check_worldmap_config_docs())
    warnings.extend(check_lang_parity())
    warnings.extend(check_manual_chapters())

    if warnings:
        print("WARNINGS:")
        for w in warnings:
            print("  -", w)
        print()

    if errors:
        print("ERRORS:")
        for e in errors:
            print("  -", e)
        print("\nFAILED")
        return 1

    print("OK (warnings: %d)" % len(warnings))
    return 0


if __name__ == "__main__":
    sys.exit(main())
