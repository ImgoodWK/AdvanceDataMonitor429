#!/usr/bin/env python3
"""Generate 128x128 flow loom cell — multi-fluid stream animation."""

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

# Connector pins: red on shared base → blue for flow cell identification
FLOW_CONNECTOR_BLUE = (66, 120, 189)

TANK_DEEP = (8, 14, 28)
TANK_MID = (16, 28, 52)
TANK_SURFACE = (35, 55, 80)
HIGHLIGHT = (210, 235, 255)

# Main flow: lower-left → upper-right through the cell window
FLOW_ANGLE = -0.62

# Each stream: angle offset, deep color, bright color, speed, lateral center, width
FLUID_STREAMS: List[Tuple[float, Color, Color, float, float, float]] = [
    (-0.08, (20, 70, 160), (70, 160, 255), 1.00, -14.0, 11.0),   # water
    (0.06, (150, 55, 10), (255, 130, 45), 1.25, -2.0, 9.0),      # lava
    (0.12, (25, 100, 35), (90, 230, 110), 0.90, 10.0, 8.5),     # acid
    (-0.18, (80, 25, 130), (190, 90, 255), 1.15, 18.0, 7.0),   # exotic
    (0.20, (120, 90, 15), (255, 215, 70), 1.05, -22.0, 6.5),    # oil
    (-0.04, (130, 30, 70), (255, 100, 170), 0.95, 6.0, 7.5),    # organic
]


def build_flow_bubbles() -> List[Tuple[float, float, float, float, int]]:
    """start_along, start_across, speed, phase, stream_index"""
    rng = random.Random(71002)
    bubbles: List[Tuple[float, float, float, float, int]] = []
    for _ in range(48):
        bubbles.append(
            (
                rng.uniform(-38.0, 38.0),
                rng.uniform(-22.0, 22.0),
                rng.uniform(0.85, 1.35),
                rng.random(),
                rng.randint(0, len(FLUID_STREAMS) - 1),
            )
        )
    return bubbles


BUBBLES = build_flow_bubbles()


def recolor_red_connector_to_blue(image: Image.Image) -> Image.Image:
    out = image.copy()
    px = out.load()
    br, bg, bb = FLOW_CONNECTOR_BLUE
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            r, g, b, a = px[x, y]
            if a >= 24 and r > 150 and g < 120 and b < 120:
                px[x, y] = (br, bg, bb, a)
    return out


def is_flow_housing_pixel(r: int, g: int, b: int, a: int, x: int, y: int) -> bool:
    if a >= 24 and b > 150 and r < 100 and g < 145:
        return True
    return is_housing_pixel(r, g, b, a, x, y)


def build_flow_core_mask(template: Image.Image):
    px = template.load()
    mask = [[False] * FRAME_SIZE for _ in range(FRAME_SIZE)]
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            r, g, b, a = px[x, y]
            if a < 24:
                continue
            if is_flow_housing_pixel(r, g, b, a, x, y):
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


def load_flow_housing_template() -> Image.Image:
    return recolor_red_connector_to_blue(load_housing_template())


def flow_coords(dx: float, dy: float, angle: float) -> Tuple[float, float]:
    along = dx * math.cos(angle) + dy * math.sin(angle)
    across = -dx * math.sin(angle) + dy * math.cos(angle)
    return along, across


def stream_strength_world(
    dx: float,
    dy: float,
    t: float,
    angle_off: float,
    speed: float,
    across_center: float,
    width: float,
    phase_off: float,
) -> float:
    angle = FLOW_ANGLE + angle_off
    along, across = flow_coords(dx, dy, angle)
    across -= across_center
    phase = 2.0 * math.pi * t

    ripple = math.sin(across * 0.42 + along * 0.06 + phase * 2.5) * 1.8
    body = math.exp(-((across + ripple) ** 2) / (width * width))

    # Traveling pulse = fluid moving downstream
    pulse = math.sin(along * 0.13 - phase * speed * 2.0 + phase_off) * 0.5 + 0.5
    micro = math.sin(along * 0.31 - phase * speed * 3.2 + phase_off * 1.7) * 0.5 + 0.5

    fade = clamp((along + 42.0) / 28.0) * clamp((38.0 - along) / 22.0 + 0.2)
    return body * (0.45 + pulse * 0.55) * (0.8 + micro * 0.2) * fade


