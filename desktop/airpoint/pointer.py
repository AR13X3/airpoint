"""Mouse output with eased motion.

Phones send motion in bursts (one coalesced frame every ~12 ms over Wi-Fi, with jitter).
Applying each burst directly makes the cursor stutter, so deltas are queued and drained
at 200 Hz: every tick moves a fixed fraction (alpha) of the remaining distance, then carries
sub-pixel remainders so slow movements aren't lost to rounding.

Edge scrolling: each tick, the cursor's position plus the requested move is checked against
the monitors. A target that lies on no monitor is a push past the desktop's edge, and the
overshoot drives scrolling. (Comparing requested and actual movement is not reliable:
Windows drops about 1% of cursor moves, which would read as phantom edge hits.) Moving onto
another monitor is not a push, so multi-monitor layouts behave. Three details make it feel
right:

- The edge absorbs a flick: when the cursor first hits an edge, the rest of that flick's
  queued motion is dropped, so reaching for a tab doesn't lurch the page. Only continued
  pushing scrolls, in proportion to how hard the pen pushes.
- The wheel is aimed at the content. Windows sends wheel input to the window under the
  cursor, which at an edge is the taskbar or a title bar, where scrolling does nothing. So
  each wheel slice is injected at an anchor point inside the screen, in one atomic input
  batch (move there, wheel, move back), and the cursor stays at the edge.
- Not while dragging: apps already auto-scroll a drag that reaches an edge.
"""

import asyncio
import ctypes
import ctypes.wintypes
import logging
import math
import sys

log = logging.getLogger("airpoint")

TICK = 0.005          # 200 Hz drain loop
DEFAULT_ALPHA = 0.42  # fraction of remaining distance per tick (higher = snappier)

EDGE_PX_PER_NOTCH = 40.0   # pushing this far past an edge scrolls one wheel notch
EDGE_ANCHOR_INSET = 200    # logical px inside the edge where the wheel is aimed: clear of
                           # taskbars, browser tab strips and Office ribbons
WHEEL_DELTA = 120          # Windows wheel units per notch
MIN_WHEEL_UNITS = 120      # whole notches: some apps ignore partial wheel deltas
MAX_WHEEL_BACKLOG = 2.0    # notches; a hard shove can't queue up a runaway scroll


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
    """The real cursor, via pynput (plus raw SendInput for aimed wheel input on Windows)."""

    def __init__(self):
        _prepare_windows()
        from pynput.mouse import Button, Controller  # imported lazily so tests run headless
        self._mouse = Controller()
        self._left = Button.left

    def position(self) -> tuple[int, int] | None:
        return self._mouse.position

    def move(self, dx: int, dy: int) -> None:
        self._mouse.move(dx, dy)

    def edge_overflow(self, x: int, y: int) -> tuple[int, int]:
        """How far (x, y) lies past the desktop's edge on each axis; (0, 0) if on a monitor."""
        if sys.platform != "win32":
            return 0, 0
        return _overflow(x, y)

    def scale_at(self, x: int, y: int) -> float:
        """Display scaling of the monitor at (x, y), e.g. 2.0 at 200%."""
        if sys.platform != "win32":
            return 1.0
        return _scale_at(x, y)

    def scroll(self, dx: float, dy: float, at: tuple[int, int] | None = None) -> None:
        """Scroll by (possibly fractional) notches; +dy is up, +dx is right.

        With [at], the wheel goes to the window at that point while the cursor stays put.
        """
        if at is not None and sys.platform == "win32":
            _wheel_at(at, dx, dy)
        else:
            self._mouse.scroll(dx, dy)

    def press(self) -> None:
        self._mouse.press(self._left)

    def release(self) -> None:
        self._mouse.release(self._left)

    def center(self) -> None:
        self._mouse.position = _primary_screen_center()


# ---- Windows: wheel input aimed at a point ---------------------------------------------

class _MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", ctypes.c_long), ("dy", ctypes.c_long), ("mouseData", ctypes.c_ulong),
                ("dwFlags", ctypes.c_ulong), ("time", ctypes.c_ulong), ("dwExtraInfo", ctypes.c_size_t)]


class _INPUT(ctypes.Structure):
    class _U(ctypes.Union):
        # MOUSEINPUT is the largest member of the Win32 union, so it alone sets the size.
        _fields_ = [("mi", _MOUSEINPUT)]
    _anonymous_ = ("u",)
    _fields_ = [("type", ctypes.c_ulong), ("u", _U)]


