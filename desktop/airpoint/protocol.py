"""Wire protocol constants. See docs/PROTOCOL.md for the full description."""

import json

VERSION = 1

# UDP discovery (same port number as the WebSocket server; UDP and TCP don't collide)
DISCOVER = "airpoint.discover"
ANNOUNCE = "airpoint.announce"

# WebSocket close codes (4000-4999 are reserved for applications)
CLOSE_BAD_REQUEST = 4400
CLOSE_PAIRING_REQUIRED = 4401
CLOSE_BAD_PIN = 4403
CLOSE_UNSUPPORTED = 4426
CLOSE_LOCKED_OUT = 4429

ERROR_CLOSE_CODES = {
    "bad_request": CLOSE_BAD_REQUEST,
    "pairing_required": CLOSE_PAIRING_REQUIRED,
    "bad_pin": CLOSE_BAD_PIN,
    "unsupported_version": CLOSE_UNSUPPORTED,
    "locked_out": CLOSE_LOCKED_OUT,
}

HELLO_TIMEOUT = 5.0
MAX_MESSAGE_BYTES = 4096


def dumps(obj: dict) -> str:
    return json.dumps(obj, separators=(",", ":"))


def loads(raw) -> dict | None:
    """Parse one JSON object message; None for anything malformed."""
    if isinstance(raw, bytes):
        try:
            raw = raw.decode("utf-8")
        except UnicodeDecodeError:
            return None
    try:
        data = json.loads(raw)
    except (TypeError, ValueError):
        return None
    return data if isinstance(data, dict) else None


def finite(value, default=0.0, limit=1e5) -> float:
    """Coerce to a finite float clamped to +-limit (guards against NaN/inf/huge input)."""
    try:
        v = float(value)
    except (TypeError, ValueError):
        return default
    if v != v or v in (float("inf"), float("-inf")):
        return default
    return max(-limit, min(limit, v))
