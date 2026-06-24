#!/usr/bin/env python3
"""Generate 128x128 dust loom cell animation (Universe-cell layout, inward dust gather)."""

from __future__ import annotations

import json
import math
import os
import random
from typing import List, Sequence, Tuple

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/advancedatamonitor/textures")
ITEMS = os.path.join(ASSETS, "items")
GENERATED = os.path.join(ASSETS, "review/generated")

FRAME_SIZE = 128
FRAME_COUNT = 20
CORE_CX = 64.0
CORE_CY = 56.0

Color = Tuple[int, int, int]
Rgba = Tuple[int, int, int, int]

# Gray-white luminous ramp (Universe aurora → desaturated dust glow)
WINDOW_VOID = (28, 30, 36)
WINDOW_MID = (105, 110, 118)
WINDOW_BRIGHT = (205, 210, 218)
WINDOW_HOT = (255, 255, 252)
DUST_WARM = (255, 248, 235)


def clamp(value: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, value))


def mix_color(a: Color, b: Color, t: float) -> Color:
    t = clamp(t)
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def add_glow(base: Color, glow: Color, strength: float) -> Color:
    strength = clamp(strength)
    return tuple(min(255, int(base[i] + glow[i] * strength)) for i in range(3))


def smoothstep(value: float) -> float:
    value = clamp(value)
    return value * value * (3.0 - 2.0 * value)


def hash21(x: float, y: float, seed: float) -> float:
    return (math.sin(x * 12.9898 + y * 78.233 + seed * 43.758) * 43758.5453) % 1.0


def build_particles() -> List[Tuple[float, float, float, float, float]]:
    """angle, start_radius, end_radius, phase, size_weight"""
    rng = random.Random(61001)
    particles: List[Tuple[float, float, float, float, float]] = []
    for _ in range(96):
        particles.append(
            (
                rng.random() * 2.0 * math.pi,
                rng.uniform(34.0, 52.0),
                rng.uniform(0.0, 8.0),
                rng.random(),
                rng.uniform(0.45, 1.0),
            )
        )
    return particles


PARTICLES = build_particles()


def load_housing_template() -> Image.Image:
    base_path = os.path.join(ITEMS, "data_dust_loom_cell_base.png")
    item_path = os.path.join(ITEMS, "data_dust_loom_cell.png")
    if os.path.isfile(base_path):
        return Image.open(base_path).convert("RGBA")
    source = Image.open(item_path).convert("RGBA")
    frame = source.crop((0, 0, FRAME_SIZE, FRAME_SIZE))
    os.makedirs(ITEMS, exist_ok=True)
    frame.save(base_path)
    return frame


def is_housing_pixel(r: int, g: int, b: int, a: int, x: int, y: int) -> bool:
    if a < 24:
        return False
    saturation = max(r, g, b) - min(r, g, b)
    # Metallic frame, dark outlines, connector bezels
    if saturation < 42 and max(r, g, b) > 55:
        return True
    if max(r, g, b) < 88:
        return True
    # Status LED (Universe cell corner element)
    if x >= 104 and y >= 106 and g > 130 and r < 130:
        return True
    # Colored wiring on housing
    if r > 170 and g < 110 and b < 110:
        return True
    if g > 150 and b > 150 and r < 130:
        return True
    return False


def build_core_mask(template: Image.Image) -> Tuple[List[List[bool]], List[List[float]]]:
    px = template.load()
    mask = [[False] * FRAME_SIZE for _ in range(FRAME_SIZE)]
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            r, g, b, a = px[x, y]
            if a < 24:
                continue
            if is_housing_pixel(r, g, b, a, x, y):
                continue
            # Inner diamond window (Universe core viewport)
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


def universe_aurora_gray(x: int, y: int, t: float, gather: float) -> Color:
    """
    ItemExtremeStorageCell-style flowing bands, desaturated to gray-white dust light.
    gather: 0..1 inward convergence boost.
    """
    dx = (x - CORE_CX) / 44.0
    dy = (y - CORE_CY) / 46.0
    radius = math.hypot(dx, dy)
    theta = math.atan2(dy, dx)
    phase = 2.0 * math.pi * t

    window = clamp(1.0 - radius * 0.92)
    color = mix_color(WINDOW_VOID, WINDOW_MID, window * 0.35)

    band_a = math.sin(theta * 3.0 + phase * 2.0 + radius * 5.0) * 0.5 + 0.5
    band_b = math.sin(theta * 5.0 - phase * 3.0 - radius * 3.5) * 0.5 + 0.5
    band_c = math.sin(theta * 2.0 + phase * 5.0) * 0.5 + 0.5
    aurora = (band_a * 0.45 + band_b * 0.35 + band_c * 0.20) * window
    aurora *= 0.55 + gather * 0.55

    if aurora > 0.08:
        tone = mix_color(WINDOW_MID, WINDOW_BRIGHT, aurora)
        tone = mix_color(tone, WINDOW_HOT, max(0.0, aurora - 0.55) * 1.4)
        color = mix_color(color, tone, clamp(aurora * 0.9))

    return color


