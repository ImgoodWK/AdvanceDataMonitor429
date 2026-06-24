#!/usr/bin/env python3
"""Generate 128x128 source loom cell — arcane essentia / magic fluid animation."""

from __future__ import annotations

import json
import math
import os
import random
import sys
from typing import List, Sequence, Tuple

from PIL import Image

TOOLS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, TOOLS)

from generate_dust_loom_cell import (  # noqa: E402
    CORE_CX,
    CORE_CY,
    FRAME_COUNT,
    FRAME_SIZE,
    GENERATED,
    ITEMS,
    add_glow,
    clamp,
    hash21,
    is_housing_pixel,
    load_housing_template,
    mix_color,
)

Color = Tuple[int, int, int]

# Connector pins: red on shared base → purple for source (essentia) cell
SOURCE_CONNECTOR_PURPLE = (150, 55, 190)

# Red ↔ purple arcane palette
VOID = (14, 6, 22)
DEEP = (48, 12, 58)
CRIMSON = (170, 35, 55)
MAGENTA = (195, 55, 145)
VIOLET = (130, 45, 210)
BRIGHT = (240, 100, 190)
CORE_HOT = (255, 170, 220)


def recolor_red_connector_to_purple(image: Image.Image) -> Image.Image:
    out = image.copy()
    px = out.load()
    pr, pg, pb = SOURCE_CONNECTOR_PURPLE
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            r, g, b, a = px[x, y]
            if a >= 24 and r > 150 and g < 120 and b < 120:
                px[x, y] = (pr, pg, pb, a)
    return out


def is_source_housing_pixel(r: int, g: int, b: int, a: int, x: int, y: int) -> bool:
    if a >= 24 and r > 110 and b > 130 and g < 105:
        return True
    return is_housing_pixel(r, g, b, a, x, y)


def build_source_core_mask(template: Image.Image):
    px = template.load()
    mask = [[False] * FRAME_SIZE for _ in range(FRAME_SIZE)]
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            r, g, b, a = px[x, y]
            if a < 24:
                continue
            if is_source_housing_pixel(r, g, b, a, x, y):
                continue
            dx = abs(x - CORE_CX) / 49.0
            dy = abs(y - CORE_CY) / 52.0
            if dx + dy <= 1.02:
                mask[y][x] = True

    feather = [[0.0] * FRAME_SIZE for _ in range(FRAME_SIZE)]
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            if not mask[y][x]:
                continue
            nearest = 99
            for oy in range(max(0, y - 8), min(FRAME_SIZE, y + 9)):
                for ox in range(max(0, x - 8), min(FRAME_SIZE, x + 9)):
                    if not mask[oy][ox]:
                        nearest = min(nearest, abs(ox - x) + abs(oy - y))
            feather[y][x] = float(min(nearest, 8))
    return mask, feather


def load_source_housing_template() -> Image.Image:
    return recolor_red_connector_to_purple(load_housing_template())


def gradient_magic(t: float, amount: float) -> Color:
    """Shift crimson → violet → magenta over the loop."""
    phase = 2.0 * math.pi * t
    wave = (math.sin(phase) + 1.0) * 0.5
    mid = mix_color(CRIMSON, VIOLET, wave)
    tone = mix_color(mid, MAGENTA, (math.sin(phase * 2.0) + 1.0) * 0.5)
    deep = mix_color(DEEP, tone, clamp(amount))
    return mix_color(deep, BRIGHT, clamp(amount * 1.1 - 0.15))


def build_essence_motes() -> List[Tuple[float, float, float, float]]:
    rng = random.Random(82003)
    motes = []
    for _ in range(36):
        motes.append((rng.uniform(0, 6.28), rng.uniform(10, 38), rng.random(), rng.uniform(0.6, 1.2)))
    return motes


ESSENCE_MOTES = build_essence_motes()


