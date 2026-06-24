#!/usr/bin/env python3
"""Generate ADM loom cell + weave amplifier textures from AE2 references."""

from __future__ import annotations

import json
import math
import os
import shutil
from typing import Iterable, List, Tuple

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(
    ROOT,
    "src/main/resources/assets/advancedatamonitor/textures/review/reference_ae2",
)
OUT = os.path.join(
    ROOT,
    "src/main/resources/assets/advancedatamonitor/textures/review/generated",
)

FRAME_SIZE = 16
FRAME_COUNT = 20  # match AE2 Universe cell strip (16×320)

# Weave icon — diamond ring (clockwise) + center hub. Only these pixels animate.
WEAVE_RING = [
    (8, 6), (9, 6), (10, 7), (10, 9), (9, 10), (8, 10),
    (7, 10), (6, 9), (6, 7), (7, 6), (7, 8), (9, 8),
]
WEAVE_HUB = (8, 8)
# Head advances 0.6 ring-steps per frame (12 / 20); frame 19 → 0 wraps cleanly.
WEAVE_TRAIL_OFFSET = 4.0

# AE2-style cyan ramp: ambient → base → bright → hot (white core)
WEAVE_LEVELS = [
    (0, 95, 120, 255),
    (0, 155, 190, 255),
    (40, 215, 245, 255),
    (150, 255, 255, 255),
]
CYAN = (0, 255, 255, 255)
DUST_COLORS = [
    (120, 90, 70, 255),
    (140, 105, 80, 255),
    (100, 80, 60, 255),
    (150, 115, 85, 255),
]
FORM_COLORS = [
    (0, 200, 255, 255),
    (0, 170, 230, 255),
    (80, 220, 255, 255),
    (0, 140, 210, 255),
]
FLOW_COLORS = [
    (30, 120, 255, 255),
    (60, 160, 255, 255),
    (10, 90, 220, 255),
    (90, 190, 255, 255),
]
SOURCE_COLORS = [
    (180, 40, 200, 255),
    (140, 30, 180, 255),
    (220, 80, 240, 255),
    (100, 20, 160, 255),
]


def load(name: str) -> Image.Image:
    path = os.path.join(REF, name)
    img = Image.open(path)
    return img.convert("RGBA")


def save(img: Image.Image, name: str) -> None:
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name))


def write_mcmeta(stem: str, frametime: int = 2, interpolate: bool = False) -> None:
    animation = {"frametime": frametime}
    if interpolate:
        animation["interpolate"] = True
    meta = {"animation": animation}
    path = os.path.join(OUT, stem + ".png.mcmeta")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
        f.write("\n")


def replace_center(img: Image.Image, pixels: Iterable[Tuple[int, int, Tuple[int, int, int, int]]]) -> Image.Image:
    out = img.copy()
    px = out.load()
    for x, y, rgba in pixels:
        if 0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE:
            px[x, y] = rgba
    return out


def compose_weave_card(speed_name: str, out_name: str, tier: str) -> None:
    """Card body from BasicCard + connector from Speed card + custom weave icon."""
    basic = load("ItemMaterial.BasicCard.png")
    speed = load(speed_name)
    out = basic.copy()
    bpx = basic.load()
    spx = speed.load()
    px = out.load()
    # copy gold finger connector from speed/super-speed card (left 5 columns)
    for y in range(FRAME_SIZE):
        for x in range(5):
            px[x, y] = spx[x, y]
    # restore card face from basic card (clear speed chevrons)
    for y in range(FRAME_SIZE):
        for x in range(5, FRAME_SIZE):
            px[x, y] = bpx[x, y]
    weave_pixels = [
        (7, 6, CYAN), (8, 6, CYAN), (9, 6, CYAN),
        (6, 7, CYAN), (10, 7, CYAN),
        (7, 8, CYAN), (8, 8, (180, 255, 255, 255)), (9, 8, CYAN),
        (6, 9, CYAN), (10, 9, CYAN),
        (7, 10, CYAN), (8, 10, CYAN), (9, 10, CYAN),
    ]
    if tier == "super":
        for y in range(FRAME_SIZE):
            for x in range(5, FRAME_SIZE):
                px[x, y] = spx[x, y]
        for y in range(FRAME_SIZE):
            for x in range(5):
                px[x, y] = spx[x, y]
        weave_pixels.extend([
            (5, 8, (255, 180, 60, 255)), (11, 8, (255, 180, 60, 255)),
            (8, 5, (255, 180, 60, 255)), (8, 11, (255, 180, 60, 255)),
        ])
    for x, y, c in weave_pixels:
        px[x, y] = c
    save(out, out_name)
    return out


