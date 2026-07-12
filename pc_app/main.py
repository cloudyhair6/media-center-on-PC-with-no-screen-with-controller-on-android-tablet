"""Media Centre v2.0 — Passive media center server."""
import sys
import os
import subprocess
import importlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

_REQUIRED_PACKAGES = {
    'PySide6': 'PySide6',
    'pycaw': 'pycaw',
    'comtypes': 'comtypes',
    'psutil': 'psutil',
}

try:
    from backend.startup_announcer import play_starting_sound
    play_starting_sound()
except Exception as e:
    print(f"Failed to play starting sound: {e}")

def _ensure_dependencies():
    missing = []
    for import_name, pip_name in _REQUIRED_PACKAGES.items():
        try:
            importlib.import_module(import_name)
        except ImportError:
            missing.append(pip_name)
    if not missing:
        return
    print(f'[Media Centre] Installing: {", ".join(missing)}')
    try:
        subprocess.check_call(
            [sys.executable, '-m', 'pip', 'install', '--quiet', *missing],
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
        )
    except subprocess.CalledProcessError as e:
        print(f'[Media Centre] Install failed: {e}')
        sys.exit(1)
    importlib.invalidate_caches()

_ensure_dependencies()

from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QPalette, QColor
from styles.theme import Colors, get_global_stylesheet
from ui.main_window import MainWindow

def main():
    os.environ['QT_ENABLE_HIGHDPI_SCALING'] = '1'
    app = QApplication(sys.argv)
    app.setApplicationName('Media Centre')
    
    palette = QPalette()
    palette.setColor(QPalette.ColorRole.Window, QColor(Colors.BG_PRIMARY))
    palette.setColor(QPalette.ColorRole.WindowText, QColor(Colors.TEXT_PRIMARY))
    palette.setColor(QPalette.ColorRole.Base, QColor(Colors.BG_SECONDARY))
    palette.setColor(QPalette.ColorRole.Text, QColor(Colors.TEXT_PRIMARY))
    palette.setColor(QPalette.ColorRole.Button, QColor(Colors.BG_TERTIARY))
    palette.setColor(QPalette.ColorRole.ButtonText, QColor(Colors.TEXT_PRIMARY))
    palette.setColor(QPalette.ColorRole.Highlight, QColor(Colors.ACCENT))
    app.setPalette(palette)
    app.setStyleSheet(get_global_stylesheet())
    
    window = MainWindow()
    window.show()
    sys.exit(app.exec())

if __name__ == '__main__':
    main()
