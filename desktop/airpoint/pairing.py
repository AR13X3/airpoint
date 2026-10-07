"""PIN pairing and device tokens.

A phone proves physical access once by typing the 6-digit PIN shown on the PC. It then gets
a random token that it presents on later connections. Only a SHA-256 hash of each token is
stored. Wrong PINs are rate-limited: after MAX_ATTEMPTS failures inside ATTEMPT_WINDOW the
PIN rotates and pairing locks. Each consecutive lockout doubles (1 min, 2, 4 ... up to an
hour) until someone pairs successfully, which leaves a guesser roughly 120 tries a day
against a PIN that keeps changing.
"""

import hashlib
import hmac
import secrets
import threading
import time
from dataclasses import dataclass

from .config import ConfigStore, PairedDevice

PIN_LENGTH = 6
MAX_ATTEMPTS = 5
ATTEMPT_WINDOW = 300.0
LOCKOUT = 60.0
MAX_LOCKOUT = 3600.0


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def new_pin() -> str:
    return "".join(secrets.choice("0123456789") for _ in range(PIN_LENGTH))


def format_pin(pin: str) -> str:
    return f"{pin[:3]} {pin[3:]}"


@dataclass
class AuthResult:
    ok: bool
    device: PairedDevice | None = None
    token: str | None = None        # set only when a new pairing issued a token
    error: str | None = None        # pairing_required | bad_pin | locked_out
    retry_after: float = 0.0


class Pairing:
    def __init__(self, store: ConfigStore, clock=time.monotonic, wall=time.time):
        self.store = store
        self._clock = clock
        self._wall = wall
        self._lock = threading.Lock()
        self.pin = new_pin()
        self._failures: list[float] = []
        self._locked_until = 0.0
        self._lockouts = 0  # consecutive, reset by a successful pairing

    # ---- PIN -------------------------------------------------------------------------

    def rotate_pin(self) -> str:
        with self._lock:
            self.pin = new_pin()
            self._failures.clear()
            return self.pin

    def locked_for(self) -> float:
        return max(0.0, self._locked_until - self._clock())

    # ---- authentication ---------------------------------------------------------------

    def authenticate(self, device_id: str, device_name: str, token: str | None,
                     pin: str | None) -> AuthResult:
        device_name = (device_name or "Phone").strip()[:64] or "Phone"
        if token:
            device = self._match_token(token)
            if device:
                self.store.update(lambda s: self._touch(s, device.device_id, device_name))
                return AuthResult(ok=True, device=device)
            if not pin:
                return AuthResult(ok=False, error="pairing_required")
        if not pin:
            return AuthResult(ok=False, error="pairing_required")
        return self._pair_with_pin(device_id, device_name, pin)

    def _pair_with_pin(self, device_id: str, device_name: str, pin: str) -> AuthResult:
        with self._lock:
            now = self._clock()
            if now < self._locked_until:
                return AuthResult(ok=False, error="locked_out", retry_after=self._locked_until - now)
            if not hmac.compare_digest(pin.strip().replace(" ", ""), self.pin):
                self._failures = [t for t in self._failures if now - t < ATTEMPT_WINDOW] + [now]
                if len(self._failures) >= MAX_ATTEMPTS:
                    duration = min(MAX_LOCKOUT, LOCKOUT * 2 ** self._lockouts)
                    self._lockouts += 1
                    self._locked_until = now + duration
                    self._failures.clear()
                    self.pin = new_pin()
                    return AuthResult(ok=False, error="locked_out", retry_after=duration)
                return AuthResult(ok=False, error="bad_pin")
            # Success: a PIN is single-use.
            self.pin = new_pin()
            self._failures.clear()
            self._lockouts = 0

        token = secrets.token_urlsafe(32)
        device = PairedDevice(
            device_id=(device_id or secrets.token_hex(8))[:64],
            name=device_name,
            token_hash=_hash(token),
            paired_at=self._wall(),
            last_seen=self._wall(),
        )

        def add(s):
            s.devices = [d for d in s.devices if d.device_id != device.device_id] + [device]

        self.store.update(add)
        return AuthResult(ok=True, device=device, token=token)

    def _match_token(self, token: str) -> PairedDevice | None:
        h = _hash(token)
        for d in list(self.store.settings.devices):
            if hmac.compare_digest(d.token_hash, h):
                return d
        return None

    def _touch(self, settings, device_id: str, name: str) -> None:
        for d in settings.devices:
            if d.device_id == device_id:
                d.last_seen = self._wall()
                d.name = name

    # ---- management ---------------------------------------------------------------------

    @property
    def devices(self) -> list[PairedDevice]:
        return list(self.store.settings.devices)

    def forget(self, device_id: str) -> None:
        self.store.update(lambda s: setattr(s, "devices", [d for d in s.devices if d.device_id != device_id]))

    def forget_all(self) -> None:
        self.store.update(lambda s: setattr(s, "devices", []))