def ring_distance(idx: float, head: float, count: int) -> float:
    d = abs(idx - head) % count
    return min(d, count - d)


def weave_energy_strength(ring_idx: float, head: float, count: int) -> float:
    """Primary spark + trailing echo; periodic in head so loop is seamless."""
    trail_head = (head - WEAVE_TRAIL_OFFSET) % count
    lead = strength_from_distance(ring_distance(ring_idx, head, count))
    trail = strength_from_distance(ring_distance(ring_idx, trail_head, count)) * 0.42
    ambient = 0.10
    return min(1.0, ambient + lead + trail)


def strength_from_distance(d: float) -> float:
    if d >= 3.2:
        return 0.0
    return max(0.0, math.cos(d * math.pi / 3.2))


def rgba_from_strength(strength: float) -> Tuple[int, int, int, int]:
    strength = max(0.0, min(1.0, strength))
    if strength <= 0.25:
        t = strength / 0.25
        lo, hi = WEAVE_LEVELS[0], WEAVE_LEVELS[1]
    elif strength <= 0.55:
        t = (strength - 0.25) / 0.30
        lo, hi = WEAVE_LEVELS[1], WEAVE_LEVELS[2]
    elif strength <= 0.82:
        t = (strength - 0.55) / 0.27
        lo, hi = WEAVE_LEVELS[2], WEAVE_LEVELS[3]
    else:
        t = (strength - 0.82) / 0.18
        lo, hi = WEAVE_LEVELS[3], (255, 255, 255, 255)
    return tuple(int(lo[i] + (hi[i] - lo[i]) * t) for i in range(4))


def weave_frame_colors(frame_idx: int) -> dict:
    """
    20-frame weave animation (seamless loop):

    Energy travels clockwise around the diamond ring (12 nodes). Each frame the
    head advances 0.6 nodes; after frame 19 the head wraps to 0, matching frame 0.

    Per frame:
      - Lead spark on the ring (hot white-cyan)
      - Trailing echo ~4 nodes behind (dimmer, AE singularity-style tail)
      - Hub at (8,8) follows the average of its four neighbors

    Frame guide (head position on ring, approximate hot pixel):
      0:(8,6)  1:(9,6)  2:(10,7)  3:(10,9)  4:(9,10)  5:(8,10)
      6:(7,10) 7:(6,9)  8:(6,7)  9:(7,6)  10:(7,8) 11:(9,8)
      12-19: second lap with sub-step interpolation between nodes
    """
    count = len(WEAVE_RING)
    head = ((frame_idx % FRAME_COUNT) * count / FRAME_COUNT) % count
    colors = {}
    strengths = {}
    for i, pos in enumerate(WEAVE_RING):
        s = weave_energy_strength(float(i), head, count)
        strengths[i] = s
        colors[pos] = rgba_from_strength(s)

    hub_strength = min(
        1.0,
        (strengths[WEAVE_RING.index((7, 8))] + strengths[WEAVE_RING.index((9, 8))]) / 2 * 0.88 + 0.12,
    )
    colors[WEAVE_HUB] = rgba_from_strength(hub_strength)
    return colors


def apply_weave_animation(base: Image.Image, frame_idx: int) -> Image.Image:
    frame = base.copy()
    px = frame.load()
    for (x, y), rgba in weave_frame_colors(frame_idx).items():
        px[x, y] = rgba
    return frame


