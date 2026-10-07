# PyInstaller build for the Windows tray app:  pyinstaller airpoint.spec
# Output: dist/Airpoint.exe (single file, no console window).
# -*- mode: python ; coding: utf-8 -*-

import re
from pathlib import Path

version = re.search(r'__version__ = "([^"]+)"', Path("airpoint/__init__.py").read_text()).group(1)
parts = [int(p) for p in version.split(".")] + [0]
Path("build").mkdir(exist_ok=True)
Path("build/version_info.txt").write_text(f"""VSVersionInfo(
  ffi=FixedFileInfo(filevers={tuple(parts[:4])}, prodvers={tuple(parts[:4])}),
  kids=[StringFileInfo([StringTable('040904B0', [
    StringStruct('CompanyName', 'AR13X3'),
    StringStruct('FileDescription', 'Airpoint'),
    StringStruct('FileVersion', '{version}'),
    StringStruct('InternalName', 'Airpoint'),
    StringStruct('LegalCopyright', 'Copyright (c) 2026 AR13X3. MIT License.'),
    StringStruct('OriginalFilename', 'Airpoint.exe'),
    StringStruct('ProductName', 'Airpoint'),
    StringStruct('ProductVersion', '{version}')])]),
    VarFileInfo([VarStruct('Translation', [1033, 1200])])])
""")

a = Analysis(
    ["airpoint_app.py"],
    hiddenimports=["pynput.mouse._win32", "pynput.keyboard._win32", "pystray._win32"],
    excludes=["tkinter", "unittest", "pydoc", "pytest"],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="Airpoint",
    console=False,
    upx=False,
    icon=["assets/airpoint.ico"],
    version="build/version_info.txt",
)
