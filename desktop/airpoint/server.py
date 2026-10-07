"""WebSocket server: authenticates phones, then turns their events into pointer actions.

`ServerController` runs everything on a background asyncio loop so the tray (or the
headless CLI) can start and stop it from the main thread.
"""

import asyncio
import logging
import threading
from dataclasses import dataclass, field
from http import HTTPStatus

from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed

from . import __version__, protocol
from .config import ConfigStore
from .discovery import start_discovery
from .pairing import Pairing
from .pointer import PointerDriver

log = logging.getLogger("airpoint")

HOST = "0.0.0.0"


@dataclass
class Session:
    device_id: str
    name: str
    address: str


@dataclass
class Status:
    running: bool = False
    error: str | None = None
    sessions: list = field(default_factory=list)  # list[Session]

    @property
    def connected(self) -> bool:
        return bool(self.sessions)


class AirpointServer:
    def __init__(self, store: ConfigStore, pairing: Pairing, driver: PointerDriver,
                 on_change=None, on_event=None):
        self.store = store
        self.pairing = pairing
        self.driver = driver
        self.status = Status()
        self._on_change = on_change or (lambda: None)
        self._on_event = on_event or (lambda kind, **info: None)

    def _changed(self):
        try:
            self._on_change()
        except Exception:
            log.exception("on_change handler failed")

    def _event(self, kind, **info):
        try:
            self._on_event(kind, **info)
        except Exception:
            log.exception("on_event handler failed")

    def announce(self) -> dict:
        s = self.store.settings
        return {"type": protocol.ANNOUNCE, "v": protocol.VERSION, "id": s.pc_id, "name": s.name,
                "port": s.port, "version": __version__}

    @staticmethod
    def _reject_browsers(connection, request):
        # Browsers always send Origin; the Android app never does. Refusing these blocks
        # web pages from scripting the local server (cross-site WebSocket hijacking).
        if request.headers.get("Origin") is not None:
            return connection.respond(HTTPStatus.FORBIDDEN, "Browsers are not allowed\n")
        return None

    async def serve(self, stop: asyncio.Event, ready: asyncio.Event | None = None) -> None:
        port = self.store.settings.port
        drain = asyncio.create_task(self.driver.run())
        discovery = None
        try:
            async with serve(self._handle, HOST, port, process_request=self._reject_browsers,
                             max_size=protocol.MAX_MESSAGE_BYTES, ping_interval=20, ping_timeout=20,
                             compression=None):
                try:
                    discovery = await start_discovery(port, self.announce)
                except OSError as e:
                    log.warning("Discovery unavailable on UDP %d: %s", port, e)
                self.status.running = True
                self.status.error = None
                self._changed()
                log.info("Listening on ws://%s:%d", HOST, port)
                if ready:
                    ready.set()
                await stop.wait()
        finally:
            if discovery:
                discovery.close()
            drain.cancel()
            self.status.running = False
            self.status.sessions = []
            self._changed()

    async def _handle(self, ws) -> None:
        address = "%s:%s" % ws.remote_address[:2] if ws.remote_address else "?"
        try:
            raw = await asyncio.wait_for(ws.recv(), protocol.HELLO_TIMEOUT)
        except (asyncio.TimeoutError, ConnectionClosed):
            await ws.close(protocol.CLOSE_BAD_REQUEST, "hello expected")
            return

        hello = protocol.loads(raw)
        if not hello or hello.get("type") != "hello":
            await self._fail(ws, "bad_request")
            return
        if hello.get("v") != protocol.VERSION:
            await self._fail(ws, "unsupported_version")
            return

        name = str(hello.get("device") or "Phone")[:64]
        result = self.pairing.authenticate(
            device_id=str(hello.get("device_id") or ""),
            device_name=name,
            token=hello.get("token") if isinstance(hello.get("token"), str) else None,
            pin=str(hello["pin"]) if hello.get("pin") is not None else None,
        )
        if not result.ok:
            log.info("Rejected %s (%s): %s", name, address, result.error)
            if result.error == "locked_out":
                self._event("locked_out", seconds=result.retry_after)
            await self._fail(ws, result.error, retry_after=result.retry_after)
            if result.error in ("bad_pin", "locked_out"):
                self._changed()  # the PIN may have rotated
            return

        welcome = {"type": "welcome", "v": protocol.VERSION, "pc_id": self.store.settings.pc_id,
                   "name": self.store.settings.name, "version": __version__}
        if result.token:
            welcome["token"] = result.token
        await ws.send(protocol.dumps(welcome))

        session = Session(result.device.device_id, result.device.name, address)
        self.status.sessions.append(session)
        if result.token:
            log.info("Paired %s (%s)", session.name, address)
            self._event("paired", name=session.name)
        log.info("%s connected from %s", session.name, address)
        self._event("connected", name=session.name)
        self._changed()

        holding = False
        try:
            async for raw in ws:
                msg = protocol.loads(raw)
                if not msg:
                    continue
                kind = msg.get("type")
                if kind == "motion":
                    self.driver.add_motion(protocol.finite(msg.get("dx")), protocol.finite(msg.get("dy")))
                elif kind == "button":
                    down = msg.get("action") == "down"
                    if down and not holding:
                        self.driver.press()
                        holding = True
                    elif not down and holding:
                        self.driver.release()
                        holding = False
                elif kind == "center":
                    self.driver.center()
                elif kind == "config":
                    if "smooth_alpha" in msg:
                        self.driver.set_alpha(protocol.finite(msg.get("smooth_alpha"), 0.42))
                    if isinstance(msg.get("edge_scroll"), bool):
                        self.driver.set_edge_scroll(msg["edge_scroll"])
        except ConnectionClosed:
            pass
        finally:
            if holding:
                self.driver.release()  # never leave the mouse button stuck down
            if session in self.status.sessions:
                self.status.sessions.remove(session)
            log.info("%s disconnected", session.name)
            self._event("disconnected", name=session.name)
            self._changed()

    async def _fail(self, ws, error: str, retry_after: float = 0.0) -> None:
        msg = {"type": "error", "code": error}
        if retry_after:
            msg["retry_after"] = round(retry_after)
        try:
            await ws.send(protocol.dumps(msg))
        except ConnectionClosed:
            return
        await ws.close(protocol.ERROR_CLOSE_CODES.get(error, protocol.CLOSE_BAD_REQUEST), error)


class ServerController:
    """Runs an AirpointServer on a background thread. start()/stop() are thread-safe."""

    def __init__(self, server: AirpointServer):
        self.server = server
        self._thread: threading.Thread | None = None
        self._loop: asyncio.AbstractEventLoop | None = None
        self._stop: asyncio.Event | None = None

    @property
    def status(self) -> Status:
        return self.server.status

    @property
    def active(self) -> bool:
        return self._thread is not None and self._thread.is_alive()

    def start(self) -> None:
        if self.active:
            return
        self._thread = threading.Thread(target=self._run, name="airpoint-server", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        if self._loop and self._stop:
            self._loop.call_soon_threadsafe(self._stop.set)
        if self._thread:
            self._thread.join(timeout=3)
        self._thread = None

    def _run(self) -> None:
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._stop = asyncio.Event()
        try:
            self._loop.run_until_complete(self.server.serve(self._stop))
        except OSError as e:
            port = self.server.store.settings.port
            self.server.status.error = (f"Port {port} is in use. Is Airpoint already running?"
                                        if getattr(e, "errno", None) in (98, 10048) else str(e))
            log.error("Server failed: %s", e)
            self.server._changed()
        finally:
            self._loop.close()
            self._loop = None
