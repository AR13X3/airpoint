"""Builds desktop/assets/airpoint.ico (the .exe icon) from the brand geometry.

    python tools/make_icons.py [--preview out.png]

Sizes of 24 px and under use the small optical cut of the mark.
"""

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from PIL import Image  # noqa: E402

from airpoint import brand  # noqa: E402
from airpoint.raster import render, rounded_square  # noqa: E402
from airpoint.tray import tray_image  # noqa: E402

SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256]


def app_icon(size: int) -> Image.Image:
    small = size <= 24
    stroke, dot = (brand.SMALL_STROKE, brand.SMALL_DOT) if small else (brand.DISPLAY_STROKE, brand.DISPLAY_DOT)
    # Mark at ~62% of the tile (a touch larger at small sizes, where the tile margin eats detail).
    scale = 0.74 if small else 0.62
    offset = 24 * (1 - scale)
    radius = 11 if small else 13
    return render([
        (rounded_square(48, radius), brand.INK, None),
        (stroke, brand.ACCENT, (scale, offset, offset)),
        (dot, brand.ACCENT, (scale, offset, offset)),
    ], size)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="also write a PNG contact sheet here")
    args = parser.parse_args()

    frames = [app_icon(s) for s in SIZES]
    out = ROOT / "assets" / "airpoint.ico"
    out.parent.mkdir(exist_ok=True)
    frames[-1].save(out, format="ICO", sizes=[(s, s) for s in SIZES], append_images=frames[:-1])
    print(f"wrote {out}")

    if args.preview:
        sheet = Image.new("RGBA", (900, 260), (128, 128, 128, 255))
        x = 10
        for f in frames:
            sheet.alpha_composite(f, (x, 10))
            x += f.width + 10
        x = 10
        for light in (False, True):
            bg = (243, 243, 243, 255) if light else (32, 32, 32, 255)
            for state in ("paused", "listening", "connected"):
                tile = Image.new("RGBA", (56, 56), bg)
                for size, off in ((16, 4), (32, 20)):
                    tile.alpha_composite(tray_image(state, light).resize((size, size), Image.Resampling.LANCZOS), (off, off))
                sheet.alpha_composite(tile, (x, 190))
                x += 62
        sheet.save(args.preview)
        print(f"wrote {args.preview}")


if __name__ == "__main__":
    main()
