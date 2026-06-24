#!/usr/bin/env python3
"""Generate 128x128 form loom cell — galaxy backdrop + morphing cyan geometry."""

from __future__ import annotations

import json
import math
import os
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
    build_core_mask,
    clamp,
    hash21,
    load_housing_template,
    mix_color,
    smoothstep,
)

Color = Tuple[int, int, int]

# Blue-cyan tech palette
VOID = (6, 10, 28)
GALAXY_DEEP = (18, 28, 72)
GALAXY_MID = (40, 70, 140)
GALAXY_ARM = (80, 130, 210)
CYAN_DEEP = (0, 110, 170)
CYAN_MID = (30, 190, 230)
CYAN_BRIGHT = (120, 240, 255)
CYAN_HOT = (210, 255, 255)

Vec3 = Tuple[float, float, float]


def dist_point_segment(px: float, py: float, x1: float, y1: float, x2: float, y2: float) -> float:
    dx = x2 - x1
    dy = y2 - y1
    length_sq = dx * dx + dy * dy
    if length_sq < 1e-6:
        return math.hypot(px - x1, py - y1)
    t = clamp(((px - x1) * dx + (py - y1) * dy) / length_sq)
    proj_x = x1 + t * dx
    proj_y = y1 + t * dy
    return math.hypot(px - proj_x, py - proj_y)


def rotate_y(point: Vec3, angle: float) -> Vec3:
    x, y, z = point
    c = math.cos(angle)
    s = math.sin(angle)
    return (x * c + z * s, y, -x * s + z * c)


def rotate_x(point: Vec3, angle: float) -> Vec3:
    x, y, z = point
    c = math.cos(angle)
    s = math.sin(angle)
    return (x, y * c - z * s, y * s + z * c)


def project_iso(point: Vec3, scale: float) -> Tuple[float, float]:
    x, y, z = point
    sx = CORE_CX + (x - z) * 0.866 * scale
    sy = CORE_CY + y * scale + (x + z) * 0.5 * scale
    return sx, sy


def lerp3(a: Vec3, b: Vec3, t: float) -> Vec3:
    t = clamp(t)
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)


CUBE_VERTS: List[Vec3] = [
    (-1.0, -1.0, -1.0),
    (1.0, -1.0, -1.0),
    (1.0, 1.0, -1.0),
    (-1.0, 1.0, -1.0),
    (-1.0, -1.0, 1.0),
    (1.0, -1.0, 1.0),
    (1.0, 1.0, 1.0),
    (-1.0, 1.0, 1.0),
]
CUBE_EDGES = (
    (0, 1),
    (1, 2),
    (2, 3),
    (3, 0),
    (4, 5),
    (5, 6),
    (6, 7),
    (7, 4),
    (0, 4),
    (1, 5),
    (2, 6),
    (3, 7),
)

OCTAHEDRON_VERTS: List[Vec3] = [
    (0.0, 1.35, 0.0),
    (1.35, 0.0, 0.0),
    (0.0, 0.0, 1.35),
    (-1.35, 0.0, 0.0),
    (0.0, 0.0, -1.35),
    (0.0, -1.35, 0.0),
]
OCTAHEDRON_EDGES = (
    (0, 1),
    (0, 2),
    (0, 3),
    (0, 4),
    (1, 2),
    (2, 3),
    (3, 4),
    (4, 1),
    (5, 1),
    (5, 2),
    (5, 3),
    (5, 4),
)

HEX_RING = [  # flat hexagon in XZ plane
    (math.cos(a), 0.0, math.sin(a))
    for a in [math.pi / 6 + i * math.pi / 3 for i in range(6)]
]
HEX_EDGES = tuple((i, (i + 1) % 6) for i in range(6))


def morph_vertices(shape_a: List[Vec3], shape_b: List[Vec3], morph: float) -> List[Vec3]:
    count = min(len(shape_a), len(shape_b))
    return [lerp3(shape_a[i], shape_b[i], morph) for i in range(count)]


def wireframe_glow(px: float, py: float, verts: List[Vec3], edges, scale: float, angle_y: float, angle_x: float) -> float:
    projected = []
    for v in verts:
        rv = rotate_x(rotate_y(v, angle_y), angle_x)
        projected.append(project_iso(rv, scale))
    strength = 0.0
    for i, j in edges:
        if i >= len(projected) or j >= len(projected):
            continue
        x1, y1 = projected[i]
        x2, y2 = projected[j]
        dist = dist_point_segment(px, py, x1, y1, x2, y2)
        strength = max(strength, math.exp(-dist * dist / 5.5))
    return strength