def render_core_pixel(x: int, y: int, t: float) -> Color:
    dx = x - CORE_CX
    dy = y - CORE_CY
    radius = math.hypot(dx, dy)
    theta = math.atan2(dy, dx)
    phase = 2.0 * math.pi * t
    window = clamp(1.0 - radius / 50.0)

    color = mix_color(VOID, DEEP, window * 0.65)

    # Faint rotating hex sigil (Thaumcraft arcane hint)
    hex_angle = theta + phase * 0.6
    hex_ring = math.sin(hex_angle * 3.0 + math.sin(radius * 0.09) * 2.0) * 0.5 + 0.5
    hex_mask = math.exp(-((radius - 30.0) ** 2) / 520.0) * hex_ring * 0.22 * window
    color = add_glow(color, VIOLET, hex_mask)

    # Bottom arcane fluid pool — simmering crimson/purple surface
    pool_y = dy - 18.0
    pool = math.exp(-(pool_y * pool_y) / 180.0) * math.exp(-(dx * dx) / 520.0)
    ripple = math.sin(dx * 0.22 + phase * 3.5) * math.cos(dx * 0.11 - phase * 2.0)
    pool *= 0.55 + ripple * 0.25
    pool_color = gradient_magic(t, pool)
    color = mix_color(color, pool_color, clamp(pool * 0.85))

    # Twin helix essentia vapors rising from pool
    for helix in (0.0, math.pi):
        twist = math.sin(theta * 2.0 + phase * 3.0 + helix + radius * 0.08) * 0.5 + 0.5
        column = math.exp(-((radius - 16.0 - twist * 8.0) ** 2) / 140.0)
        rise = math.sin(dy * 0.12 - phase * 4.5 + helix) * 0.5 + 0.5
        rise *= clamp((28.0 - dy) / 36.0)
        vapor = column * rise * window
        vapor_color = mix_color(CRIMSON, MAGENTA, twist)
        vapor_color = mix_color(vapor_color, VIOLET, rise * 0.45)
        color = add_glow(color, vapor_color, vapor * 0.62)

    # Condensing motes near top (distilled essence)
    condense = math.exp(-((dy + 22.0) ** 2) / 260.0) * (0.45 + 0.55 * math.sin(phase * 2.5 + dx * 0.1))
    condense *= window
    color = add_glow(color, BRIGHT, condense * 0.35)

    # Central node — pulsing arcane heart
    core = math.exp(-radius * radius / 220.0) * (0.5 + 0.5 * math.cos(phase * 2.0))
    core_color = mix_color(CRIMSON, CORE_HOT, (math.sin(phase * 1.5) + 1.0) * 0.5)
    color = add_glow(color, core_color, core * 0.88)
    color = add_glow(color, (255, 220, 255), core * 0.25)

    # Outer aura band (red-purple breathing ring)
    ring = math.exp(-((radius - 24.0) ** 2) / 110.0) * (0.4 + 0.6 * math.sin(phase * 3.0 - theta * 2.0))
    ring_color = mix_color(MAGENTA, VIOLET, (math.sin(phase + theta) + 1.0) * 0.5)
    color = add_glow(color, ring_color, ring * 0.42 * window)

    return color


