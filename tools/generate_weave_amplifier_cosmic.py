#!/usr/bin/env python3
"""Generate 128x128 cosmic stardust animations for weave amplifier cards."""

from __future__ import annotations

import json
import math
import os
import random
from dataclasses import dataclass
from typing import Dict, List, Sequence, Tuple

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/advancedatamonitor/textures")
ITEMS = os.path.join(ASSETS, "items")
GENERATED = os.path.join(ASSETS, "review/generated")

FRAME_SIZE = 128
FRAME_COUNT = 20
FACE_CX = 79.5
FACE_CY = 63.5

Color = Tuple[int, int, int]
Rgba = Tuple[int, int, int, int]


@dataclass(frozen=True)
class CosmicPalette:
    name: str
    void_inner: Color
    void_outer: Color
    nebula_deep: Color
    nebula_bright: Color
    dust_primary: Color
    dust_spark: Color
    core_glow: Color
    core_hot: Color
    halo_inner: Color
    halo_outer: Color
    scan_glow: Color
    star_tones: Tuple[str, ...]
    star_seed: int
    orbits: Tuple[Dict, ...]


PALETTE_CYAN = CosmicPalette(
    name="cyan",
    void_inner=(4, 6, 18),
    void_outer=(14, 18, 42),
    nebula_deep=(52, 24, 110),
    nebula_bright=(18, 46, 130),
    dust_primary=(180, 210, 255),
    dust_spark=(255, 230, 170),
    core_glow=(120, 240, 255),
    core_hot=(255, 255, 255),
    halo_inner=(170, 90, 255),
    halo_outer=(60, 200, 255),
    scan_glow=(90, 180, 255),
    star_tones=("white", "cyan", "gold", "violet"),
    star_seed=429001,
    orbits=(
        {"rx": 32.0, "ry": 23.0, "tilt": 0.55, "speed": 1.0, "dots": 6, "hue": (90, 230, 255)},
        {"rx": 26.0, "ry": 19.0, "tilt": -0.95, "speed": -1.35, "dots": 4, "hue": (190, 110, 255)},
        {"rx": 38.0, "ry": 27.0, "tilt": 1.35, "speed": 0.75, "dots": 5, "hue": (255, 245, 255)},
    ),
)

PALETTE_PURPLE = CosmicPalette(
    name="purple",
    void_inner=(14, 4, 26),
    void_outer=(34, 10, 52),
    nebula_deep=(90, 18, 130),
    nebula_bright=(150, 40, 190),
    dust_primary=(230, 160, 255),
    dust_spark=(255, 190, 240),
    core_glow=(220, 100, 255),
    core_hot=(255, 210, 255),
    halo_inner=(255, 70, 210),
    halo_outer=(160, 50, 255),
    scan_glow=(200, 90, 255),
    star_tones=("white", "violet", "magenta", "pink"),
    star_seed=429002,
    orbits=(
        {"rx": 32.0, "ry": 23.0, "tilt": 0.55, "speed": 1.05, "dots": 6, "hue": (210, 80, 255)},
        {"rx": 26.0, "ry": 19.0, "tilt": -0.95, "speed": -1.4, "dots": 4, "hue": (255, 110, 230)},
        {"rx": 38.0, "ry": 27.0, "tilt": 1.35, "speed": 0.8, "dots": 5, "hue": (180, 60, 255)},
    ),
)

CARD_OUTPUTS = (
    ("weave_amplifier", "weave_amplifier_base.png", PALETTE_CYAN, "weave_amplifier_preview.png"),
    ("super_weave_amplifier", "weave_amplifier_base.png", PALETTE_PURPLE, "super_weave_amplifier_preview.png"),
)


def clamp(value: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, value))


def mix_color(a: Color, b: Color, t: float) -> Color:
    t = clamp(t)
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def add_glow(base: Color, glow: Color, strength: float) -> Color:
    strength = clamp(strength)
    return tuple(min(255, int(base[i] + glow[i] * strength)) for i in range(3))


def hash21(x: float, y: float, seed: float) -> float:
    return (math.sin(x * 12.9898 + y * 78.233 + seed * 43.758) * 43758.5453) % 1.0


def fbm(x: float, y: float, seed: float, octaves: int = 4) -> float:
    value = 0.0
    amp = 0.55
    freq = 1.0
    norm = 0.0
    for i in range(octaves):
        value += amp * math.sin(x * freq + seed + i * 1.7) * math.cos(y * freq * 0.93 - seed * 0.6 + i)
        norm += amp
        amp *= 0.52
        freq *= 2.05
    return value / norm * 0.5 + 0.5


def build_star_field(palette: CosmicPalette) -> List[Tuple[int, int, float, float, str]]:
    rng = random.Random(palette.star_seed)
    stars: List[Tuple[int, int, float, float, str]] = []
    for _ in range(140):
        stars.append(
            (
                rng.randint(52, 108),
                rng.randint(22, 105),
                rng.random(),
                rng.uniform(0.35, 1.0),
                rng.choice(palette.star_tones),
            )
        )
    return stars