def render_core_pixel(x: int, y: int, t: float) -> Color:
    dx = x - CORE_CX
    dy = y - CORE_CY
    radius = math.hypot(dx, dy)
    phase = 2.0 * math.pi * t
    window = clamp(1.0 - radius / 50.0)

    color = mix_color(TANK_DEEP, TANK_MID, window * 0.55)

    # Glass meniscus / surface line near top of tank
    surface_y = CORE_CY - 26.0 + math.sin(phase + x * 0.07) * 1.6
    if y < surface_y:
        sky = clamp((surface_y - y) / 10.0) * window
        color = mix_color(color, TANK_SURFACE, sky * 0.45)
        color = add_glow(color, HIGHLIGHT, sky * 0.12)

    # Layered fluid streams (back to front)
    along_main, _ = flow_coords(dx, dy, FLOW_ANGLE)
    for idx, (angle_off, deep, bright, speed, across_center, width) in enumerate(FLUID_STREAMS):
        strength = stream_strength_world(dx, dy, t, angle_off, speed, across_center, width, idx * 0.9)
        if strength < 0.03:
            continue
        tone = mix_color(deep, bright, clamp(strength * 1.15))
        # Specular streak moving with flow
        streak = math.sin(along_main * 0.22 - phase * speed * 2.5 + idx) * 0.5 + 0.5
        tone = mix_color(tone, HIGHLIGHT, streak * strength * 0.35)
        color = mix_color(color, tone, clamp(strength * 0.82))

    # Convergence outlet glow (fluids collect toward upper-right)
    outlet_dx = dx + 18.0
    outlet_dy = dy + 14.0
    outlet = math.exp(-(outlet_dx * outlet_dx + outlet_dy * outlet_dy) / 380.0)
    outlet *= 0.5 + 0.5 * math.sin(phase * 3.0)
    color = add_glow(color, HIGHLIGHT, outlet * 0.35)

    # Caustic shimmer on fluid surface
    caustic = math.sin(dx * 0.19 + dy * 0.15 - phase * 4.0) * math.cos(dy * 0.11 + phase * 2.0)
    caustic = (caustic * 0.5 + 0.5) * math.exp(-radius * radius / 1400.0) * 0.08
    color = add_glow(color, HIGHLIGHT, caustic)

    return color


def draw_flow_particles(frame: Image.Image, frame_idx: int, mask) -> None:
    px = frame.load()
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    phase = 2.0 * math.pi * t

    for start_along, start_across, speed, p_phase, stream_idx in BUBBLES:
        stream = FLUID_STREAMS[stream_idx]
        angle_off, deep, bright, stream_speed, across_center, _width = stream
        angle = FLOW_ANGLE + angle_off
        prog = (t * speed * stream_speed + p_phase) % 1.0
        along = start_along + prog * 52.0 - 8.0
        across = start_across + math.sin(prog * 6.28 + p_phase * 9.0) * 2.5
        x = int(CORE_CX + along * math.cos(angle) - across * math.sin(angle))
        y = int(CORE_CY + along * math.sin(angle) + across * math.cos(angle))
        strength = (1.0 - prog) * 0.85 + 0.15
        bubble_color = mix_color(bright, HIGHLIGHT, 0.35)
        for ox, oy, falloff in ((0, 0, 1.0), (1, 0, 0.4), (0, -1, 0.35)):
            px_x = x + ox
            px_y = y + oy
            if not (0 <= px_x < FRAME_SIZE and 0 <= px_y < FRAME_SIZE):
                continue
            if not mask[px_y][px_x]:
                continue
            r, g, b, a = px[px_x, px_y]
            glow = add_glow((r, g, b), bubble_color, strength * falloff * 0.65)
            px[px_x, px_y] = (glow[0], glow[1], glow[2], a)

    # Tiny droplets carried in main flow
    for i in range(24):
        seed = i * 1.37 + phase
        prog = (t * 1.1 + hash21(i, 0.5, 0.0)) % 1.0
        along = -36.0 + prog * 68.0
        across = math.sin(prog * 8.0 + seed) * 16.0
        x = int(CORE_CX + along * math.cos(FLOW_ANGLE) - across * math.sin(FLOW_ANGLE))
        y = int(CORE_CY + along * math.sin(FLOW_ANGLE) + across * math.cos(FLOW_ANGLE))
        if not (0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE):
            continue
        if not mask[y][x]:
            continue
        pick = i % len(FLUID_STREAMS)
        _, _d, bright, _s, _c, _w = FLUID_STREAMS[pick]
        r, g, b, a = px[x, y]
        glow = add_glow((r, g, b), bright, 0.45)
        px[x, y] = (glow[0], glow[1], glow[2], a)


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
    draw_flow_particles(frame, frame_idx, mask)
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
        raise AssertionError("flow loom loop mismatch: %s pixels" % mismatches)


def generate() -> None:
    template = load_flow_housing_template()
    mask, feather = build_flow_core_mask(template)
    verify_loop(template, mask, feather)

    strip = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        strip.paste(render_frame(template, mask, feather, i), (0, i * FRAME_SIZE))

    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(GENERATED, exist_ok=True)
    png = os.path.join(ITEMS, "data_flow_cell.png")
    meta = os.path.join(ITEMS, "data_flow_cell.png.mcmeta")
    strip.save(png)
    write_mcmeta(meta)
    strip.save(os.path.join(GENERATED, "data_flow_cell.png"))
    write_mcmeta(os.path.join(GENERATED, "data_flow_cell.png.mcmeta"))
    save_preview(strip, os.path.join(GENERATED, "data_flow_cell_preview.png"))
    print("Generated:", png, strip.size)


if __name__ == "__main__":
    generate()
