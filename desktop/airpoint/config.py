"""Persistent settings and paths (per-user app data directory)."""

import json
import logging
import os
import socket
import sys
import tempfile
import threading
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path

log = logging.getLogger("airpoint")

DEFAULT_PORT = 8765


def data_dir() -> Path:
    override = os.environ.get("AIRPOINT_DATA_DIR")  # portable installs and testing
    if override:
        path = Path(override)
    elif sys.platform == "win32":
        base = Path(os.environ.get("APPDATA") or Path.home() / "AppData" / "Roaming")
        path = base / "Airpoint"
    elif sys.platform == "darwin":
        path = Path.home() / "Library" / "Application Support" / "Airpoint"
    else:
        path = Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config") / "airpoint"
    path.mkdir(parents=True, exist_ok=True)
    return path


@dataclass
class PairedDevice:
    device_id: str
    name: str
    token_hash: str
    paired_at: float
    last_seen: float = 0.0


@dataclass
class Settings:
    pc_id: str = field(default_factory=lambda: uuid.uuid4().hex)
    name: str = field(default_factory=lambda: socket.gethostname())
    port: int = DEFAULT_PORT
    devices: list = field(default_factory=list)  # list[PairedDevice]


class ConfigStore:
    """Loads and atomically saves Settings as JSON. Thread-safe."""

    def __init__(self, path: Path | None = None):
        self.path = path or data_dir() / "config.json"
        self._lock = threading.RLock()
        self.settings = self._load()

    def _load(self) -> Settings:
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except FileNotFoundError:
            settings = Settings()
            self._write(settings)
            return settings
        except (OSError, ValueError) as e:
            log.warning("Config unreadable (%s); starting fresh", e)
            return Settings()
        devices = []
        for d in raw.get("devices", []):
            try:
                devices.append(PairedDevice(**d))
            except TypeError:
                continue
        defaults = Settings()
        return Settings(
            pc_id=raw.get("pc_id") or defaults.pc_id,
            name=raw.get("name") or defaults.name,
            port=int(raw.get("port") or DEFAULT_PORT),
            devices=devices,
        )

    def _write(self, settings: Settings) -> None:
        data = asdict(settings)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.path.parent, prefix=".config-", suffix=".json")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        os.replace(tmp, self.path)

    def save(self) -> None:
        with self._lock:
            try:
                self._write(self.settings)
            except OSError as e:
                log.error("Could not save config: %s", e)

    def update(self, fn) -> None:
        """Mutate settings under the lock, then save."""
        with self._lock:
            fn(self.settings)
            self.save()