def star_color(tone: str, palette: CosmicPalette) -> Color:
    if tone == "cyan":
        return (130, 230, 255)
    if tone == "gold":
        return (255, 220, 150)
    if tone == "violet":
        return (210, 160, 255)
    if tone == "magenta":
        return (255, 120, 240)
    if tone == "pink":
        return (255, 180, 230)
    return (245, 250, 255)


def load_blank_template(base_filename: str) -> Image.Image:
    base_path = os.path.join(ITEMS, base_filename)
    if os.path.isfile(base_path):
        return Image.open(base_path).convert("RGBA")
    fallback = os.path.join(ITEMS, "weave_amplifier.png")
    image = Image.open(fallback).convert("RGBA")
    frame = image.crop((0, 0, FRAME_SIZE, FRAME_SIZE)) if image.size[1] > FRAME_SIZE else image
    os.makedirs(ITEMS, exist_ok=True)
    frame.save(base_path)
    return frame


def is_interior_pixel(r: int, g: int, b: int, a: int, x: int) -> bool:
    if a < 8 or x < 36:
        return False
    return abs(r - g) < 12 and abs(g - b) < 12 and 135 <= r <= 200


def build_face_mask(template: Image.Image) -> Tuple[List[List[bool]], List[List[float]]]:
    px = template.load()
    mask = [[False] * FRAME_SIZE for _ in range(FRAME_SIZE)]
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            mask[y][x] = is_interior_pixel(*px[x, y], x)

    dist = [[0.0] * FRAME_SIZE for _ in range(FRAME_SIZE)]
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            if not mask[y][x]:
                continue
            nearest = 99
            for oy in range(max(0, y - 6), min(FRAME_SIZE, y + 7)):
                for ox in range(max(0, x - 6), min(FRAME_SIZE, x + 7)):
                    if not mask[oy][ox]:
                        nearest = min(nearest, abs(ox - x) + abs(oy - y))
            dist[y][x] = float(min(nearest, 6))
    return mask, dist


def cosmic_pixel(x: int, y: int, t: float, palette: CosmicPalette) -> Color:
    dx = (x - FACE_CX) / 34.0
    dy = (y - FACE_CY) / 34.0
    radius = math.hypot(dx, dy)
    theta = math.atan2(dy, dx)
    phase = 2.0 * math.pi * t

    void = clamp(1.15 - radius * 1.05)
    color = mix_color(palette.void_inner, palette.void_outer, void)

    cloud_a = fbm(
        dx * 2.8 + math.cos(phase * 0.9) * 0.55,
        dy * 2.6 + math.sin(phase * 0.9) * 0.55,
        phase,
    )
    cloud_b = fbm(
        dx * 3.6 - math.sin(phase * 0.65) * 0.75,
        dy * 3.1 + math.cos(phase * 0.45) * 0.65,
        phase + 2.4,
    )
    nebula = clamp((cloud_a * 0.58 + cloud_b * 0.52) - 0.42)
    nebula *= clamp(1.0 - radius * 0.92)

    deep = tuple(palette.nebula_deep[i] + int(nebula * (90, 40, 90)[i]) for i in range(3))
    bright = tuple(palette.nebula_bright[i] + int(nebula * (60, 50, 70)[i]) for i in range(3))
    color = mix_color(color, mix_color(deep, bright, cloud_b), nebula * 0.88)

    arms = math.sin(theta * 3.0 - phase * 4.0 + radius * 9.0) * 0.5 + 0.5
    lane_mask = math.exp(-radius * radius * 1.55) * (0.45 + 0.55 * math.sin(phase * 2.0 + radius * 4.0))
    dust = arms * lane_mask
    color = add_glow(color, palette.dust_primary, dust * 0.58)
    color = add_glow(color, palette.dust_spark, dust * hash21(x * 0.17, y * 0.23, phase) * 0.28)

    core = math.exp(-radius * radius * 3.8) * (0.55 + 0.45 * math.cos(phase * 2.0))
    halo = math.exp(-((radius - 0.24) ** 2) * 95.0) * (0.45 + 0.55 * math.sin(phase * 3.0 - theta * 2.0))
    color = add_glow(color, palette.core_glow, core * 0.95)
    color = add_glow(color, palette.core_hot, core * 0.55)
    color = add_glow(color, palette.halo_inner, halo * 0.68)
    color = add_glow(color, palette.halo_outer, halo * 0.38)

    scan = math.sin((y - FACE_CY) * 0.55 + phase * 6.0) * 0.5 + 0.5
    scan *= math.exp(-radius * radius * 1.2) * 0.12
    color = add_glow(color, palette.scan_glow, scan)

    return color