_MOVE, _WHEEL, _HWHEEL, _VIRTUALDESK, _ABSOLUTE = 0x0001, 0x0800, 0x1000, 0x4000, 0x8000


class _MONITORINFO(ctypes.Structure):
    _fields_ = [("cbSize", ctypes.c_ulong), ("rcMonitor", ctypes.wintypes.RECT),
                ("rcWork", ctypes.wintypes.RECT), ("dwFlags", ctypes.c_ulong)]


def _scale_at(x: int, y: int) -> float:
    try:
        user32, shcore = ctypes.windll.user32, ctypes.windll.shcore
        user32.MonitorFromPoint.restype = ctypes.c_void_p
        user32.MonitorFromPoint.argtypes = [ctypes.wintypes.POINT, ctypes.c_ulong]
        hmon = user32.MonitorFromPoint(ctypes.wintypes.POINT(x, y), 2)
        dx, dy = ctypes.c_uint(), ctypes.c_uint()
        if shcore.GetDpiForMonitor(ctypes.c_void_p(hmon), 0, ctypes.byref(dx), ctypes.byref(dy)) == 0:
            return max(1.0, dy.value / 96)
    except (AttributeError, OSError):
        pass
    return 1.0


def _overflow(x: int, y: int) -> tuple[int, int]:
    user32 = ctypes.windll.user32
    user32.MonitorFromPoint.restype = ctypes.c_void_p
    user32.MonitorFromPoint.argtypes = [ctypes.wintypes.POINT, ctypes.c_ulong]
    user32.GetMonitorInfoW.argtypes = [ctypes.c_void_p, ctypes.POINTER(_MONITORINFO)]
    pt = ctypes.wintypes.POINT(x, y)
    if user32.MonitorFromPoint(pt, 0):  # MONITOR_DEFAULTTONULL: on some monitor
        return 0, 0
    info = _MONITORINFO(cbSize=ctypes.sizeof(_MONITORINFO))
    if not user32.GetMonitorInfoW(user32.MonitorFromPoint(pt, 2), ctypes.byref(info)):  # nearest
        return 0, 0
    r = info.rcMonitor
    return x - min(max(x, r.left), r.right - 1), y - min(max(y, r.top), r.bottom - 1)


def _wheel_at(at: tuple[int, int], dx: float, dy: float) -> None:
    user32 = ctypes.windll.user32
    vx, vy = user32.GetSystemMetrics(76), user32.GetSystemMetrics(77)    # virtual desktop origin
    vw, vh = user32.GetSystemMetrics(78), user32.GetSystemMetrics(79)    # and size

    def absolute(x: int, y: int) -> _INPUT:
        nx = round((x - vx) * 65535 / max(1, vw - 1))
        ny = round((y - vy) * 65535 / max(1, vh - 1))
        return _INPUT(type=0, mi=_MOUSEINPUT(nx, ny, 0, _MOVE | _ABSOLUTE | _VIRTUALDESK, 0, 0))

    def wheel(units: int, flag: int) -> _INPUT:
        return _INPUT(type=0, mi=_MOUSEINPUT(0, 0, units & 0xFFFFFFFF, flag, 0, 0))

    pt = ctypes.wintypes.POINT()
    user32.GetCursorPos(ctypes.byref(pt))
    batch = [absolute(*at)]
    if dy:
        batch.append(wheel(int(dy * WHEEL_DELTA), _WHEEL))
    if dx:
        batch.append(wheel(int(dx * WHEEL_DELTA), _HWHEEL))
    batch.append(absolute(pt.x, pt.y))
    # One batch, so nothing can interleave: the wheel lands on the window at `at`.
    arr = (_INPUT * len(batch))(*batch)
    user32.SendInput(len(batch), arr, ctypes.sizeof(_INPUT))
    user32.SetCursorPos(pt.x, pt.y)  # undo any rounding in the normalised coordinates



