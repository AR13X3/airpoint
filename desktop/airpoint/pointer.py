"""Mouse output with eased motion.

Phones send motion in bursts (one coalesced frame every ~12 ms over Wi-Fi, with jitter).
Applying each burst directly makes the cursor stutter, so deltas are queued and drained
at 200 Hz: every tick moves a fixed fraction (alpha) of the remaining distance, then carries
sub-pixel remainders so slow movements aren't lost to rounding.
"""

import asyncio
import ctypes
import logging
import math
import sys

log = logging.getLogger("airpoint")

TICK = 0.005          # 200 Hz drain loop
DEFAULT_ALPHA = 0.42  # fraction of remaining distance per tick (higher = snappier)


def _prepare_windows() -> None:
    if sys.platform != "win32":
        return
    try:
        # Per-monitor DPI awareness so cursor maths uses physical pixels on scaled displays.
        if not ctypes.windll.user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4)):
            ctypes.windll.user32.SetProcessDPIAware()
    except (AttributeError, OSError):
        pass
    try:
        ctypes.windll.winmm.timeBeginPeriod(1)  # 1 ms timer so 5 ms sleeps are accurate
    except (AttributeError, OSError):
        pass


class SystemMouse:
    """The real cursor, via pynput."""

    def __init__(self):
        _prepare_windows()
        from pynput.mouse import Button, Controller  # imported lazily so tests run headless
        self._mouse = Controller()
        self._left = Button.left

    def move(self, dx: int, dy: int) -> None:
        self._mouse.move(dx, dy)

    def press(self) -> None:
        self._mouse.press(self._left)

    def release(self) -> None:
        self._mouse.release(self._left)

    def center(self) -> None:
        self._mouse.position = _primary_screen_center()


class LoggingMouse:
    """--dry-run: logs what would happen instead of touching the real cursor."""

    def __init__(self):
        self._moved = 0

    def move(self, dx: int, dy: int) -> None:
        self._moved += abs(dx) + abs(dy)
        if self._moved >= 500:
            log.info("dry-run: cursor moved ~500 px")
            self._moved = 0

    def press(self) -> None:
        log.info("dry-run: button down")

    def release(self) -> None:
        log.info("dry-run: button up")

    def center(self) -> None:
        log.info("dry-run: center")


def _primary_screen_center() -> tuple[int, int]:
    if sys.platform == "win32":
        w = ctypes.windll.user32.GetSystemMetrics(0)
        h = ctypes.windll.user32.GetSystemMetrics(1)
        return w // 2, h // 2
    try:
        import tkinter
        root = tkinter.Tk()
        root.withdraw()
        w, h = root.winfo_screenwidth(), root.winfo_screenheight()
        root.destroy()
        return w // 2, h // 2
    except Exception:
        return 960, 540


class PointerDriver:
    """Queues motion from any number of sessions and drains it smoothly into a mouse."""

    def __init__(self, mouse):
        self.mouse = mouse
        self.alpha = DEFAULT_ALPHA
        self._pending_x = self._pending_y = 0.0
        self._carry_x = self._carry_y = 0.0
        self._pressed = 0  # sessions currently holding the button
        self._wake: asyncio.Event | None = None

    def set_alpha(self, alpha: float) -> None:
        self.alpha = min(1.0, max(0.05, alpha))
        log.info("Smoothing alpha set to %.2f", self.alpha)

    def add_motion(self, dx: float, dy: float) -> None:
        self._pending_x += dx
        self._pending_y += dy
        if self._wake:
            self._wake.set()

    def press(self) -> None:
        if self._pressed == 0:
            self.mouse.press()
        self._pressed += 1

    def release(self) -> None:
        if self._pressed == 0:
            return
        self._pressed -= 1
        if self._pressed == 0:
            self.mouse.release()

    def center(self) -> None:
        self._pending_x = self._pending_y = 0.0
        self._carry_x = self._carry_y = 0.0
        self.mouse.center()

    def step(self) -> None:
        """One drain tick (public for tests)."""
        a = self.alpha
        sx = self._pending_x if abs(self._pending_x) < 0.5 else self._pending_x * a
        sy = self._pending_y if abs(self._pending_y) < 0.5 else self._pending_y * a
        self._pending_x -= sx
        self._pending_y -= sy
        self._carry_x += sx
        self._carry_y += sy
        # The epsilon stops float error (2.9999999999999996) from swallowing a whole pixel.
        mx = int(self._carry_x + math.copysign(1e-9, self._carry_x))
        my = int(self._carry_y + math.copysign(1e-9, self._carry_y))
        self._carry_x -= mx
        self._carry_y -= my
        if mx or my:
            self.mouse.move(mx, my)

    @property
    def idle(self) -> bool:
        return self._pending_x == 0.0 and self._pending_y == 0.0

    async def run(self) -> None:
        self._wake = asyncio.Event()
        try:
            while True:
                if self.idle:
                    # Sleep until motion arrives instead of spinning at 200 Hz.
                    self._wake.clear()
                    await self._wake.wait()
                await asyncio.sleep(TICK)
                self.step()
        finally:
            self._wake = None