def draw_star_layer(frame: Image.Image, frame_idx: int, stars, palette: CosmicPalette) -> None:
    px = frame.load()
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    phase = 2.0 * math.pi * t
    for sx, sy, star_phase, intensity, tone in stars:
        twinkle = 0.25 + 0.75 * max(0.0, math.sin(phase * 3.0 + star_phase * 6.28318))
        strength = intensity * twinkle
        if strength < 0.2:
            continue
        base = star_color(tone, palette)
        drift = int(math.sin(phase + star_phase * 12.0) * 1.2)
        x = sx + drift
        y = sy + int(math.cos(phase * 0.7 + star_phase * 9.0) * 1.0)
        for ox, oy, scale in ((0, 0, 1.0), (1, 0, 0.45), (-1, 0, 0.45), (0, 1, 0.45), (0, -1, 0.45)):
            px_x = x + ox
            px_y = y + oy
            if not (0 <= px_x < FRAME_SIZE and 0 <= px_y < FRAME_SIZE):
                continue
            r, g, b, a = px[px_x, px_y]
            glow = add_glow((r, g, b), base, strength * scale)
            px[px_x, px_y] = (glow[0], glow[1], glow[2], a)


def draw_orbit_layer(frame: Image.Image, frame_idx: int, palette: CosmicPalette) -> None:
    px = frame.load()
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    phase = 2.0 * math.pi * t
    for orbit in palette.orbits:
        rx = orbit["rx"]
        ry = orbit["ry"]
        tilt = orbit["tilt"]
        speed = orbit["speed"]
        dots = orbit["dots"]
        hue = orbit["hue"]
        for i in range(dots):
            ang = phase * speed + (2.0 * math.pi * i / dots)
            ox = math.cos(ang) * rx
            oy = math.sin(ang) * ry
            rot_x = ox * math.cos(tilt) - oy * math.sin(tilt)
            rot_y = ox * math.sin(tilt) + oy * math.cos(tilt)
            x = int(FACE_CX + rot_x)
            y = int(FACE_CY + rot_y)
            for tx, ty, falloff in ((0, 0, 1.0), (-1, 0, 0.55), (1, 0, 0.35)):
                px_x = x + tx
                px_y = y + ty
                if not (0 <= px_x < FRAME_SIZE and 0 <= px_y < FRAME_SIZE):
                    continue
                r, g, b, a = px[px_x, px_y]
                glow = add_glow((r, g, b), hue, falloff)
                px[px_x, px_y] = (
                    min(255, glow[0] + 40),
                    min(255, glow[1] + 40),
                    min(255, glow[2] + 40),
                    a,
                )


def render_cosmic_frame(
    template: Image.Image,
    mask: Sequence[Sequence[bool]],
    feather: Sequence[Sequence[float]],
    frame_idx: int,
    stars,
    palette: CosmicPalette,
) -> Image.Image:
    t = (frame_idx % FRAME_COUNT) / FRAME_COUNT
    frame = template.copy()
    px = frame.load()
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            if not mask[y][x]:
                continue
            cosmic = cosmic_pixel(x, y, t, palette)
            edge = clamp(feather[y][x] / 4.0)
            tr, tg, tb, ta = px[x, y]
            blend = mix_color((tr, tg, tb), cosmic, 0.92 * edge + 0.08)
            px[x, y] = (blend[0], blend[1], blend[2], ta)
    draw_orbit_layer(frame, frame_idx, palette)
    draw_star_layer(frame, frame_idx, stars, palette)
    return frame


def write_mcmeta(path: str) -> None:
    meta = {
        "animation": {
            "frametime": 2,
            "interpolate": True,
            "width": FRAME_SIZE,
            "height": FRAME_SIZE,
        }
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(meta, handle, indent=2)
        handle.write("\n")


def save_preview_strip(strip: Image.Image, path: str) -> None:
    preview = Image.new("RGBA", (FRAME_SIZE * FRAME_COUNT, FRAME_SIZE), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        preview.paste(strip.crop((0, i * FRAME_SIZE, FRAME_SIZE, (i + 1) * FRAME_SIZE)), (i * FRAME_SIZE, 0))
    preview.save(path)


def verify_loop(template, mask, feather, stars, palette) -> None:
    first = render_cosmic_frame(template, mask, feather, 0, stars, palette)
    wrap = render_cosmic_frame(template, mask, feather, FRAME_COUNT, stars, palette)
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
        raise AssertionError("%s loop mismatch pixels: %s" % (palette.name, mismatches))


def generate_card(stem: str, base_filename: str, palette: CosmicPalette, preview_name: str) -> None:
    template = load_blank_template(base_filename)
    mask, feather = build_face_mask(template)
    stars = build_star_field(palette)
    verify_loop(template, mask, feather, stars, palette)

    strip = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), (0, 0, 0, 0))
    for i in range(FRAME_COUNT):
        strip.paste(render_cosmic_frame(template, mask, feather, i, stars, palette), (0, i * FRAME_SIZE))

    item_png = os.path.join(ITEMS, stem + ".png")
    item_meta = os.path.join(ITEMS, stem + ".png.mcmeta")
    strip.save(item_png)
    write_mcmeta(item_meta)
    strip.save(os.path.join(GENERATED, stem + ".png"))
    write_mcmeta(os.path.join(GENERATED, stem + ".png.mcmeta"))
    save_preview_strip(strip, os.path.join(GENERATED, preview_name))
    print("Generated:", item_png, strip.size, palette.name)


def generate() -> None:
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(GENERATED, exist_ok=True)
    for stem, base_file, palette, preview in CARD_OUTPUTS:
        generate_card(stem, base_file, palette, preview)


if __name__ == "__main__":
    generate()
