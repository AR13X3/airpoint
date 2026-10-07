"""A tiny SVG-path rasterizer (M/L/H/V/C/A/Z, absolute) with nonzero fill, built on Pillow.

Pillow has no SVG support, and its polygon fill uses the even-odd rule, which would punch a
hole where the Loop's stroke crosses itself. Shapes are filled with a supersampled nonzero
scanline fill and box-filtered down, which keeps 16 px tray icons crisp without extra
dependencies.
"""

import math
import re

from PIL import Image

_TOKEN = re.compile(r"[MLHVCAZmlhvcaz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def _arc(x1, y1, rx, ry, phi, large, sweep, x2, y2):
    """SVG endpoint arc to points (SVG 1.1 implementation notes, F.6.5)."""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    phi = math.radians(phi)
    c, s = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = c * dx + s * dy, -s * dx + c * dy
    rx, ry = abs(rx), abs(ry)
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        coef = -coef
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx = c * cxp - s * cyp + (x1 + x2) / 2
    cy = s * cxp + c * cyp + (y1 + y2) / 2

    def angle(ux, uy, vx, vy):
        return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)

    ux, uy = (x1p - cxp) / rx, (y1p - cyp) / ry
    vx, vy = (-x1p - cxp) / rx, (-y1p - cyp) / ry
    th1 = angle(1, 0, ux, uy)
    dth = angle(ux, uy, vx, vy)
    if not sweep and dth > 0:
        dth -= 2 * math.pi
    elif sweep and dth < 0:
        dth += 2 * math.pi
    n = max(2, int(abs(dth) / (math.pi / 24)) + 1)
    pts = []
    for k in range(1, n + 1):
        t = th1 + dth * k / n
        px, py = rx * math.cos(t), ry * math.sin(t)
        pts.append((c * px - s * py + cx, s * px + c * py + cy))
    return pts


def flatten(d, scale=1.0, tx=0.0, ty=0.0, curve_steps=10):
    """Flatten an absolute SVG path into closed polygons, applying scale then translate."""
    toks = _TOKEN.findall(d)
    i = 0
    polys, cur = [], []
    x = y = sx = sy = 0.0
    cmd = None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                if cur:
                    polys.append(cur)
                cur = []
                x, y = sx, sy
                continue
        if cmd == "M":
            if cur:
                polys.append(cur)
            x, y = num(), num()
            sx, sy = x, y
            cur = [(x, y)]
            cmd = "L"  # further coordinate pairs are implicit line-tos
        elif cmd == "L":
            x, y = num(), num()
            cur.append((x, y))
        elif cmd == "H":
            x = num()
            cur.append((x, y))
        elif cmd == "V":
            y = num()
            cur.append((x, y))
        elif cmd == "C":
            x1, y1, x2, y2, x3, y3 = (num() for _ in range(6))
            for k in range(1, curve_steps + 1):
                t = k / curve_steps
                u = 1 - t
                cur.append((u ** 3 * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x3,
                            u ** 3 * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y3))
            x, y = x3, y3
        elif cmd == "A":
            rx, ry, phi, large, sweep, x2, y2 = (num() for _ in range(7))
            cur.extend(_arc(x, y, rx, ry, phi, int(large), int(sweep), x2, y2))
            x, y = x2, y2
        else:
            raise ValueError(f"unsupported path command {cmd!r}")
    if cur:
        polys.append(cur)
    return [[(px * scale + tx, py * scale + ty) for px, py in p] for p in polys]


def _coverage(polys, w, h):
    """Nonzero-winding scanline fill of polygons (pixel units) into an L-mode mask."""
    edges = []
    for poly in polys:
        n = len(poly)
        for k in range(n):
            (ax, ay), (bx, by) = poly[k], poly[(k + 1) % n]
            if ay == by:
                continue
            wind = 1 if by > ay else -1
            if ay > by:
                ax, ay, bx, by = bx, by, ax, ay
            edges.append((ay, by, ax, (bx - ax) / (by - ay), wind))
    buf = bytearray(w * h)
    for row in range(h):
        sy = row + 0.5
        xs = [(ax + (sy - ay) * slope, wind) for ay, by, ax, slope, wind in edges if ay <= sy < by]
        if not xs:
            continue
        xs.sort()
        winding = 0
        off = row * w
        for k in range(len(xs) - 1):
            winding += xs[k][1]
            if winding:
                x0 = max(0, math.ceil(xs[k][0] - 0.5))
                x1 = min(w, math.ceil(xs[k + 1][0] - 0.5))
                if x1 > x0:
                    buf[off + x0:off + x1] = b"\xff" * (x1 - x0)
    return Image.frombytes("L", (w, h), bytes(buf))


def render(layers, size, view=48.0, supersample=4):
    """Render [(path_d, (r, g, b[, a]), transform)] onto a transparent size x size RGBA image.

    `transform` is (scale, tx, ty) in view units, or None. Paths are drawn in a `view`-unit
    square box (48 for the brand grid).
    """
    big = size * supersample
    k = big / view
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for d, color, transform in layers:
        s, tx, ty = transform or (1.0, 0.0, 0.0)
        polys = flatten(d, scale=s * k, tx=tx * k, ty=ty * k)
        mask = _coverage(polys, big, big).resize((size, size), Image.Resampling.BOX)
        rgba = tuple(color) + ((255,) if len(color) == 3 else ())
        if rgba[3] != 255:
            mask = mask.point(lambda v, a=rgba[3]: v * a // 255)
        layer = Image.new("RGBA", (size, size), rgba[:3] + (255,))
        layer.putalpha(mask)
        out = Image.alpha_composite(out, layer)
    return out


def rounded_square(size=48.0, radius=14.0):
    """Path for a rounded square filling the view box."""
    r, s = radius, size
    return (f"M{r} 0L{s - r} 0A{r} {r} 0 0 1 {s} {r}L{s} {s - r}A{r} {r} 0 0 1 {s - r} {s}"
            f"L{r} {s}A{r} {r} 0 0 1 0 {s - r}L0 {r}A{r} {r} 0 0 1 {r} 0Z")