def inward_gather_field(x: int, y: int, t: float) -> Tuple[Color, float]:
    """Radial inward dust stream + central white convergence."""
    dx = x - CORE_CX
    dy = y - CORE_CY
    radius = math.hypot(dx, dy)
    theta = math.atan2(dy, dx)
    phase = 2.0 * math.pi * t
    flow = math.sin(theta * 6.0 + phase * 4.0 - radius * 0.22) * 0.5 + 0.5
    radial_wave = math.sin(radius * 0.28 - phase * 6.0) * 0.5 + 0.5
    stream = flow * radial_wave * math.exp(-radius * radius / 1800.0)
    stream *= clamp((radius - 6.0) / 42.0)

    color = WINDOW_VOID
    color = add_glow(color, WINDOW_BRIGHT, stream * 0.55)
    color = add_glow(color, DUST_WARM, stream * hash21(x * 0.11, y * 0.09, phase) * 0.35)

    gather = clamp(stream * 0.65 + math.exp(-radius * radius / 420.0) * (0.35 + 0.35 * math.sin(phase * 2.0)))
    return color, gather


def render_core_pixel(x: int, y: int, t: float) -> Color:
    stream_color, gather = inward_gather_field(x, y, t)
    color = universe_aurora_gray(x, y, t, gather)
    color = mix_color(color, stream_color, 0.42)

    phase = 2.0 * math.pi * t

    # Fine sparkling powder grains
    sparkle = hash21(x * 0.31 + t * 2.0, y * 0.27, phase)
    dx = x - CORE_CX
    dy = y - CORE_CY
    radius = math.hypot(dx, dy)
    ring = math.sin(radius * 0.35 - phase * 5.0) * 0.5 + 0.5
    if sparkle > 0.93 and ring > 0.45:
        color = add_glow(color, WINDOW_HOT, (sparkle - 0.93) * 14.0)
    if sparkle > 0.965 and radius < 18:
        color = add_glow(color, DUST_WARM, 0.45)

    # Central convergence nucleus (Universe core hotspot, gray-white)
    core = math.exp(-radius * radius / 260.0) * (0.5 + 0.5 * math.cos(phase * 2.0))
    core *= 0.75 + gather * 0.55
    color = add_glow(color, WINDOW_HOT, core * 0.85)
    color = add_glow(color, WINDOW_BRIGHT, core * 0.35)

    return color


def draw_particle_layer(frame: Image.Image, frame_idx: int, mask) -> None:
    px = frame.load()
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    phase = 2.0 * math.pi * t
    for angle, r_start, r_end, p_phase, weight in PARTICLES:
        prog = (t + p_phase) % 1.0
        eased = smoothstep(prog)
        radius = r_start + (r_end - r_start) * eased
        x = int(CORE_CX + math.cos(angle + phase * 0.15) * radius)
        y = int(CORE_CY + math.sin(angle + phase * 0.15) * radius * 0.92)
        strength = (1.0 - eased) * weight
        if strength < 0.12:
            continue
        for ox, oy, falloff in ((0, 0, 1.0), (1, 0, 0.5), (-1, 0, 0.35), (0, 1, 0.35), (0, -1, 0.35)):
            px_x = x + ox
            px_y = y + oy
            if not (0 <= px_x < FRAME_SIZE and 0 <= px_y < FRAME_SIZE):
                continue
            if not mask[px_y][px_x]:
                continue
            r, g, b, a = px[px_x, px_y]
            dust = mix_color((r, g, b), DUST_WARM, 0.55)
            glow = add_glow(dust, WINDOW_HOT, strength * falloff * 0.75)
            px[px_x, px_y] = (glow[0], glow[1], glow[2], a)


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
            # Blend softly into housing bezel at window edge
            blend = mix_color((tr, tg, tb), core, 0.90 * edge + 0.10)
            px[x, y] = (blend[0], blend[1], blend[2], ta)
    draw_particle_layer(frame, frame_idx, mask)
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
        raise AssertionError("dust loom loop mismatch: %s pixels" % mismatches)


def generate() -> None:
    template = load_housing_template()
    mask, feather = build_core_mask(template)
    verify_loop(template, mask, feather)

    strip = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        strip.paste(render_frame(template, mask, feather, i), (0, i * FRAME_SIZE))

    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(GENERATED, exist_ok=True)
    png = os.path.join(ITEMS, "data_dust_loom_cell.png")
    meta = os.path.join(ITEMS, "data_dust_loom_cell.png.mcmeta")
    strip.save(png)
    write_mcmeta(meta)
    strip.save(os.path.join(GENERATED, "data_dust_loom_cell.png"))
    write_mcmeta(os.path.join(GENERATED, "data_dust_loom_cell.png.mcmeta"))
    save_preview(strip, os.path.join(GENERATED, "data_dust_loom_cell_preview.png"))
    print("Generated:", png, strip.size)


if __name__ == "__main__":
    generate()
