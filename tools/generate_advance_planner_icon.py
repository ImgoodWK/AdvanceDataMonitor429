#!/usr/bin/env python3
"""Generate 64x64 Advance Planner item icon."""

from __future__ import annotations

import os
from typing import Tuple

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(
    ROOT,
    "src/main/resources/assets/advancedatamonitor/textures/items/advance_planner.png",
)

SIZE = 64
Color = Tuple[int, int, int, int]

TRANSPARENT = (0, 0, 0, 0)
BODY_DARK = (24, 30, 46, 255)
BODY_MID = (38, 46, 68, 255)
BODY_LIGHT = (58, 68, 92, 255)
METAL = (150, 158, 172, 255)
METAL_DARK = (95, 102, 118, 255)
CYAN = (40, 210, 235, 255)
CYAN_BRIGHT = (130, 245, 255, 255)
CYAN_DIM = (18, 120, 150, 255)
GOLD = (255, 196, 72, 255)
CHECK = (90, 255, 170, 255)
SCREEN = (12, 16, 28, 255)
LINE = (170, 185, 200, 255)


def blend(a: Color, b: Color, t: float) -> Color:
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def draw_planner() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
    px = img.load()

    def fill_rect(x0: int, y0: int, x1: int, y1: int, color: Color) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if 0 <= x < SIZE and 0 <= y < SIZE:
                    px[x, y] = color

    def plot(x: int, y: int, color: Color) -> None:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[x, y] = color

    # Soft drop shadow
    for y in range(52, 60):
        for x in range(16, 50):
            dist = abs(x - 33) / 18.0 + abs(y - 56) / 6.0
            if dist < 1.0:
                plot(x, y, (0, 0, 0, int(50 * (1.0 - dist))))

    # Main tablet body (isometric-ish slab)
    fill_rect(14, 18, 49, 53, BODY_DARK)
    fill_rect(15, 17, 48, 52, BODY_MID)
    # Top bevel
    for x in range(16, 49):
        plot(x, 16, BODY_LIGHT)
        plot(x, 17, blend(BODY_MID, BODY_LIGHT, 0.45))
    # Side depth
    for y in range(18, 54):
        plot(49, y, BODY_DARK)
        plot(50, y, (18, 22, 34, 255))
    for y in range(19, 54):
        plot(13, y, (16, 20, 32, 255))

    # Metal corner brackets (ADM family style)
    bracket = [
        (15, 18), (16, 18), (17, 18), (15, 19), (15, 20),
        (48, 18), (47, 18), (46, 18), (48, 19), (48, 20),
        (15, 52), (16, 52), (17, 52), (15, 51), (15, 50),
        (48, 52), (47, 52), (46, 52), (48, 51), (48, 50),
    ]
    for x, y in bracket:
        plot(x, y, METAL)
        if x in (16, 47):
            plot(x, y, blend(METAL, METAL_DARK, 0.3))

    # Top clip / hinge
    fill_rect(26, 11, 37, 16, METAL_DARK)
    fill_rect(27, 10, 36, 15, METAL)
    fill_rect(29, 9, 34, 12, CYAN_DIM)
    plot(31, 10, CYAN_BRIGHT)
    plot(32, 10, CYAN_BRIGHT)

    # Screen inset
    fill_rect(18, 20, 47, 50, SCREEN)
    fill_rect(19, 21, 46, 49, (16, 20, 34, 255))

    # Header bar
    fill_rect(19, 21, 46, 24, CYAN_DIM)
    fill_rect(20, 22, 45, 23, CYAN)
    for x in range(22, 44, 3):
        plot(x, 22, CYAN_BRIGHT)

    # Small schedule dots (planner identity)
    for dx in (24, 28, 32, 36, 40):
        plot(dx, 23, GOLD if dx % 4 == 0 else (255, 220, 120, 255))

    # Task rows: checkbox + text line
    rows = [
        (27, True),
        (32, False),
        (37, True),
        (42, False),
    ]
    for row_y, checked in rows:
        # checkbox
        for dy in range(3):
            for dx in range(3):
                c = CYAN if dx == 0 or dy == 0 or dx == 2 or dy == 2 else SCREEN
                plot(21 + dx, row_y + dy, c)
        if checked:
            plot(22, row_y + 1, CHECK)
            plot(22, row_y + 2, CHECK)
            plot(23, row_y + 2, CHECK)
            plot(23, row_y + 1, CHECK)
        # text lines
        length = 18 if checked else 14
        for lx in range(length):
            tone = blend(LINE, CYAN_BRIGHT, 0.15 if checked else 0.0)
            plot(26 + lx, row_y + 1, tone)
            if lx > 2:
                plot(26 + lx, row_y + 2, blend(tone, SCREEN, 0.55))

    # Priority marker — small gold pin top-right of screen
    for dy in range(4):
        for dx in range(4):
            if dx + dy <= 4:
                plot(43 + dx, 22 + dy, GOLD if dx + dy < 3 else blend(GOLD, SCREEN, 0.4))

    # Bottom data glow strip (matches manual / mod cyan accent)
    for y in range(47, 51):
        glow = blend(CYAN_DIM, CYAN_BRIGHT, (y - 47) / 4.0)
        for x in range(20, 46):
            wave = (x + y) % 3
            if wave != 0:
                plot(x, y, glow)

    # Edge highlight
    for x in range(18, 48):
        plot(x, 18, blend(BODY_LIGHT, CYAN, 0.12))
    for y in range(20, 51):
        plot(18, y, blend(BODY_MID, CYAN, 0.08))

    return img


def main() -> None:
    img = draw_planner()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("Generated:", OUT, img.size)


if __name__ == "__main__":
    main()
