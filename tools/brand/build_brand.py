"""Single source of truth for the Airpoint mark (the "Loop").

The Loop is a prolate trochoid (a point on a rolling circle, extended past the rim so it
loops once), stroked with a taper that thickens toward the end, followed by a gap and a
point. This script derives every asset from those parameters:

    assets/brand/*.svg                         mark and app icon artwork
    android/app/src/main/res/drawable/*.xml    launcher, themed, notification and UI vectors
    android/.../ui/brand/LoopGeometry.kt       centerline used to animate the mark writing itself
    desktop/airpoint/_mark.py                  outline paths for the tray icon

Run from the repo root:  python tools/brand/build_brand.py
"""

import math
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

INK = "#0D0F14"
PAPER = "#F4F4F1"
ACCENT = "#3D6BFF"


@dataclass(frozen=True)
class Cut:
    """One optical cut of the mark. DISPLAY is for 32 px and up; SMALL is heavier and more
    open so it survives 16-24 px."""
    d: float = 1.9          # trochoid pen distance (loop size; r is fixed at 1)
    t0: float = -2.35       # start / end parameters
    t1: float = 3.2
    rotate: float = -12.0   # degrees
    w0: float = 1.8         # stroke width at start / end (pre-normalisation units)
    w1: float = 6.0
    taper: float = 1.2      # width easing exponent
    gap: float = 2.8        # air between stroke and point
    dot_r: float = 4.4
    target: float = 37.0    # longest side inside the 48 box


DISPLAY = Cut()
SMALL = Cut(d=2.05, t0=-2.25, w0=3.6, w1=7.6, taper=0.9, gap=3.0, dot_r=5.2, target=40.0)


@dataclass
class Mark:
    centerline: list   # [(x, y, width)] in the 48 box
    left: list
    right: list
    dot: tuple         # (cx, cy, r)


def _fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def build(cut: Cut, samples: int = 26, centerline_samples: int = 72) -> Mark:
    ts = [cut.t0 + (cut.t1 - cut.t0) * i / 400 for i in range(401)]
    pts = [(t - cut.d * math.sin(t), 1 - cut.d * math.cos(t)) for t in ts]
    a = math.radians(cut.rotate)
    pts = [(7.2 * (x * math.cos(a) - y * math.sin(a)), 7.2 * (x * math.sin(a) + y * math.cos(a))) for x, y in pts]

    # Resample evenly by arc length so curve nodes are evenly spaced.
    acc = [0.0]
    for p, q in zip(pts, pts[1:]):
        acc.append(acc[-1] + math.dist(p, q))

    def at(s):
        for i in range(1, len(acc)):
            if acc[i] >= s:
                f = (s - acc[i - 1]) / ((acc[i] - acc[i - 1]) or 1)
                return (pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f,
                        pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f)
        return pts[-1]

    center = [at(acc[-1] * i / samples) for i in range(samples + 1)]
    widths = [cut.w0 + (cut.w1 - cut.w0) * (i / samples) ** cut.taper for i in range(samples + 1)]
    left, right = [], []
    for i, (x, y) in enumerate(center):
        p, q = center[max(i - 1, 0)], center[min(i + 1, samples)]
        L = math.dist(p, q)
        nx, ny = -(q[1] - p[1]) / L, (q[0] - p[0]) / L
        hw = widths[i] / 2
        left.append((x + nx * hw, y + ny * hw))
        right.append((x - nx * hw, y - ny * hw))
    ang = math.atan2(center[-1][1] - center[-2][1], center[-1][0] - center[-2][0])
    reach = cut.w1 / 2 + cut.gap + cut.dot_r
    dot = (center[-1][0] + math.cos(ang) * reach, center[-1][1] + math.sin(ang) * reach, cut.dot_r)

    # Normalise into the 48 box (the end cap's reach counts toward the width).
    xs = ([p[0] for p in left + right] + [dot[0] - dot[2], dot[0] + dot[2]]
          + [center[-1][0] + math.cos(ang) * cut.w1 / 2])
    ys = [p[1] for p in left + right] + [dot[1] - dot[2], dot[1] + dot[2]]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    s = cut.target / max(x1 - x0, y1 - y0)
    ox, oy = (48 - (x1 - x0) * s) / 2 - x0 * s, (48 - (y1 - y0) * s) / 2 - y0 * s

    def T(p):
        return (p[0] * s + ox, p[1] * s + oy)

    fine = [at(acc[-1] * i / centerline_samples) for i in range(centerline_samples + 1)]
    fine_w = [cut.w0 + (cut.w1 - cut.w0) * (i / centerline_samples) ** cut.taper
              for i in range(centerline_samples + 1)]
    return Mark(
        centerline=[(*T(c), w * s) for c, w in zip(fine, fine_w)],
        left=[T(p) for p in left],
        right=[T(p) for p in right],
        dot=(dot[0] * s + ox, dot[1] * s + oy, dot[2] * s),
    )


