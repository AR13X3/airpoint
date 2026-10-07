"""Small OS integrations: local IP, taskbar theme, start-at-login, opening folders."""

import logging
import os
import socket
import subprocess
import sys

log = logging.getLogger("airpoint")

_RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
_RUN_VALUE = "Airpoint"


def primary_ip() -> str:
    """Best guess at the LAN address phones should reach (no packets are sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def taskbar_is_light() -> bool:
    if sys.platform != "win32":
        return False
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize") as key:
            return winreg.QueryValueEx(key, "SystemUsesLightTheme")[0] == 1
    except OSError:
        return False


def autostart_supported() -> bool:
    # Only the packaged .exe has a stable command line to register.
    return sys.platform == "win32" and getattr(sys, "frozen", False)


def autostart_enabled() -> bool:
    if not autostart_supported():
        return False
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY) as key:
            value = winreg.QueryValueEx(key, _RUN_VALUE)[0]
        return os.path.normcase(sys.executable) in os.path.normcase(value)
    except OSError:
        return False


def set_autostart(enabled: bool) -> None:
    if not autostart_supported():
        return
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY, 0, winreg.KEY_SET_VALUE) as key:
        if enabled:
            winreg.SetValueEx(key, _RUN_VALUE, 0, winreg.REG_SZ, f'"{sys.executable}"')
        else:
            try:
                winreg.DeleteValue(key, _RUN_VALUE)
            except FileNotFoundError:
                pass


def open_folder(path) -> None:
    try:
        if sys.platform == "win32":
            os.startfile(path)  # noqa: S606 - opening our own data folder
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(path)])
        else:
            subprocess.Popen(["xdg-open", str(path)])
    except OSError as e:
        log.warning("Could not open %s: %s", path, e)
