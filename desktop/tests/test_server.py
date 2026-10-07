"""End-to-end: a real server on localhost, driven by a WebSocket client acting as the phone."""

import asyncio
import json
import socket

import pytest
from websockets.asyncio.client import connect
from websockets.exceptions import ConnectionClosed, InvalidStatus

from airpoint import protocol
from airpoint.config import ConfigStore
from airpoint.pairing import Pairing
from airpoint.pointer import PointerDriver
from airpoint.server import AirpointServer

from test_pointer import FakeMouse


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture
def env(tmp_path):
    store = ConfigStore(tmp_path / "config.json")
    store.settings.port = free_port()
    pairing = Pairing(store)
    mouse = FakeMouse()
    events = []
    server = AirpointServer(store, pairing, PointerDriver(mouse),
                            on_event=lambda kind, **info: events.append(kind))
    return store, pairing, mouse, server, events


async def running(server):
    stop, ready = asyncio.Event(), asyncio.Event()
    task = asyncio.create_task(server.serve(stop, ready))
    await asyncio.wait_for(ready.wait(), 5)
    return stop, task


def url(store):
    return f"ws://127.0.0.1:{store.settings.port}"


def hello(**extra):
    return json.dumps({"type": "hello", "v": protocol.VERSION, "device": "Test Phone",
                       "device_id": "phone-1", **extra})


async def until(predicate, timeout=2.0):
    loop = asyncio.get_running_loop()
    end = loop.time() + timeout
    while not predicate():
        if loop.time() > end:
            raise AssertionError("condition not reached")
        await asyncio.sleep(0.01)


def test_full_pairing_flow(env):
    store, pairing, mouse, server, events = env

    async def scenario():
        stop, task = await running(server)
        try:
            # 1. No credentials: told to pair, connection closed with 4401.
            async with connect(url(store)) as ws:
                await ws.send(hello())
                assert json.loads(await ws.recv())["code"] == "pairing_required"
                with pytest.raises(ConnectionClosed) as exc:
                    await ws.recv()
                assert exc.value.rcvd.code == protocol.CLOSE_PAIRING_REQUIRED

            # 2. Wrong PIN.
            async with connect(url(store)) as ws:
                await ws.send(hello(pin="999999" if pairing.pin != "999999" else "000000"))
                assert json.loads(await ws.recv())["code"] == "bad_pin"

            # 3. Right PIN: welcome with a token, then events drive the mouse.
            async with connect(url(store)) as ws:
                await ws.send(hello(pin=pairing.pin))
                welcome = json.loads(await ws.recv())
                assert welcome["type"] == "welcome" and welcome["pc_id"] == store.settings.pc_id
                token = welcome["token"]
                await until(lambda: server.status.connected)
                await ws.send(json.dumps({"type": "motion", "dx": 40, "dy": -12}))
                await ws.send(json.dumps({"type": "button", "action": "down"}))
                await until(lambda: mouse.x == 40 and mouse.y == -12 and mouse.pressed)
            # Disconnecting mid-press must release the button.
            await until(lambda: not mouse.pressed and not server.status.connected)
            assert "paired" in events

            # 4. Token reconnect, no new token issued.
            async with connect(url(store)) as ws:
                await ws.send(hello(token=token))
                welcome = json.loads(await ws.recv())
                assert welcome["type"] == "welcome" and "token" not in welcome
        finally:
            stop.set()
            await task

    asyncio.run(scenario())


def test_events_before_hello_are_rejected(env):
    store, pairing, mouse, server, _ = env

    async def scenario():
        stop, task = await running(server)
        try:
            async with connect(url(store)) as ws:
                await ws.send(json.dumps({"type": "motion", "dx": 999, "dy": 999}))
                assert json.loads(await ws.recv())["code"] == "bad_request"
            assert (mouse.x, mouse.y) == (0, 0)
        finally:
            stop.set()
            await task

    asyncio.run(scenario())


def test_browsers_are_refused(env):
    store, _, _, server, _ = env

    async def scenario():
        stop, task = await running(server)
        try:
            with pytest.raises(InvalidStatus) as exc:
                async with connect(url(store), origin="https://evil.example"):
                    pass
            assert exc.value.response.status_code == 403
        finally:
            stop.set()
            await task

    asyncio.run(scenario())


def test_garbage_motion_is_sanitised(env):
    store, pairing, mouse, server, _ = env

    async def scenario():
        stop, task = await running(server)
        try:
            async with connect(url(store)) as ws:
                await ws.send(hello(pin=pairing.pin))
                await ws.recv()
                await ws.send(json.dumps({"type": "motion", "dx": "NaN", "dy": None}))
                await ws.send("not json")
                await ws.send(json.dumps({"type": "motion", "dx": 5, "dy": 5}))
                await until(lambda: (mouse.x, mouse.y) == (5, 5))
        finally:
            stop.set()
            await task

    asyncio.run(scenario())


def test_udp_discovery_announces_this_pc(env):
    store, _, _, server, _ = env

    async def scenario():
        stop, task = await running(server)
        try:
            loop = asyncio.get_running_loop()
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setblocking(False)
            sock.sendto(json.dumps({"type": protocol.DISCOVER, "v": 1}).encode(),
                        ("127.0.0.1", store.settings.port))
            data = await asyncio.wait_for(loop.sock_recv(sock, 1024), 2)
            sock.close()
            reply = json.loads(data)
            assert reply["type"] == protocol.ANNOUNCE
            assert reply["id"] == store.settings.pc_id and reply["port"] == store.settings.port
        finally:
            stop.set()
            await task

    asyncio.run(scenario())
