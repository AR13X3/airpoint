"""Brand geometry and colors, shared by the tray icon and the icon build script.

The mark is the "Loop": a handwritten air gesture that lifts off into a point. It comes in
two optical cuts on a 48x48 grid. Use SMALL at 24 px and below (tray, notifications) and
DISPLAY above that. Each cut is split into the stroke and the point so the point can carry
a status color on its own.
"""

from ._mark import DISPLAY_DOT, DISPLAY_STROKE, SMALL_DOT, SMALL_STROKE  # generated geometry

__all__ = ["DISPLAY_STROKE", "DISPLAY_DOT", "SMALL_STROKE", "SMALL_DOT",
           "INK", "PAPER", "ACCENT", "ACCENT_ON_DARK", "LIVE", "MUTED"]

# Palette (sRGB)
INK = (13, 15, 20)            # #0D0F14
PAPER = (244, 244, 241)       # #F4F4F1
ACCENT = (61, 107, 255)       # #3D6BFF  Electric
ACCENT_ON_DARK = (110, 146, 255)  # lifted accent for dark surfaces
LIVE = (61, 214, 140)         # #3DD68C  connected
MUTED = (138, 144, 156)       # #8A909C