def galaxy_background(x: int, y: int, t: float) -> Color:
    dx = x - CORE_CX
    dy = y - CORE_CY
    radius = math.hypot(dx, dy)
    theta = math.atan2(dy, dx)
    phase = 2.0 * math.pi * t

    rot_theta = theta + phase * 0.85
    window = clamp(1.0 - radius / 52.0)
    color = mix_color(VOID, GALAXY_DEEP, window * 0.55)

    arm_a = math.sin(rot_theta * 2.4 + radius * 0.11 - phase * 4.0) * 0.5 + 0.5
    arm_b = math.sin(rot_theta * 3.8 - radius * 0.08 + phase * 2.5) * 0.5 + 0.5
    core_halo = math.exp(-radius * radius / 900.0)
    spiral = (arm_a * 0.55 + arm_b * 0.45) * window
    spiral = spiral * (0.35 + core_halo * 0.65)

    tone = mix_color(GALAXY_MID, GALAXY_ARM, spiral)
    tone = mix_color(tone, CYAN_MID, spiral * spiral * 0.35)
    color = mix_color(color, tone, clamp(spiral * 0.75))

    # Distant stars + micro dust
    star = hash21(x * 0.19, y * 0.23, phase)
    if star > 0.965 and window > 0.2:
        color = add_glow(color, CYAN_HOT, (star - 0.965) * 18.0)
    if star > 0.992:
        color = add_glow(color, (255, 255, 255), 0.35)

    return color


def geometry_field(x: int, y: int, t: float) -> Tuple[Color, float]:
    phase = 2.0 * math.pi * t
    px = float(x)
    py = float(y)
    angle_y = phase * 1.1
    angle_x = phase * 0.55 + 0.35

    cycle = (math.sin(phase) + 1.0) * 0.5
    glow = 0.0
    if cycle < 0.5:
        morph = smoothstep(cycle * 2.0)
        cube = wireframe_glow(px, py, CUBE_VERTS, CUBE_EDGES, 14.0, angle_y, angle_x)
        octa = wireframe_glow(px, py, OCTAHEDRON_VERTS, OCTAHEDRON_EDGES, 16.0, angle_y, angle_x)
        glow = cube * (1.0 - morph) + octa * morph
    else:
        morph = smoothstep((cycle - 0.5) * 2.0)
        octa = wireframe_glow(px, py, OCTAHEDRON_VERTS, OCTAHEDRON_EDGES, 16.0, angle_y + 0.4, angle_x)
        hex_g = wireframe_glow(px, py, HEX_RING, HEX_EDGES, 15.0, -angle_y * 0.7, angle_x * 0.5)
        glow = octa * (1.0 - morph) + hex_g * morph

    dx = px - CORE_CX
    dy = py - CORE_CY
    facet = math.sin(dx * 0.22 + phase * 2.0) * math.cos(dy * 0.18 - phase * 1.5)
    facet = (facet * 0.5 + 0.5) * math.exp(-(dx * dx + dy * dy) / 520.0) * 0.35

    strength = clamp(glow + facet)
    if strength < 0.02:
        return (0, 0, 0), 0.0

    grad = mix_color(CYAN_DEEP, CYAN_MID, strength * 0.65)
    grad = mix_color(grad, CYAN_BRIGHT, max(0.0, strength - 0.35) * 1.1)
    grad = mix_color(grad, CYAN_HOT, max(0.0, strength - 0.72) * 1.4)
    return grad, strength


def render_core_pixel(x: int, y: int, t: float) -> Color:
    color = galaxy_background(x, y, t)
    geo, strength = geometry_field(x, y, t)
    if strength > 0.0:
        color = mix_color(color, geo, clamp(0.35 + strength * 0.75))

    phase = 2.0 * math.pi * t
    scan = math.sin((y - CORE_CY) * 0.48 + phase * 5.0) * 0.5 + 0.5
    scan *= math.exp(-((x - CORE_CX) ** 2 + (y - CORE_CY) ** 2) / 900.0) * 0.1
    color = add_glow(color, CYAN_BRIGHT, scan)

    # Tech corner brackets (Universe cell motif, cyan)
    dx = abs(x - CORE_CX)
    dy = abs(y - CORE_CY)
    bracket = 0.0
    if 28 < dx < 38 and 24 < dy < 34 and ((x < CORE_CX and y < CORE_CY) or (x > CORE_CX and y > CORE_CY)):
        bracket = 0.45 + 0.25 * math.sin(phase * 3.0)
    if bracket > 0.3:
        color = add_glow(color, CYAN_MID, bracket * 0.25)

    return color


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
        raise AssertionError("form loom loop mismatch: %s pixels" % mismatches)


def generate() -> None:
    template = load_housing_template()
    mask, feather = build_core_mask(template)
    verify_loop(template, mask, feather)

    strip = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        strip.paste(render_frame(template, mask, feather, i), (0, i * FRAME_SIZE))

    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(GENERATED, exist_ok=True)
    png = os.path.join(ITEMS, "data_form_loom_cell.png")
    meta = os.path.join(ITEMS, "data_form_loom_cell.png.mcmeta")
    strip.save(png)
    write_mcmeta(meta)
    strip.save(os.path.join(GENERATED, "data_form_loom_cell.png"))
    write_mcmeta(os.path.join(GENERATED, "data_form_loom_cell.png.mcmeta"))
    save_preview(strip, os.path.join(GENERATED, "data_form_loom_cell_preview.png"))
    print("Generated:", png, strip.size)


if __name__ == "__main__":
    generate()