def verify_weave_loop() -> None:
    """Frame 0 must match virtual frame FRAME_COUNT; each step is uniform."""
    count = len(WEAVE_RING)
    step = count / FRAME_COUNT
    head_last = (FRAME_COUNT - 1) * count / FRAME_COUNT
    assert abs(((head_last + step) % count)) < 1e-9

    c0 = weave_frame_colors(0)
    c_wrap = weave_frame_colors(FRAME_COUNT)
    for pos in WEAVE_RING + [WEAVE_HUB]:
        assert c0[pos] == c_wrap[pos], "wrap mismatch at %s" % (pos,)

    def color_delta(frame_a: int, frame_b: int) -> int:
        a = weave_frame_colors(frame_a)
        b = weave_frame_colors(frame_b)
        total = 0
        for pos in WEAVE_RING + [WEAVE_HUB]:
            for i in range(3):
                total += abs(a[pos][i] - b[pos][i])
        return total

    d_wrap = color_delta(19, 0)
    steps = [color_delta(i, i + 1) for i in range(FRAME_COUNT - 1)]
    assert min(steps) <= d_wrap <= max(steps), "wrap step out of range: %s not in [%s,%s]" % (
        d_wrap,
        min(steps),
        max(steps),
    )


def build_weave_amplifier_animation(speed_name: str, out_name: str, tier: str) -> None:
    """HD 128px cosmic animation — see tools/generate_weave_amplifier_cosmic.py."""
    import subprocess
    import sys

    script = os.path.join(ROOT, "tools/generate_weave_amplifier_cosmic.py")
    subprocess.check_call([sys.executable, script])


def frame_from_housing(housing: Image.Image, frame_idx: int, palette: List[Tuple[int, int, int, int]]) -> Image.Image:
    frame = housing.copy()
    px = frame.load()
    c1 = palette[frame_idx % len(palette)]
    c2 = palette[(frame_idx + 1) % len(palette)]
    c3 = palette[(frame_idx + 2) % len(palette)]
    # AE2 cell core window ~ (5..10, 4..10)
    core = [
        (6, 5, c2), (7, 5, c1), (8, 5, c1), (9, 5, c2),
        (5, 6, c2), (6, 6, c3), (7, 6, c1), (8, 6, c1), (9, 6, c3), (10, 6, c2),
        (5, 7, c1), (6, 7, c1), (7, 7, c2), (8, 7, c2), (9, 7, c1), (10, 7, c1),
        (5, 8, c1), (6, 8, c2), (7, 8, c3), (8, 8, c3), (9, 8, c2), (10, 8, c1),
        (5, 9, c2), (6, 9, c3), (7, 9, c1), (8, 9, c1), (9, 9, c3), (10, 9, c2),
        (6, 10, c2), (7, 10, c1), (8, 10, c1), (9, 10, c2),
    ]
    for x, y, c in core:
        px[x, y] = c
    return frame


def make_dust_frame(housing: Image.Image, frame_idx: int) -> Image.Image:
    frame = housing.copy()
    px = frame.load()
    shift = frame_idx % 4
    dust_dots = [
        (6 + shift % 2, 6), (8, 5 + shift % 3), (9, 7), (7, 9 - shift % 2), (8, 8),
    ]
    for i, (x, y) in enumerate(dust_dots):
        color = DUST_COLORS[(frame_idx + i) % len(DUST_COLORS)]
        if 0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE:
            px[x, y] = color
    return frame


def make_form_frame(housing: Image.Image, frame_idx: int) -> Image.Image:
    frame = housing.copy()
    px = frame.load()
    rot = frame_idx % 4
    cube_edges = [
        [(6, 6), (9, 6), (9, 9), (6, 9), (6, 6)],
        [(7, 5), (10, 7), (8, 10), (5, 8), (7, 5)],
        [(5, 7), (8, 5), (10, 8), (7, 10), (5, 7)],
        [(6, 5), (10, 6), (9, 10), (5, 9), (6, 5)],
    ][rot]
    color = FORM_COLORS[frame_idx % len(FORM_COLORS)]
    for i in range(len(cube_edges) - 1):
        x1, y1 = cube_edges[i]
        x2, y2 = cube_edges[i + 1]
        px[x1, y1] = color
        px[x2, y2] = color
    px[8, 8] = (220, 255, 255, 255)
    return frame


def make_flow_frame(housing: Image.Image, frame_idx: int) -> Image.Image:
    frame = housing.copy()
    px = frame.load()
    level = frame_idx % 5
    for y in range(7 + level, 11):
        for x in range(6, 10):
            px[x, y] = FLOW_COLORS[(x + y + frame_idx) % len(FLOW_COLORS)]
    px[7, 6 + level] = (200, 230, 255, 255)
    px[8, 6 + level] = (200, 230, 255, 255)
    return frame


