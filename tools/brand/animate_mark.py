"""Renders the Loop's write-on animation to assets/brand/airpoint-writeon.gif.

Same choreography as the app's AnimatedLoopMark: the stroke draws along its centerline on a
cubic-bezier(0.65, 0, 0.25, 1) curve, then the point lands on an underdamped spring
(damping 0.42, stiffness 520). Rendered with 3x supersampling.

    python tools/brand/animate_mark.py
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw

from build_brand import DISPLAY, ROOT, build

SIZE = 480
SS = 3
FPS = 30
INK = (13, 15, 20)
ACCENT = (61, 107, 255)

# Timeline (seconds)
STROKE_START, STROKE_DUR = 0.25, 1.15
DOT_START = STROKE_START + 1.05
HOLD_UNTIL = 3.4
FADE = 0.35


def bezier_ease(x1, y1, x2, y2):
    def sample(t, a, b):
        return 3 * a * (1 - t) ** 2 * t + 3 * b * (1 - t) * t ** 2 + t ** 3

    def ease(x):
        lo, hi = 0.0, 1.0
        for _ in range(40):  # solve x(t) = x by bisection
            mid = (lo + hi) / 2
            if sample(mid, x1, x2) < x:
                lo = mid
            else:
                hi = mid
        return sample((lo + hi) / 2, y1, y2)

    return ease


DRAW = bezier_ease(0.65, 0, 0.25, 1)


def spring(t, damping=0.42, stiffness=520.0):
    """Position of a unit-mass spring released from 0 toward 1 (underdamped)."""
    if t <= 0:
        return 0.0
    w0 = math.sqrt(stiffness)
    wd = w0 * math.sqrt(1 - damping ** 2)
    return 1 - math.exp(-damping * w0 * t) * (math.cos(wd * t) + damping * w0 / wd * math.sin(wd * t))


def frame(t, line):
    big = SIZE * SS
    im = Image.new("RGB", (big, big), INK)
    d = ImageDraw.Draw(im)
    k = big / 48 * 0.78
    off = (big - 48 * k) / 2
    fade = 1.0
    if t > HOLD_UNTIL:
        fade = max(0.0, 1 - (t - HOLD_UNTIL) / FADE)
    color = tuple(round(INK[i] + (ACCENT[i] - INK[i]) * fade) for i in range(3))

    progress = DRAW(min(1.0, max(0.0, (t - STROKE_START) / STROKE_DUR)))
    n = len(line)
    reach = progress * (n - 1)
    for i in range(n - 1):
        if i >= reach:
            break
        f = min(1.0, reach - i)
        (x0, y0, w0), (x1, y1, w1) = line[i], line[i + 1]
        xe, ye, w = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, (w0 + (w1 - w0) * f) * k
        a, b = (off + x0 * k, off + y0 * k), (off + xe * k, off + ye * k)
        d.line([a, b], fill=color, width=max(1, round(w)))
        for (cx, cy) in (a, b):  # round caps
            d.ellipse([cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2], fill=color)

    s = spring(t - DOT_START)
    if s > 0:
        cx, cy, r = build(DISPLAY).dot
        r = r * k * s
        cx, cy = off + cx * k, off + cy * k
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    return im.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def main():
    line = build(DISPLAY).centerline
    total = HOLD_UNTIL + FADE + 0.15
    frames = [frame(i / FPS, line) for i in range(int(total * FPS))]
    out = ROOT / "assets" / "brand" / "airpoint-writeon.gif"
    pal = [f.convert("P", palette=Image.Palette.ADAPTIVE, colors=64) for f in frames]
    pal[0].save(out, save_all=True, append_images=pal[1:], duration=round(1000 / FPS), loop=0, optimize=True, disposal=1)
    print("wrote", out.relative_to(ROOT), f"({len(frames)} frames, {out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