class LoggingMouse:
    """--dry-run: logs what would happen instead of touching the real cursor."""

    def __init__(self, width: int = 1920, height: int = 1080):
        self._w, self._h = width, height
        self._x, self._y = width // 2, height // 2
        self._moved = 0
        self._scrolled = 0.0

    def position(self) -> tuple[int, int]:
        return self._x, self._y

    def move(self, dx: int, dy: int) -> None:
        # Simulate a single screen so edge scrolling can be exercised too.
        nx = min(self._w - 1, max(0, self._x + dx))
        ny = min(self._h - 1, max(0, self._y + dy))
        self._moved += abs(nx - self._x) + abs(ny - self._y)
        self._x, self._y = nx, ny
        if self._moved >= 500:
            log.info("dry-run: cursor moved ~500 px, now at (%d, %d)", self._x, self._y)
            self._moved = 0

    def edge_overflow(self, x: int, y: int) -> tuple[int, int]:
        return x - min(max(x, 0), self._w - 1), y - min(max(y, 0), self._h - 1)

    def scale_at(self, x: int, y: int) -> float:
        return 1.0

    def scroll(self, dx: float, dy: float, at: tuple[int, int] | None = None) -> None:
        self._scrolled += abs(dx) + abs(dy)
        if self._scrolled >= 3:
            log.info("dry-run: scrolled 3 notches at %s (last %+.2f, %+.2f)", at, dx, dy)
            self._scrolled = 0.0

    def press(self) -> None:
        log.info("dry-run: button down")

    def release(self) -> None:
        log.info("dry-run: button up")

    def center(self) -> None:
        self._x, self._y = self._w // 2, self._h // 2
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
        self.edge_scroll = True
        self._wheel_x = self._wheel_y = 0.0
        self._pinned_x = self._pinned_y = 0  # -1/+1 while held against an edge on that axis
        self._pending_x = self._pending_y = 0.0
        self._carry_x = self._carry_y = 0.0
        self._pressed = 0  # sessions currently holding the button
        self._wake: asyncio.Event | None = None

    def set_alpha(self, alpha: float) -> None:
        self.alpha = min(1.0, max(0.05, alpha))
        log.info("Smoothing alpha set to %.2f", self.alpha)

    def set_edge_scroll(self, enabled: bool) -> None:
        self.edge_scroll = bool(enabled)
        self._wheel_x = self._wheel_y = 0.0
        log.info("Edge scrolling %s", "on" if self.edge_scroll else "off")

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
        self._wheel_x = self._wheel_y = 0.0
        self._pinned_x = self._pinned_y = 0
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
        if not (mx or my):
            return
        pos = self.mouse.position()
        self.mouse.move(mx, my)
        if pos is None:
            return
        over_x, over_y = self.mouse.edge_overflow(pos[0] + mx, pos[1] + my)
        push_x = self._edge_axis(mx, over_x, "x")
        push_y = self._edge_axis(my, over_y, "y")
        if self.edge_scroll and self._pressed == 0 and (push_x or push_y):
            self._edge_scroll(push_x, push_y)

    def _edge_axis(self, moved: int, over: int, axis: str) -> int:
        """Motion pushed past an edge on one axis this tick (0 if none)."""
        lost = max(-abs(moved), min(abs(moved), over))  # never more than this tick's move
        direction = (1 if moved > 0 else -1) if lost and (lost > 0) == (moved > 0) else 0
        was = getattr(self, f"_pinned_{axis}")
        setattr(self, f"_pinned_{axis}", direction if direction else (was if not moved else 0))
        if not direction:
            return 0
        if was != direction:
            # Just hit the edge: it absorbs the rest of this flick.
            setattr(self, f"_pending_{axis}", 0.0)
            setattr(self, f"_carry_{axis}", 0.0)
            return 0
        return lost

    def _edge_scroll(self, push_x: int, push_y: int) -> None:
        cap = MAX_WHEEL_BACKLOG
        self._wheel_x = max(-cap, min(cap, self._wheel_x + push_x / EDGE_PX_PER_NOTCH))
        self._wheel_y = max(-cap, min(cap, self._wheel_y - push_y / EDGE_PX_PER_NOTCH))  # down scrolls down
        ux = int(self._wheel_x * WHEEL_DELTA)
        uy = int(self._wheel_y * WHEEL_DELTA)
        ux = ux if abs(ux) >= MIN_WHEEL_UNITS else 0
        uy = uy if abs(uy) >= MIN_WHEEL_UNITS else 0
        if not (ux or uy):
            return
        pos = self.mouse.position()
        at = None
        if pos is not None:
            x, y = pos
            inset = round(EDGE_ANCHOR_INSET * self.mouse.scale_at(x, y))
            if push_x:
                x -= inset if push_x > 0 else -inset
            if push_y:
                y -= inset if push_y > 0 else -inset
            at = (x, y)
        self.mouse.scroll(ux / WHEEL_DELTA, uy / WHEEL_DELTA, at=at)
        self._wheel_x -= ux / WHEEL_DELTA
        self._wheel_y -= uy / WHEEL_DELTA

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
