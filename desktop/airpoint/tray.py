"""System-tray app.

The tray icon is the Loop mark. Its point shows the state:
    grey mark            paused / error
    accent point         listening for a phone
    green point          a phone is connected
Left-click shows the pairing PIN.
"""

import functools
import logging
import webbrowser

import pystray

from . import REPO_URL, __version__, brand, system
from .config import data_dir
from .pairing import format_pin
from .raster import render

log = logging.getLogger("airpoint")

ICON_PX = 64


def tray_image(state: str, light_taskbar: bool):
    base = brand.INK if light_taskbar else brand.PAPER
    if state in ("paused", "error"):
        stroke = dot = brand.MUTED
    else:
        stroke = base
        if state == "connected":
            dot = brand.LIVE
        else:
            dot = brand.ACCENT if light_taskbar else brand.ACCENT_ON_DARK
    return render([(brand.SMALL_STROKE, stroke, None), (brand.SMALL_DOT, dot, None)], ICON_PX)


class TrayApp:
    def __init__(self, controller, pairing):
        self.controller = controller
        self.pairing = pairing
        self.paused = False
        self._last_state = None
        self.icon = pystray.Icon("airpoint", tray_image("paused", system.taskbar_is_light()),
                                 "Airpoint", menu=self._build_menu())

    # ---- state ----------------------------------------------------------------------

    def state(self) -> str:
        status = self.controller.status
        if self.paused:
            return "paused"
        if status.error:
            return "error"
        if status.connected:
            return "connected"
        return "listening" if status.running else "paused"

    def headline(self) -> str:
        status = self.controller.status
        state = self.state()
        if state == "paused":
            return "Paused"
        if state == "error":
            return status.error or "Stopped (see log)"
        if state == "connected":
            names = ", ".join(sorted({s.name for s in status.sessions}))
            return f"Connected · {names}"
        return f"Waiting for a phone · {system.primary_ip()}"

    def refresh(self) -> None:
        """Called from any thread when server state changes."""
        state = self.state()
        try:
            if state != self._last_state:
                self.icon.icon = tray_image(state, system.taskbar_is_light())
                self._last_state = state
            self.icon.title = f"Airpoint · {self.headline()}"[:127]
            self.icon.update_menu()
        except Exception:
            log.debug("Tray refresh failed", exc_info=True)

    def on_event(self, kind: str, **info) -> None:
        if kind == "paired":
            self.notify(f"Paired with {info.get('name', 'your phone')}. It will reconnect automatically from now on.")
        elif kind == "locked_out":
            self.notify(f"Too many wrong PINs. Pairing is paused for {round(info.get('seconds', 60))} s, "
                        f"then use the new PIN {format_pin(self.pairing.pin)}.")

    def notify(self, message: str, title: str = "Airpoint") -> None:
        try:
            self.icon.notify(message, title)
        except Exception:
            log.debug("Notification failed", exc_info=True)

    # ---- menu -----------------------------------------------------------------------

    def _build_menu(self):
        item = pystray.MenuItem
        return pystray.Menu(
            item(lambda _: self.headline(), None, enabled=False),
            item(lambda _: f"Pairing PIN   {format_pin(self.pairing.pin)}", self._show_pin, default=True),
            item("New PIN", self._new_pin),
            pystray.Menu.SEPARATOR,
            item("Paired phones", pystray.Menu(self._device_items)),
            item(lambda _: "Resume" if self.paused else "Pause", self._toggle_pause),
            item("Start with Windows", self._toggle_autostart,
                 checked=lambda _: system.autostart_enabled(), visible=system.autostart_supported()),
            pystray.Menu.SEPARATOR,
            item("Open log folder", lambda: system.open_folder(data_dir())),
            item(f"About Airpoint {__version__}", lambda: webbrowser.open(REPO_URL)),
            item("Quit", self._quit),
        )

    def _device_items(self):
        devices = sorted(self.pairing.devices, key=lambda d: -d.last_seen)
        if not devices:
            yield pystray.MenuItem("No phones paired yet", None, enabled=False)
            return
        for d in devices:
            # partial has no __code__, so pystray passes (icon, item) straight through
            yield pystray.MenuItem(f"Forget {d.name}", functools.partial(self._forget, d.device_id))
        yield pystray.Menu.SEPARATOR
        yield pystray.MenuItem("Forget all", functools.partial(self._forget, None))

    def _show_pin(self):
        self.notify(f"PIN {format_pin(self.pairing.pin)}  ·  this PC is {system.primary_ip()}",
                    "Pair a phone with Airpoint")

    def _new_pin(self):
        self.pairing.rotate_pin()
        self._show_pin()
        self.refresh()

    def _forget(self, device_id, *_):
        if device_id is None:
            self.pairing.forget_all()
        else:
            self.pairing.forget(device_id)
        self.refresh()

    def _toggle_pause(self):
        if self.paused:
            self.paused = False
            self.controller.start()
        else:
            self.paused = True
            self.controller.stop()
        self.refresh()

    def _toggle_autostart(self):
        try:
            system.set_autostart(not system.autostart_enabled())
        except OSError as e:
            log.error("Could not change start-at-login: %s", e)
        self.refresh()

    def _quit(self):
        self.controller.stop()
        self.icon.stop()

    # ---- run ------------------------------------------------------------------------

    def _setup(self, icon):
        icon.visible = True
        self.controller.start()
        if not self.pairing.devices:
            self.notify(f"Airpoint is running in the tray. Pair your phone with PIN {format_pin(self.pairing.pin)}.")

    def run(self):
        self.icon.run(setup=self._setup)
