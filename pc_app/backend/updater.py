"""Auto-updater — checks GitHub for new versions and self-updates."""

from __future__ import annotations

import json
import os
import sys
import shutil
import tempfile
from pathlib import Path
from urllib.request import urlopen, Request
from urllib.error import URLError

from PySide6.QtCore import QThread, Signal


_REPO = "cloudyhair6/music-tv-app-for-minisforum-z83-f"
_VERSION_URL = (
    f"https://raw.githubusercontent.com/{_REPO}/refs/heads/main/current-version.txt"
)
_INDEX_URL_TEMPLATE = (
    f"https://raw.githubusercontent.com/{_REPO}/refs/tags/{{version}}/download-index.json"
)
_RAW_FILE_TEMPLATE = (
    f"https://raw.githubusercontent.com/{_REPO}/refs/tags/{{version}}/{{path}}"
)

# Project root directory
PROJECT_ROOT = Path(__file__).resolve().parent.parent


def get_local_version() -> str:
    """Read the local version from version.txt."""
    version_file = PROJECT_ROOT / "version.txt"
    try:
        return version_file.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        return "0.0.0"


def get_remote_version() -> str | None:
    """Fetch the latest version string from GitHub."""
    try:
        req = Request(_VERSION_URL, headers={"User-Agent": "Media Centre-Updater/1.0"})
        with urlopen(req, timeout=10) as resp:
            return resp.read().decode("utf-8").strip()
    except (URLError, OSError, ValueError):
        return None


def _parse_version(v: str) -> tuple[int, ...]:
    """Parse a version string like '1.2.3' into a comparable tuple."""
    try:
        return tuple(int(x) for x in v.strip().split("."))
    except (ValueError, AttributeError):
        return (0, 0, 0)


def is_update_available(local: str, remote: str) -> bool:
    """Return True if remote version is newer than local."""
    return _parse_version(remote) > _parse_version(local)


class UpdateWorker(QThread):
    """Background thread that checks for and applies updates.

    Signals:
        check_complete(bool, str, str): (update_available, local_ver, remote_ver)
        download_progress(int, int, str): (current_file, total_files, filename)
        update_complete(bool, str): (success, message)
        error(str): error message
    """

    check_complete = Signal(bool, str, str)
    download_progress = Signal(int, int, str)
    update_complete = Signal(bool, str)
    error = Signal(str)

    def __init__(self, auto_apply: bool = False) -> None:
        super().__init__()
        self._auto_apply = auto_apply

    def run(self) -> None:
        # Step 1: Check versions
        local_ver = get_local_version()
        remote_ver = get_remote_version()

        if remote_ver is None:
            self.error.emit("Could not connect to update server.")
            self.check_complete.emit(False, local_ver, "unknown")
            return

        update_available = is_update_available(local_ver, remote_ver)
        self.check_complete.emit(update_available, local_ver, remote_ver)

        if not update_available or not self._auto_apply:
            return

        # Step 2: Fetch download index
        try:
            self._apply_update(remote_ver)
        except Exception as e:
            self.error.emit(f"Update failed: {e}")
            self.update_complete.emit(False, str(e))

    def _apply_update(self, version: str) -> None:
        """Download and apply the update."""
        index_url = _INDEX_URL_TEMPLATE.format(version=version)
        try:
            req = Request(index_url, headers={"User-Agent": "Media Centre-Updater/1.0"})
            with urlopen(req, timeout=15) as resp:
                index = json.loads(resp.read().decode("utf-8"))
        except (URLError, OSError, json.JSONDecodeError) as e:
            self.error.emit(f"Could not fetch update index: {e}")
            return

        files = index.get("files", [])
        total = len(files)

        if total == 0:
            self.update_complete.emit(True, "No files to update.")
            return

        # Download each file
        for i, entry in enumerate(files):
            rel_path = entry.get("path", "")
            if not rel_path:
                continue

            self.download_progress.emit(i + 1, total, rel_path)

            file_url = _RAW_FILE_TEMPLATE.format(version=version, path=rel_path)
            target_path = PROJECT_ROOT / rel_path

            try:
                req = Request(file_url, headers={"User-Agent": "Media Centre-Updater/1.0"})
                with urlopen(req, timeout=30) as resp:
                    data = resp.read()

                # Ensure parent directory exists
                target_path.parent.mkdir(parents=True, exist_ok=True)

                # Write file
                target_path.write_bytes(data)

            except (URLError, OSError) as e:
                self.error.emit(f"Failed to download {rel_path}: {e}")
                continue

        # Update local version file
        version_file = PROJECT_ROOT / "version.txt"
        version_file.write_text(version + "\n", encoding="utf-8")

        self.update_complete.emit(True, f"Updated to version {version}")
