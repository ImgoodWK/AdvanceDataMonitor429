"""Build the sparse ADM GUI atlas from the approved local Meowa outputs.

The source sheets are intentionally kept in ``.workspace`` and are never
published.  This deterministic promotion step creates two transparent,
contract-oriented control sheets (complete fixed-aspect buttons and
underline fields/icons), validates their manifests, then writes the compact
512x512 runtime atlas plus a tracked UV/source-hash ledger.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
CHROME = ROOT / (
    ".workspace/meowa/adm-gui-20260731/recovery/chrome/"
    "job_a011a4a62aad4357b3c948bd89ed33fb/ui_output.png"
)
LEGACY_CONTROLS = ROOT / (
    ".workspace/meowa/adm-gui-20260731/recovery/controls/"
    "job_6838940dbb2940e8a83ba4e288e5271b/ui_output.png"
)
GENERATED_ROOT = ROOT / ".workspace/adm-gui-batch1-assets"
BUTTON_SHEET = GENERATED_ROOT / "buttons/button_families.png"
FIELD_ICON_SHEET = GENERATED_ROOT / "fields-icons/underline_fields_and_icons.png"
OUTPUT = ROOT / "src/main/resources/assets/textech/textures/gui/adm_ui_atlas.png"
LAYOUT_OUTPUT = ROOT / "tools/gui/adm_ui_atlas.layout.json"

ATLAS_SIZE = 512
SOURCE_SIZE = (2048, 2048)
EXPECTED_CHROME_SHA256 = "a3889faf589b9d1832e4ba8707169b171638eed7a2e1b6dcc2a178195d006ee0"
EXPECTED_CONTROLS_SHA256 = "ce3b712acec6d3a377dc924656c61a484d0d508f3fdb8931edfd5ac81435242d"

STATES = ("normal", "hover", "pressed", "disabled")
BUTTON_WIDTHS = (20, 50, 60, 80, 100, 200, 240)
BUTTON_SOURCE_ROWS = {
    "normal": (65, 179),
    "hover": (189, 302),
    "pressed": (310, 423),
    "disabled": (433, 547),
}
FIELD_SOURCE_ROWS = {
    "normal": (581, 686),
    "focused": (696, 801),
    "invalid": (811, 918),
    "disabled": (927, 1032),
}
ICON_NAMES = (
    "save",
    "cancel",
    "back",
    "previous",
    "next",
    "add",
    "delete",
    "edit",
    "search",
    "refresh",
    "settings",
    "import",
    "export",
    "send",
    "bind",
    "copy",
    "menu",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_open(path: Path, expected_hash: str) -> Image.Image:
    if not path.is_file():
        raise SystemExit(f"Missing approved local source: {path}")
    actual_hash = sha256(path)
    if actual_hash != expected_hash:
        raise SystemExit(f"Unexpected source SHA256: {path}: {actual_hash}")
    image = Image.open(path).convert("RGBA")
    if image.size != SOURCE_SIZE:
        raise SystemExit(f"Unexpected source size: {path}: {image.size}")
    return image


def crop(source: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
    return source.crop(box)


def fit(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Uniform nearest-neighbour fit with transparent padding."""
    target_width, target_height = size
    scale = min(target_width / float(image.width), target_height / float(image.height))
    scaled = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.NEAREST,
    )
    result = Image.new("RGBA", size, (0, 0, 0, 0))
    result.alpha_composite(
        scaled,
        ((target_width - scaled.width) // 2, (target_height - scaled.height) // 2),
    )
    return result


def opaque_glass(image: Image.Image, size: tuple[int, int], alpha: int) -> Image.Image:
    result = image.resize(size, Image.Resampling.LANCZOS)
    result.putalpha(Image.new("L", size, alpha))
    return result


def tile_center(center: Image.Image, width: int, height: int) -> Image.Image:
    result = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    if width <= 0:
        return result
    center = center.resize((max(1, center.width), height), Image.Resampling.NEAREST)
    for x in range(0, width, center.width):
        part_width = min(center.width, width - x)
        result.alpha_composite(center.crop((0, 0, part_width, height)), (x, 0))
    return result


def complete_button(controls: Image.Image, state: str, width: int) -> Image.Image:
    """Offline three-piece assembly; runtime receives one complete button."""
    y1, y2 = BUTTON_SOURCE_ROWS[state]
    source = crop(controls, (281, y1, 812, y2)).resize((94, 20), Image.Resampling.NEAREST)
    cap = min(10, width // 2)
    left = source.crop((0, 0, 20, 20)).resize((cap, 20), Image.Resampling.NEAREST)
    right = source.crop((74, 0, 94, 20)).resize((cap, 20), Image.Resampling.NEAREST)
    middle_width = max(0, width - cap * 2)
    center = tile_center(source.crop((45, 0, 49, 20)), middle_width, 20)
    result = Image.new("RGBA", (width, 20), (0, 0, 0, 0))
    result.alpha_composite(left, (0, 0))
    if middle_width:
        result.alpha_composite(center, (cap, 0))
    result.alpha_composite(right, (width - cap, 0))
    return result


def field_parts(controls: Image.Image, state: str) -> tuple[Image.Image, Image.Image, Image.Image]:
    y1, y2 = FIELD_SOURCE_ROWS[state]
    # Side strokes are uniformly reduced from the complete longest field.
    left = fit(crop(controls, (267, y1, 292, y2)), (3, 20))
    right = fit(crop(controls, (863, y1, 888, y2)), (3, 20))
    # The 480px source is a centred crop from the longest generated underline.
    # Only its thickness is reduced; its horizontal pixels are never stretched.
    longest = crop(controls, (267, y2 - 18, 888, y2))
    crop_x = (longest.width - 480) // 2
    bottom = longest.crop((crop_x, 0, crop_x + 480, longest.height))
    bottom = bottom.resize((480, 3), Image.Resampling.NEAREST)
    return left, right, bottom


def draw_icon(name: str, primary: tuple[int, int, int, int], secondary: tuple[int, int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    p = primary
    s = secondary

    if name == "save":
        draw.rectangle((3, 2, 12, 13), outline=p, width=2)
        draw.rectangle((5, 3, 10, 6), fill=s)
        draw.rectangle((5, 9, 10, 12), outline=p)
    elif name == "cancel":
        draw.line((3, 3, 12, 12), fill=p, width=2)
        draw.line((12, 3, 3, 12), fill=s, width=2)
    elif name in ("back", "previous", "next"):
        reverse = name != "next"
        points = ((11, 3), (5, 8), (11, 13)) if reverse else ((5, 3), (11, 8), (5, 13))
        draw.line(points, fill=p, width=2, joint="curve")
        if name == "back":
            draw.line((5, 8, 13, 8), fill=s, width=2)
    elif name == "add":
        draw.line((8, 3, 8, 13), fill=p, width=2)
        draw.line((3, 8, 13, 8), fill=s, width=2)
    elif name == "delete":
        draw.rectangle((4, 5, 11, 13), outline=p)
        draw.line((3, 4, 12, 4), fill=s, width=2)
        draw.line((6, 2, 9, 2), fill=p, width=2)
    elif name == "edit":
        draw.line((3, 12, 11, 4), fill=p, width=3)
        draw.polygon(((11, 3), (13, 5), (12, 2)), fill=s)
        draw.line((3, 12, 6, 12), fill=s)
    elif name == "search":
        draw.ellipse((2, 2, 10, 10), outline=p, width=2)
        draw.line((9, 9, 13, 13), fill=s, width=2)
    elif name == "refresh":
        draw.arc((2, 2, 13, 13), 35, 310, fill=p, width=2)
        draw.polygon(((11, 2), (14, 3), (12, 6)), fill=s)
    elif name == "settings":
        draw.ellipse((3, 3, 12, 12), outline=p, width=2)
        draw.ellipse((6, 6, 9, 9), fill=s)
        for x1, y1, x2, y2 in ((7, 1, 8, 4), (7, 11, 8, 14), (1, 7, 4, 8), (11, 7, 14, 8)):
            draw.rectangle((x1, y1, x2, y2), fill=p)
    elif name in ("import", "export"):
        outward = name == "export"
        draw.rectangle((2, 4, 9, 13), outline=p)
        if outward:
            draw.line((7, 8, 13, 2), fill=s, width=2)
            draw.line((9, 2, 13, 2, 13, 6), fill=s, width=2)
        else:
            draw.line((13, 2, 7, 8), fill=s, width=2)
            draw.line((7, 4, 7, 8, 11, 8), fill=s, width=2)
    elif name == "send":
        draw.polygon(((2, 3), (14, 8), (2, 13), (5, 8)), outline=p)
        draw.line((5, 8, 12, 8), fill=s)
    elif name == "bind":
        draw.arc((1, 4, 9, 12), 80, 280, fill=p, width=2)
        draw.arc((7, 4, 15, 12), 260, 100, fill=s, width=2)
        draw.line((5, 8, 11, 8), fill=p, width=2)
    elif name == "copy":
        draw.rectangle((2, 2, 10, 10), outline=s)
        draw.rectangle((5, 5, 13, 13), outline=p, width=2)
    elif name == "menu":
        for y in (4, 8, 12):
            draw.line((3, y, 13, y), fill=p if y != 8 else s, width=2)
    else:
        raise ValueError(name)
    return image


def write_manifest(directory: Path, output: Path, kind: str) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    manifest = {
        "status": "success",
        "generator": "tools/gui/build_adm_sparse_atlas.py",
        "kind": kind,
        "outputs": [
            {
                "type": "media",
                "path": str(output.relative_to(ROOT)).replace("/", "\\"),
                "mime_type": "image/png",
                "sha256": sha256(output),
            }
        ],
    }
    (directory / "final_outputs.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def build_control_sheets(controls: Image.Image) -> tuple[dict[str, tuple[int, int, int, int]], dict[str, tuple[int, int, int, int]]]:
    button_sheet = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    button_layout: dict[str, tuple[int, int, int, int]] = {}
    for state_index, state in enumerate(STATES):
        block_y = state_index * 44
        positions = {
            240: (0, block_y),
            200: (244, block_y),
            100: (0, block_y + 22),
            80: (104, block_y + 22),
            60: (188, block_y + 22),
            50: (252, block_y + 22),
            20: (306, block_y + 22),
        }
        for width in BUTTON_WIDTHS:
            x, y = positions[width]
            image = complete_button(controls, state, width)
            button_sheet.alpha_composite(image, (x, y))
            button_layout[f"button.{state}.{width}"] = (x, y, width, 20)
    BUTTON_SHEET.parent.mkdir(parents=True, exist_ok=True)
    button_sheet.save(BUTTON_SHEET, optimize=True)
    write_manifest(BUTTON_SHEET.parent, BUTTON_SHEET, "complete-fixed-aspect-button-families")

    field_sheet = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    field_layout: dict[str, tuple[int, int, int, int]] = {}
    for state_index, state in enumerate(FIELD_SOURCE_ROWS):
        left, right, bottom = field_parts(controls, state)
        bottom_y = state_index * 5
        side_y = state_index * 22
        field_sheet.alpha_composite(bottom, (0, bottom_y))
        field_sheet.alpha_composite(left, (482, side_y))
        field_sheet.alpha_composite(right, (487, side_y))
        field_layout[f"field.{state}.bottom"] = (0, bottom_y, 480, 3)
        field_layout[f"field.{state}.left"] = (482, side_y, 3, 20)
        field_layout[f"field.{state}.right"] = (487, side_y, 3, 20)

    palettes = (
        ((7, 236, 246, 255), (1, 97, 243, 255)),
        ((120, 255, 255, 255), (38, 150, 255, 255)),
    )
    for state_index, (primary, secondary) in enumerate(palettes):
        for icon_index, name in enumerate(ICON_NAMES):
            x = (icon_index % 8) * 16
            y = 96 + state_index * 48 + (icon_index // 8) * 16
            icon = draw_icon(name, primary, secondary)
            field_sheet.alpha_composite(icon, (x, y))
            field_layout[f"icon.{state_index}.{name}"] = (x, y, 16, 16)

    FIELD_ICON_SHEET.parent.mkdir(parents=True, exist_ok=True)
    field_sheet.save(FIELD_ICON_SHEET, optimize=True)
    write_manifest(FIELD_ICON_SHEET.parent, FIELD_ICON_SHEET, "underline-field-states-and-semantic-icons")
    return button_layout, field_layout


def build_runtime_atlas(chrome: Image.Image, controls: Image.Image) -> dict[str, object]:
    button_layout, field_layout = build_control_sheets(controls)
    button_sheet = Image.open(BUTTON_SHEET).convert("RGBA")
    field_sheet = Image.open(FIELD_ICON_SHEET).convert("RGBA")
    atlas = Image.new("RGBA", (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))
    layout: dict[str, object] = {
        "atlas_size": ATLAS_SIZE,
        "chrome_sha256": EXPECTED_CHROME_SHA256,
        "legacy_controls_sha256": EXPECTED_CONTROLS_SHA256,
        "button_sheet_sha256": sha256(BUTTON_SHEET),
        "field_icon_sheet_sha256": sha256(FIELD_ICON_SHEET),
        "main_background_alpha": 56,
        "section_background_alpha": 72,
        "button_families": list(BUTTON_WIDTHS),
        "button_states": list(STATES),
        "field_states": list(FIELD_SOURCE_ROWS),
        "semantic_icons": list(ICON_NAMES),
    }

    def place(name: str, image: Image.Image, xy: tuple[int, int]) -> None:
        atlas.alpha_composite(image, xy)
        layout[name] = {"u": xy[0], "v": xy[1], "width": image.width, "height": image.height}

    # Outer frame: four independent corners and a dark cover-cropped background sample.
    place("mainCornerTL", fit(crop(chrome, (53, 39, 384, 369)), (22, 22)), (0, 0))
    place("mainCornerTR", fit(crop(chrome, (1663, 39, 1993, 369)), (22, 22)), (24, 0))
    place("mainCornerBL", fit(crop(chrome, (53, 1578, 383, 1908)), (22, 22)), (0, 24))
    place("mainCornerBR", fit(crop(chrome, (1663, 1578, 1994, 1908)), (22, 22)), (24, 24))
    place("mainBackground", opaque_glass(crop(chrome, (610, 570, 880, 840)), (64, 64), 56), (264, 0))

    # Inner frame comes from the independent middle-lower panel, while its
    # brighter glass uses the approved top-right sample.
    place("sectionCornerTL", fit(crop(chrome, (671, 992, 769, 1085)), (14, 14)), (0, 66))
    place("sectionCornerTR", fit(crop(chrome, (1278, 992, 1375, 1084)), (14, 14)), (16, 66))
    place("sectionCornerBL", fit(crop(chrome, (671, 1283, 769, 1376)), (14, 14)), (0, 82))
    place("sectionCornerBR", fit(crop(chrome, (1277, 1283, 1375, 1376)), (14, 14)), (16, 82))
    place("sectionBackground", opaque_glass(crop(chrome, (1160, 570, 1430, 840)), (64, 64), 72), (156, 66))

    place("titleOrnament", fit(crop(chrome, (560, 285, 1488, 465)), (160, 12)), (222, 66))
    place("footerOrnament", fit(crop(chrome, (560, 1450, 1488, 1590)), (160, 8)), (222, 80))

    # Copy each complete button family into the runtime atlas. No runtime
    # three-slice or tiling is permitted for ADM controls.
    for state_index, state in enumerate(STATES):
        block_y = 140 + state_index * 44
        runtime_positions = {
            240: (0, block_y),
            200: (244, block_y),
            100: (0, block_y + 22),
            80: (104, block_y + 22),
            60: (188, block_y + 22),
            50: (252, block_y + 22),
            20: (306, block_y + 22),
        }
        for width in BUTTON_WIDTHS:
            source_box = button_layout[f"button.{state}.{width}"]
            image = crop(button_sheet, (source_box[0], source_box[1], source_box[0] + width, source_box[1] + 20))
            place(f"button.{state}.{width}", image, runtime_positions[width])

    # Underline fields: a 480px longest bottom source plus left/right strokes.
    for state_index, state in enumerate(FIELD_SOURCE_ROWS):
        bottom_box = field_layout[f"field.{state}.bottom"]
        left_box = field_layout[f"field.{state}.left"]
        right_box = field_layout[f"field.{state}.right"]
        bottom = crop(field_sheet, (bottom_box[0], bottom_box[1], bottom_box[0] + 480, bottom_box[1] + 3))
        left = crop(field_sheet, (left_box[0], left_box[1], left_box[0] + 3, left_box[1] + 20))
        right = crop(field_sheet, (right_box[0], right_box[1], right_box[0] + 3, right_box[1] + 20))
        place(f"field.{state}.bottom", bottom, (0, 320 + state_index * 5))
        place(f"field.{state}.left", left, (482, 320 + state_index * 22))
        place(f"field.{state}.right", right, (487, 320 + state_index * 22))

    # Normal and hover semantic icon grids, eight columns per row.
    for visual_state in range(2):
        for icon_index, name in enumerate(ICON_NAMES):
            source_box = field_layout[f"icon.{visual_state}.{name}"]
            icon = crop(
                field_sheet,
                (source_box[0], source_box[1], source_box[0] + 16, source_box[1] + 16),
            )
            x = (icon_index % 8) * 16
            y = 350 + visual_state * 48 + (icon_index // 8) * 16
            place(f"icon.{visual_state}.{name}", icon, (x, y))

    # Exact-size secondary controls retained from the approved control sheet.
    place("slotNormal", fit(crop(controls, (1207, 165, 1425, 353)), (18, 18)), (300, 452))
    place("scrollTrack", fit(crop(controls, (73, 1073, 169, 1592)), (8, 42)), (320, 452))
    place("scrollThumb", fit(crop(controls, (199, 1090, 293, 1588)), (8, 20)), (330, 452))
    place("toggleOff", fit(crop(controls, (765, 1113, 858, 1206)), (14, 14)), (342, 452))
    place("toggleOn", fit(crop(controls, (881, 1113, 974, 1206)), (14, 14)), (358, 452))
    place("toggleDisabled", fit(crop(controls, (997, 1113, 1090, 1206)), (14, 14)), (374, 452))
    place("checkOff", fit(crop(controls, (765, 1217, 858, 1310)), (14, 14)), (342, 468))
    place("checkOn", fit(crop(controls, (881, 1217, 974, 1310)), (14, 14)), (358, 468))
    place("checkDisabled", fit(crop(controls, (997, 1217, 1090, 1310)), (14, 14)), (374, 468))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT, optimize=True)
    layout["atlas_sha256"] = sha256(OUTPUT)
    return layout


def validate_manifest(path: Path, expected_output: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("status") != "success" or len(data.get("outputs", [])) != 1:
        raise SystemExit(f"Invalid final_outputs.json: {path}")
    output = data["outputs"][0]
    if output.get("mime_type") != "image/png" or output.get("sha256") != sha256(expected_output):
        raise SystemExit(f"Manifest/output mismatch: {path}")


def main() -> None:
    chrome = checked_open(CHROME, EXPECTED_CHROME_SHA256)
    controls = checked_open(LEGACY_CONTROLS, EXPECTED_CONTROLS_SHA256)
    layout = build_runtime_atlas(chrome, controls)
    validate_manifest(BUTTON_SHEET.parent / "final_outputs.json", BUTTON_SHEET)
    validate_manifest(FIELD_ICON_SHEET.parent / "final_outputs.json", FIELD_ICON_SHEET)
    LAYOUT_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    LAYOUT_OUTPUT.write_text(json.dumps(layout, indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "atlas": str(OUTPUT),
                "atlas_sha256": layout["atlas_sha256"],
                "layout": str(LAYOUT_OUTPUT),
                "button_sheet_sha256": layout["button_sheet_sha256"],
                "field_icon_sheet_sha256": layout["field_icon_sheet_sha256"],
            }
        )
    )


if __name__ == "__main__":
    main()