def draw_essence_particles(frame: Image.Image, frame_idx: int, mask) -> None:
    px = frame.load()
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    phase = 2.0 * math.pi * t

    for base_angle, orbit_r, p_phase, speed in ESSENCE_MOTES:
        ang = base_angle + phase * speed
        lift = (t + p_phase) % 1.0
        r = orbit_r * (1.0 - lift * 0.55)
        x = int(CORE_CX + math.cos(ang) * r)
        y = int(CORE_CY + 12.0 - lift * 38.0 + math.sin(ang * 2.0) * 3.0)
        strength = (1.0 - lift) * 0.75
        mote_color = mix_color(CRIMSON, VIOLET, lift)
        for ox, oy, falloff in ((0, 0, 1.0), (1, 0, 0.45), (0, 1, 0.35)):
            px_x = x + ox
            px_y = y + oy
            if not (0 <= px_x < FRAME_SIZE and 0 <= px_y < FRAME_SIZE):
                continue
            if not mask[px_y][px_x]:
                continue
            r0, g0, b0, a0 = px[px_x, px_y]
            glow = add_glow((r0, g0, b0), mote_color, strength * falloff * 0.7)
            px[px_x, px_y] = (glow[0], glow[1], glow[2], a0)

    # Arcane sparks
    for i in range(20):
        spark = hash21(i * 0.7, phase, t + i)
        if spark < 0.92:
            continue
        ang = spark * 12.0 + phase
        r = 8.0 + spark * 28.0
        x = int(CORE_CX + math.cos(ang) * r)
        y = int(CORE_CY + math.sin(ang) * r * 0.85 - 4.0)
        if not (0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE) or not mask[y][x]:
            continue
        r0, g0, b0, a0 = px[x, y]
        c = mix_color(MAGENTA, CORE_HOT, spark)
        glow = add_glow((r0, g0, b0), c, (spark - 0.92) * 10.0)
        px[x, y] = (glow[0], glow[1], glow[2], a0)


def render_frame(
    template: Image.Image,
    mask: Sequence[Sequence[bool]],
    feather: Sequence[Sequence[float]],
    frame_idx: int,
) -> Image.Image:
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    frame = template.copy()
    px = frame.load()
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            if not mask[y][x]:
                continue
            core = render_core_pixel(x, y, t)
            edge = clamp(feather[y][x] / 5.0)
            tr, tg, tb, ta = px[x, y]
            blend = mix_color((tr, tg, tb), core, 0.92 * edge + 0.08)
            px[x, y] = (blend[0], blend[1], blend[2], ta)
    draw_essence_particles(frame, frame_idx, mask)
    return frame


def write_mcmeta(path: str) -> None:
    meta = {
        "animation": {
            "frametime": 1,
            "interpolate": True,
            "width": FRAME_SIZE,
            "height": FRAME_SIZE,
        }
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(meta, handle, indent=2)
        handle.write("\n")


def save_preview(strip: Image.Image, path: str) -> None:
    preview = Image.new("RGBA", (FRAME_SIZE * FRAME_COUNT, FRAME_SIZE), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        preview.paste(strip.crop((0, i * FRAME_SIZE, FRAME_SIZE, (i + 1) * FRAME_SIZE)), (i * FRAME_SIZE, 0))
    preview.save(path)


def verify_loop(template, mask, feather) -> None:
    first = render_frame(template, mask, feather, 0)
    wrap = render_frame(template, mask, feather, FRAME_COUNT)
    fpx = first.load()
    wpx = wrap.load()
    mismatches = 0
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            if not mask[y][x]:
                continue
            if fpx[x, y][:3] != wpx[x, y][:3]:
                mismatches += 1
    if mismatches > 0:
        raise AssertionError("source loom loop mismatch: %s pixels" % mismatches)


def generate() -> None:
    template = load_source_housing_template()
    mask, feather = build_source_core_mask(template)
    verify_loop(template, mask, feather)

    strip = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        strip.paste(render_frame(template, mask, feather, i), (0, i * FRAME_SIZE))

    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(GENERATED, exist_ok=True)
    png = os.path.join(ITEMS, "data_source_loom_cell.png")
    meta = os.path.join(ITEMS, "data_source_loom_cell.png.mcmeta")
    strip.save(png)
    write_mcmeta(meta)
    strip.save(os.path.join(GENERATED, "data_source_loom_cell.png"))
    write_mcmeta(os.path.join(GENERATED, "data_source_loom_cell.png.mcmeta"))
    save_preview(strip, os.path.join(GENERATED, "data_source_loom_cell_preview.png"))
    print("Generated:", png, strip.size)


if __name__ == "__main__":
    generate()