def make_source_frame(housing: Image.Image, frame_idx: int) -> Image.Image:
    frame = housing.copy()
    px = frame.load()
    pulse = frame_idx % len(SOURCE_COLORS)
    for y in range(5, 11):
        for x in range(5, 11):
            dist = abs(x - 8) + abs(y - 8)
            if dist <= 3 + (frame_idx % 2):
                px[x, y] = SOURCE_COLORS[(pulse + dist) % len(SOURCE_COLORS)]
    px[8, 8] = (255, 160, 255, 255)
    return frame


def build_animation_strip(housing: Image.Image, maker, out_name: str, frametime: int = 2) -> None:
    strip = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        frame = maker(housing, i)
        strip.paste(frame, (0, i * FRAME_SIZE))
    save(strip, out_name)
    write_mcmeta(out_name.replace(".png", ""), frametime)


def copy_references() -> None:
    ref_out = os.path.join(OUT, "_ae2_reference_copies")
    os.makedirs(ref_out, exist_ok=True)
    for name in os.listdir(REF):
        src = os.path.join(REF, name)
        if os.path.isfile(src):
            shutil.copy2(src, os.path.join(ref_out, name))


def write_readme() -> None:
    readme = """# ADM 编织元件 / 增幅卡 — 待审核材质

本目录由 `tools/generate_loom_textures.py` 基于 AE2 官方贴图参考生成，供审核后再迁入正式路径。

## AE2 参考来源（已解压到 `../reference_ae2/`）

| 参考文件 | 用途 |
|---------|------|
| ItemMaterial.CardSpeed.png | 编织增幅卡外形 |
| ItemMaterial.CardSuperSpeed.png | 超级编织增幅卡外形 |
| ItemBasicStorageCell.64k.png | 物品编织元件外壳 |
| fluid_storage.64.png | 流体编织元件外壳 |
| ItemExtremeStorageCell.Universe.png | 动态帧条布局（16×320，20 帧） |

## 生成文件

| 文件 | 类型 | 说明 |
|------|------|------|
| weave_amplifier.png + .mcmeta | 动态 128×2560（20 帧） | 编织增幅卡；128px 宇宙星尘动画（独立脚本） |
| super_weave_amplifier.png | 静态 16×16 | 超级编织增幅卡，额外橙色高亮 |
| data_dust_loom_cell.png + .mcmeta | 动态 16×320 | 织尘元件，中心粉尘粒子动画 |
| data_form_loom_cell.png + .mcmeta | 动态 16×320 | 织形元件，线框立方旋转动画 |
| data_flow_cell.png + .mcmeta | 动态 16×320 | 涌流元件，液面涨落动画 |
| data_source_loom_cell.png + .mcmeta | 动态 16×320 | 织源元件，源质漩涡脉冲动画 |

## 审核通过后迁入路径

```
textures/items/weave_amplifier.png (+ .mcmeta)
textures/items/super_weave_amplifier.png
textures/items/data_dust_loom_cell.png (+ .mcmeta)
textures/items/data_form_loom_cell.png (+ .mcmeta)
textures/items/data_flow_cell.png (+ .mcmeta)
textures/items/data_source_loom_cell.png (+ .mcmeta)
```

并在 `LoaderItem.java` 中为 4 种元件分别设置独立 `setTextureName`。

## 重新生成

```bash
python tools/generate_loom_textures.py
```

可在脚本中调整 `FRAME_COUNT`、颜色表、或改用 AE2 Universe 条带作为底板进一步手工精修。
"""
    with open(os.path.join(OUT, "README.md"), "w", encoding="utf-8") as f:
        f.write(readme)


def main() -> None:
    copy_references()
    build_weave_amplifier_animation("ItemMaterial.CardSpeed.png", "weave_amplifier.png", "basic")
    # super_weave_amplifier: 128px purple cosmic strip via generate_weave_amplifier_cosmic.py

    item_housing = load("ItemBasicStorageCell.64k.png")
    fluid_housing = load("fluid_storage.64.png")

    # data_source_loom_cell: 128px via generate_source_loom_cell.py

    import subprocess
    import sys

    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools/generate_dust_loom_cell.py")])
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools/generate_form_loom_cell.py")])
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools/generate_flow_loom_cell.py")])
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools/generate_source_loom_cell.py")])

    write_readme()
    print("Generated textures in:", OUT)


if __name__ == "__main__":
    main()