def _spline(points):
    """Uniform Catmull-Rom through points, as cubic Bezier commands."""
    out, n = "", len(points)
    for i in range(n - 1):
        p0, p1, p2 = points[max(i - 1, 0)], points[i], points[i + 1]
        p3 = points[min(i + 2, n - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        out += f"C{_fmt(c1[0])} {_fmt(c1[1])} {_fmt(c2[0])} {_fmt(c2[1])} {_fmt(p2[0])} {_fmt(p2[1])}"
    return out


def stroke_path(m: Mark) -> str:
    L, R = m.left, m.right[::-1]
    end_r, start_r = m.centerline[-1][2] / 2, m.centerline[0][2] / 2
    return (f"M{_fmt(L[0][0])} {_fmt(L[0][1])}" + _spline(L)
            + f"A{_fmt(end_r)} {_fmt(end_r)} 0 0 0 {_fmt(R[0][0])} {_fmt(R[0][1])}" + _spline(R)
            + f"A{_fmt(start_r)} {_fmt(start_r)} 0 0 0 {_fmt(L[0][0])} {_fmt(L[0][1])}Z")


def dot_path(m: Mark) -> str:
    cx, cy, r = m.dot
    return (f"M{_fmt(cx - r)} {_fmt(cy)}A{_fmt(r)} {_fmt(r)} 0 1 1 {_fmt(cx + r)} {_fmt(cy)}"
            f"A{_fmt(r)} {_fmt(r)} 0 1 1 {_fmt(cx - r)} {_fmt(cy)}Z")


# ---------------------------------------------------------------- writers

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    print("wrote", path.relative_to(ROOT))


def svg(paths, fill, size=48, bg=None, radius=0, transform=None):
    rect = f'<rect width="48" height="48" rx="{radius}" fill="{bg}"/>' if bg else ""
    g0, g1 = (f'<g transform="{transform}">', "</g>") if transform else ("", "")
    body = "".join(f'<path d="{p}" fill="{fill}"/>' for p in paths)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 48 48">'
            f'{rect}{g0}{body}{g1}</svg>\n')


def vector(paths, color, viewport=48, size_dp=24, scale=1.0, translate=0.0, comment=""):
    body = "".join(f'\n        <path android:fillColor="{color}" android:pathData="{p}" />' for p in paths)
    head = f"<!-- {comment} -->\n" if comment else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n{head}'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n'
            f'    <group android:scaleX="{scale}" android:scaleY="{scale}"'
            f' android:translateX="{translate}" android:translateY="{translate}">{body}\n    </group>\n'
            f'</vector>\n')


def main():
    display, small = build(DISPLAY), build(SMALL)
    d_stroke, d_dot = stroke_path(display), dot_path(display)
    s_stroke, s_dot = stroke_path(small), dot_path(small)

    # --- SVG artwork
    brand = ROOT / "assets" / "brand"
    write(brand / "airpoint-mark.svg", svg([d_stroke, d_dot], ACCENT))
    write(brand / "airpoint-mark-small.svg", svg([s_stroke, s_dot], ACCENT))
    write(brand / "airpoint-mark-ink.svg", svg([d_stroke, d_dot], INK))
    write(brand / "airpoint-mark-paper.svg", svg([d_stroke, d_dot], PAPER))
    write(brand / "airpoint-app-icon.svg",
          svg([d_stroke, d_dot], ACCENT, size=512, bg=INK, radius=11, transform="translate(9.12 9.12) scale(0.62)"))

    # --- Android vectors. Adaptive icons draw on a 108 dp canvas with a 66 dp safe zone.
    res = ROOT / "android" / "app" / "src" / "main" / "res" / "drawable"
    note = "Generated by tools/brand/build_brand.py. Do not edit by hand."
    icon_scale, icon_shift = 1.4, (108 - 48 * 1.4) / 2
    write(res / "ic_launcher_foreground.xml",
          vector([d_stroke, d_dot], ACCENT, 108, 108, icon_scale, round(icon_shift, 2), note))
    write(res / "ic_launcher_monochrome.xml",
          vector([d_stroke, d_dot], "#FFFFFFFF", 108, 108, icon_scale, round(icon_shift, 2), note))
    write(res / "ic_mark.xml", vector([d_stroke, d_dot], "#FFFFFFFF", 48, 48, comment=note))
    write(res / "ic_stat_airpoint.xml", vector([s_stroke, s_dot], "#FFFFFFFF", 48, 24, comment=note))
    splash_scale = 1.15  # the splash icon is masked to a circle of 2/3 its size
    write(res / "ic_splash.xml",
          vector([d_stroke, d_dot], ACCENT, 108, 108, splash_scale, round((108 - 48 * splash_scale) / 2, 2), note))

    # --- Compose centerline for the write-on animation
    pts = ",\n        ".join(f"{_fmt(x)}f, {_fmt(y)}f, {_fmt(w)}f" for x, y, w in build(DISPLAY, samples=72).centerline)
    cx, cy, r = display.dot
    kt = f"""// Generated by tools/brand/build_brand.py. Do not edit by hand.
package io.github.ar13x3.airpoint.ui.brand

/** The Loop's centerline on the 48-unit brand grid as (x, y, strokeWidth) triples, plus the point. */
internal object LoopGeometry {{
    val centerline = floatArrayOf(
        {pts},
    )
    const val DOT_X = {_fmt(cx)}f
    const val DOT_Y = {_fmt(cy)}f
    const val DOT_R = {_fmt(r)}f
}}
"""
    write(ROOT / "android/app/src/main/kotlin/io/github/ar13x3/airpoint/ui/brand/LoopGeometry.kt", kt)

    # --- Desktop tray paths
    py = f'''"""Generated by tools/brand/build_brand.py. Do not edit by hand."""

DISPLAY_STROKE = "{d_stroke}"
DISPLAY_DOT = "{d_dot}"
SMALL_STROKE = "{s_stroke}"
SMALL_DOT = "{s_dot}"
'''
    write(ROOT / "desktop" / "airpoint" / "_mark.py", py)


if __name__ == "__main__":
    main()
