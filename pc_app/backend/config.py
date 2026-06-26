"""Persistent JSON configuration for MiniPC."""

from __future__ import annotations

import json
import sys
import os
from pathlib import Path

CONFIG_DIR = Path.home() / ".minipc"
CONFIG_FILE = CONFIG_DIR / "config.json"

_DEFAULT = {
    "movie_directories": [],
    "photo_directories": [],
    "startup_on_boot": False,
    "update_enabled": False,
    "surround_sound": False,
    "kiosk_ips": [],
    "kiosk_last_ip": "",
}


class Config:
    """Singleton configuration manager backed by a JSON file."""

    _instance: Config | None = None

    @classmethod
    def get(cls) -> Config:
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self) -> None:
        self._data: dict = dict(_DEFAULT)
        self._load()

    def _load(self) -> None:
        try:
            if CONFIG_FILE.exists():
                with open(CONFIG_FILE, encoding="utf-8") as f:
                    saved = json.load(f)
                self._data.update(saved)
        except Exception:
            pass

    def save(self) -> None:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(self._data, f, indent=2)

    def __getitem__(self, key: str):
        return self._data.get(key, _DEFAULT.get(key))

    def __setitem__(self, key: str, value) -> None:
        self._data[key] = value
        self.save()

    # ---- Convenience helpers ----

    @property
    def movie_dirs(self) -> list[str]:
        return self._data.get("movie_directories", [])

    @movie_dirs.setter
    def movie_dirs(self, dirs: list[str]) -> None:
        self["movie_directories"] = dirs

    @property
    def photo_dirs(self) -> list[str]:
        return self._data.get("photo_directories", [])

    @photo_dirs.setter
    def photo_dirs(self, dirs: list[str]) -> None:
        self["photo_directories"] = dirs

    # ---- Startup on boot (Windows Registry) ----

    @staticmethod
    def set_startup_on_boot(enabled: bool) -> tuple[bool, str]:
        """Add or remove MiniPC from Windows startup."""
        try:
            import winreg
            key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER, key_path, 0,
                winreg.KEY_SET_VALUE | winreg.KEY_QUERY_VALUE,
            )
            app_name = "MiniPC"
            if enabled:
                script = Path(__file__).resolve().parent.parent / "main.py"
                cmd = f'"{sys.executable}" "{script}"'
                winreg.SetValueEx(key, app_name, 0, winreg.REG_SZ, cmd)
                winreg.CloseKey(key)
                return True, "Added to startup"
            else:
                try:
                    winreg.DeleteValue(key, app_name)
                except FileNotFoundError:
                    pass
                winreg.CloseKey(key)
                return True, "Removed from startup"
        except Exception as e:
            return False, str(e)

    @staticmethod
    def is_startup_on_boot() -> bool:
        """Check if MiniPC is in Windows startup."""
        try:
            import winreg
            key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER, key_path, 0,
                winreg.KEY_QUERY_VALUE,
            )
            try:
                winreg.QueryValueEx(key, "MiniPC")
                winreg.CloseKey(key)
                return True
            except FileNotFoundError:
                winreg.CloseKey(key)
                return False
        except Exception:
            return False
