"""Answers UDP discovery broadcasts so phones can find this PC without typing an IP."""

import asyncio
import logging

from . import protocol

log = logging.getLogger("airpoint")


class DiscoveryResponder(asyncio.DatagramProtocol):
    def __init__(self, announce):
        self._announce = announce  # () -> dict
        self.transport = None

    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data, addr):
        if len(data) > 512:
            return
        msg = protocol.loads(data)
        if not msg or msg.get("type") != protocol.DISCOVER:
            return
        try:
            self.transport.sendto(protocol.dumps(self._announce()).encode("utf-8"), addr)
        except OSError as e:
            log.debug("Discovery reply to %s failed: %s", addr, e)


async def start_discovery(port: int, announce):
    loop = asyncio.get_running_loop()
    transport, _ = await loop.create_datagram_endpoint(
        lambda: DiscoveryResponder(announce),
        local_addr=("0.0.0.0", port),
        allow_broadcast=True,
    )
    return transport
