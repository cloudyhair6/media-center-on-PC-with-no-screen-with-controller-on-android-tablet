"""Media file scanner for discovering video files."""
from pathlib import Path
from PySide6.QtCore import QThread, Signal
import os
import time


VIDEO_EXTENSIONS = {
    '.mp4', '.mkv', '.avi', '.mov', '.wmv', '.flv',
    '.m4v', '.webm', '.mpg', '.mpeg', '.ts', '.vob',
    '.3gp', '.ogv', '.divx', '.m2ts'
}


class MovieInfo:
    """Stores information about a discovered movie file."""
    def __init__(self, path: Path):
        self.path = path
        self.name = path.stem
        self.filename = path.name
        self.extension = path.suffix.lower()
        self.folder = str(path.parent)
        try:
            stat = path.stat()
            self.size = stat.st_size
            self.modified = stat.st_mtime
        except OSError:
            self.size = 0
            self.modified = 0

    @property
    def size_display(self) -> str:
        """Human-readable file size."""
        size = self.size
        for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
            if size < 1024:
                return f"{size:.1f} {unit}"
            size /= 1024
        return f"{size:.1f} PB"

    @property
    def modified_display(self) -> str:
        """Human-readable modification date."""
        if self.modified == 0:
            return "Unknown"
        return time.strftime("%b %d, %Y", time.localtime(self.modified))


class MediaScanner(QThread):
    """Scans directories for video files in a background thread."""
    scan_complete = Signal(list)  # Emits list of MovieInfo
    progress = Signal(int, int)  # current, total (directories scanned)
    error = Signal(str)

    def __init__(self, directories: list[str] | None = None):
        super().__init__()
        if directories is None:
            directories = self._default_directories()
        self.directories = directories
        self._movies: list[MovieInfo] = []

    @staticmethod
    def _default_directories() -> list[str]:
        """Get default directories to scan."""
        dirs = []
        home = Path.home()
        for folder in ["Videos", "Downloads", "Movies"]:
            p = home / folder
            if p.exists():
                dirs.append(str(p))
        # Also check common drive letters for external drives
        for letter in 'DEFGH':
            drive = Path(f"{letter}:/")
            if drive.exists() and drive != Path("C:/"):
                dirs.append(str(drive))
        return dirs

    def run(self):
        """Execute the scan."""
        self._movies = []
        total_dirs = len(self.directories)
        for i, directory in enumerate(self.directories):
            self.progress.emit(i, total_dirs)
            root = Path(directory)
            if not root.exists():
                continue
            try:
                for f in root.rglob('*'):
                    if self.isInterruptionRequested():
                        return
                    try:
                        if f.is_file() and f.suffix.lower() in VIDEO_EXTENSIONS:
                            self._movies.append(MovieInfo(f))
                    except (PermissionError, OSError):
                        continue
            except (PermissionError, OSError) as e:
                self.error.emit(f"Cannot access {directory}: {e}")
                continue

        # Sort by name
        self._movies.sort(key=lambda m: m.name.lower())
        self.progress.emit(total_dirs, total_dirs)
        self.scan_complete.emit(self._movies)

    @property
    def movies(self) -> list[MovieInfo]:
        """Get the list of discovered movies."""
        return self._movies
