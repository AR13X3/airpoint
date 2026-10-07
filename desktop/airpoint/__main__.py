"""Entry point.

    python -m airpoint              tray app (use pythonw to hide the console)
    python -m airpoint --headless   console mode, prints the pairing PIN
"""

import argparse
import logging
import os
import sys
import time
from logging.handlers import RotatingFileHandler

from . import __version__
from .config import ConfigStore, data_dir
from .pairing import Pairing, format_pin
from .pointer import LoggingMouse, PointerDriver, SystemMouse
from .server import AirpointServer, ServerController
from .system import primary_ip


def _setup_logging(console: bool) -> None:
    handlers = [RotatingFileHandler(data_dir() / "airpoint.log", maxBytes=1_000_000, backupCount=2,
                                    encoding="utf-8")]
    if console:
        handlers.append(logging.StreamHandler())
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s", handlers=handlers)


def main(argv=None) -> int:
    # A windowed (no-console) build has no stdout/stderr; give prints somewhere harmless.
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w")

    parser = argparse.ArgumentParser(prog="airpoint", description="Airpoint desktop receiver")
    parser.add_argument("--headless", action="store_true", help="run without a tray icon")
    parser.add_argument("--port", type=int, help="WebSocket/discovery port (default 8765)")
    parser.add_argument("--forget-all", action="store_true", help="unpair every phone and exit")
    parser.add_argument("--dry-run", action="store_true", help="log pointer events instead of moving the mouse")
    parser.add_argument("--version", action="version", version=f"Airpoint {__version__}")
    args = parser.parse_args(argv)

    _setup_logging(console=args.headless)
    store = ConfigStore()
    if args.port:
        store.update(lambda s: setattr(s, "port", args.port))
    pairing = Pairing(store)
    if args.forget_all:
        pairing.forget_all()
        print("All phones forgotten.")
        return 0

    driver = PointerDriver(LoggingMouse() if args.dry_run else SystemMouse())

    if args.headless:
        def on_event(kind, **info):
            if kind == "paired":
                print(f"Paired with {info.get('name')}. Next PIN: {format_pin(pairing.pin)}")
            elif kind == "locked_out":
                print(f"Too many wrong PINs; locked for {round(info['seconds'])} s. New PIN: {format_pin(pairing.pin)}")
            elif kind in ("connected", "disconnected"):
                print(f"{info.get('name')} {kind}")

        controller = ServerController(AirpointServer(store, pairing, driver, on_event=on_event))
        controller.start()
        print(f"Airpoint {__version__} - {store.settings.name} - {primary_ip()}:{store.settings.port}")
        print(f"Pairing PIN: {format_pin(pairing.pin)}   (Ctrl+C to quit)")
        try:
            while controller.active:
                time.sleep(0.5)
        except KeyboardInterrupt:
            pass
        controller.stop()
        if controller.status.error:
            print(controller.status.error, file=sys.stderr)
            return 1
        return 0

    from .tray import TrayApp

    server = AirpointServer(store, pairing, driver)
    controller = ServerController(server)
    tray = TrayApp(controller, pairing)
    server._on_change = tray.refresh
    server._on_event = tray.on_event
    tray.run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
