"""Settings screen with audio, display, network, media, and system settings."""

from __future__ import annotations

import platform
import socket

from PySide6.QtCore import Qt, Signal, QTimer
from PySide6.QtGui import QKeyEvent
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QScrollArea, QFrame, QSizePolicy, QFileDialog,
)

from styles.theme import Colors, Sizes
from ui.components import SliderSetting, ToggleSetting
from backend.system_control import SystemControl
from backend.config import Config


class _SectionHeader(QLabel):
    def __init__(self, text: str, parent: QWidget | None = None) -> None:
        super().__init__(text, parent)
        self.setStyleSheet(
            f"font-size: {Sizes.FONT_H3}px; font-weight: 600; "
            f"color: {Colors.TEXT_SECONDARY}; "
            f"padding-top: {Sizes.SPACING}px; padding-bottom: {Sizes.SPACING_SM}px; "
            f"background: transparent;"
        )


class _InfoRow(QFrame):
    def __init__(self, label: str, value: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setStyleSheet(
            f"background: {Colors.BG_CARD}; border-radius: {Sizes.CARD_RADIUS_SM}px; "
            f"padding: {Sizes.SPACING_SM}px {Sizes.SPACING}px;"
        )
        layout = QHBoxLayout(self)
        layout.setContentsMargins(Sizes.SPACING, Sizes.SPACING_SM, Sizes.SPACING, Sizes.SPACING_SM)
        lbl = QLabel(label)
        lbl.setStyleSheet(f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_SECONDARY}; background: transparent;")
        layout.addWidget(lbl)
        layout.addStretch()
        self._val = QLabel(value)
        self._val.setStyleSheet(
            f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_PRIMARY}; font-weight: 500; background: transparent;"
        )
        layout.addWidget(self._val)

    def set_value(self, text: str) -> None:
        self._val.setText(text)


class _DeviceRow(QFrame):
    def __init__(self, name: str, status: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setStyleSheet(
            f"background: {Colors.BG_CARD}; border-radius: {Sizes.CARD_RADIUS_SM}px; "
            f"padding: 4px {Sizes.SPACING}px;"
        )
        layout = QHBoxLayout(self)
        layout.setContentsMargins(Sizes.SPACING, Sizes.SPACING_SM, Sizes.SPACING, Sizes.SPACING_SM)
        name_lbl = QLabel(f"  {name}")
        name_lbl.setStyleSheet(f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_PRIMARY}; background: transparent;")
        layout.addWidget(name_lbl)
        layout.addStretch()
        status_color = Colors.SUCCESS if "Connected" in status else Colors.TEXT_SECONDARY
        status_lbl = QLabel(status)
        status_lbl.setStyleSheet(f"font-size: {Sizes.FONT_SMALL}px; color: {status_color}; background: transparent;")
        layout.addWidget(status_lbl)


class _DirRow(QFrame):
    """A row showing a configured media directory with a remove button."""

    removed = Signal(str)

    def __init__(self, path: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._path = path
        self.setStyleSheet(
            f"background: {Colors.BG_CARD}; border-radius: {Sizes.CARD_RADIUS_SM}px; "
            f"padding: 4px {Sizes.SPACING}px;"
        )
        layout = QHBoxLayout(self)
        layout.setContentsMargins(Sizes.SPACING, 4, Sizes.SPACING, 4)
        lbl = QLabel(path)
        lbl.setStyleSheet(f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_PRIMARY}; background: transparent;")
        layout.addWidget(lbl, 1)
        rm_btn = QPushButton("X")
        rm_btn.setFixedSize(28, 28)
        rm_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        rm_btn.setStyleSheet(
            f"QPushButton {{ background: {Colors.DANGER}; color: white; border: none; "
            f"border-radius: 14px; font-size: 14px; font-weight: bold; }} "
            f"QPushButton:hover {{ background: #ff7777; }}"
        )
        rm_btn.clicked.connect(lambda: self.removed.emit(self._path))
        layout.addWidget(rm_btn)


def _flat_btn_style() -> str:
    return (
        f"QPushButton {{ background: {Colors.BG_TERTIARY}; color: {Colors.TEXT_PRIMARY}; "
        f"border: none; border-radius: {Sizes.BUTTON_RADIUS}px; "
        f"padding: 10px 20px; font-size: {Sizes.FONT_BODY}px; }} "
        f"QPushButton:hover {{ background: {Colors.BG_CARD_HOVER}; }} "
        f"QPushButton:focus {{ border: 2px solid {Colors.ACCENT}; }}"
    )


class SettingsScreen(QWidget):
    """System settings panel with live controls."""

    go_back = Signal()
    setting_changed = Signal(str, str)  # key, value

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("SettingsScreen")
        self._bt_device_rows: list[_DeviceRow] = []
        self._dir_rows: list[_DirRow] = []
        self._cfg = Config.get()
        self._setup_ui()
        self._load_initial_values()

    def _setup_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(Sizes.SPACING_LG, Sizes.SPACING_LG, Sizes.SPACING_LG, Sizes.SPACING_LG)
        root.setSpacing(Sizes.SPACING)

        # Header
        header = QHBoxLayout()
        back_btn = QPushButton("<  Back")
        back_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        back_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        back_btn.setStyleSheet(_flat_btn_style())
        back_btn.clicked.connect(self.go_back.emit)
        header.addWidget(back_btn)
        title = QLabel("Settings")
        title.setStyleSheet(
            f"font-size: {Sizes.FONT_H1}px; font-weight: 700; "
            f"color: {Colors.TEXT_PRIMARY}; background: transparent;"
        )
        header.addWidget(title)
        header.addStretch()
        root.addLayout(header)

        # Scrollable content
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setStyleSheet(
            "QScrollArea { border: none; background: transparent; } "
            f"QScrollBar:vertical {{ width: 6px; background: transparent; }} "
            f"QScrollBar::handle:vertical {{ background: {Colors.TEXT_MUTED}; border-radius: 3px; }} "
            "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }"
        )
        content = QWidget()
        content.setStyleSheet("background: transparent;")
        self._cl = QVBoxLayout(content)
        self._cl.setContentsMargins(0, 0, 0, 0)
        self._cl.setSpacing(Sizes.SPACING_SM)

        # ---- Audio & Display
        self._cl.addWidget(_SectionHeader("Audio & Display"))

        self._volume_slider = SliderSetting("Volume", "VOL", 0, 100, 50, content)
        self._volume_slider.value_changed.connect(lambda v: SystemControl.set_volume(v))
        self._cl.addWidget(self._volume_slider)

        self._brightness_slider = SliderSetting("Brightness", "BRT", 0, 100, 50, content)
        self._brightness_slider.value_changed.connect(lambda v: SystemControl.set_brightness(v))
        self._cl.addWidget(self._brightness_slider)

        self._surround_toggle = ToggleSetting("Surround Sound (Speaker Fill)", "5.1", is_on=self._cfg["surround_sound"], parent=content)
        self._surround_toggle.toggled.connect(self._on_surround_toggled)
        self._cl.addWidget(self._surround_toggle)

        self._cl.addSpacing(Sizes.SPACING_LG)

        # ---- Media Directories
        self._cl.addWidget(_SectionHeader("Media Directories"))
        self._dirs_container = QVBoxLayout()
        self._dirs_container.setSpacing(4)
        self._cl.addLayout(self._dirs_container)

        add_dir_btn = QPushButton("+ Add Folder")
        add_dir_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        add_dir_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        add_dir_btn.setStyleSheet(_flat_btn_style())
        add_dir_btn.clicked.connect(self._add_directory)
        self._cl.addWidget(add_dir_btn)

        self._cl.addSpacing(Sizes.SPACING_LG)

        # ---- Network
        self._cl.addWidget(_SectionHeader("Network"))

        self._wifi_toggle = ToggleSetting("Wi-Fi", "W", is_on=False, status_text="Checking...", parent=content)
        self._wifi_toggle.toggled.connect(self._on_wifi_toggled)
        self._cl.addWidget(self._wifi_toggle)

        self._network_list_label = QLabel()
        self._network_list_label.setWordWrap(True)
        self._network_list_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_SECONDARY}; "
            f"padding: {Sizes.SPACING_SM}px {Sizes.SPACING}px; background: transparent;"
        )
        self._network_list_label.setVisible(False)
        self._cl.addWidget(self._network_list_label)

        self._cl.addSpacing(Sizes.SPACING_LG)

        # ---- Bluetooth
        self._cl.addWidget(_SectionHeader("Bluetooth"))

        self._bt_toggle = ToggleSetting("Bluetooth", "BT", is_on=False, status_text="Checking...", parent=content)
        self._bt_toggle.toggled.connect(self._on_bluetooth_toggled)
        self._cl.addWidget(self._bt_toggle)

        self._bt_devices_container = QVBoxLayout()
        self._bt_devices_container.setSpacing(Sizes.SPACING_SM)
        self._cl.addLayout(self._bt_devices_container)

        self._bt_status_label = QLabel()
        self._bt_status_label.setWordWrap(True)
        self._bt_status_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_SECONDARY}; "
            f"padding: {Sizes.SPACING_SM}px {Sizes.SPACING}px; background: transparent;"
        )
        self._bt_status_label.setVisible(False)
        self._cl.addWidget(self._bt_status_label)

        self._cl.addSpacing(Sizes.SPACING_LG)

        # ---- App Settings
        self._cl.addWidget(_SectionHeader("App Settings"))

        # Window Mode setting
        mode_row = QFrame()
        mode_row.setStyleSheet(f"background: {Colors.BG_CARD}; border-radius: {Sizes.CARD_RADIUS_SM}px; padding: 10px {Sizes.SPACING}px;")
        mode_layout = QHBoxLayout(mode_row)
        mode_layout.setContentsMargins(Sizes.SPACING, Sizes.SPACING_SM, Sizes.SPACING, Sizes.SPACING_SM)
        mode_lbl = QLabel("Window Mode")
        mode_lbl.setStyleSheet(f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_PRIMARY}; background: transparent;")
        mode_layout.addWidget(mode_lbl)
        mode_layout.addStretch()
        
        from PySide6.QtWidgets import QComboBox
        self._window_mode_cb = QComboBox()
        self._window_mode_cb.addItems(["Fullscreen", "Borderless", "Windowed"])
        self._window_mode_cb.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self._window_mode_cb.setStyleSheet(
            f"QComboBox {{ background: {Colors.BG_TERTIARY}; color: {Colors.TEXT_PRIMARY}; "
            f"border: 1px solid {Colors.BORDER}; border-radius: 6px; padding: 6px; min-width: 120px; }} "
            f"QComboBox:focus {{ border: 1px solid {Colors.ACCENT}; }}"
        )
        self._window_mode_cb.setCurrentText(self._cfg._data.get("window_mode", "Fullscreen"))
        self._window_mode_cb.currentTextChanged.connect(self._on_window_mode_changed)
        mode_layout.addWidget(self._window_mode_cb)
        self._cl.addWidget(mode_row)

        self._startup_toggle = ToggleSetting("Start on Boot", "ST", is_on=Config.is_startup_on_boot(), parent=content)
        self._startup_toggle.toggled.connect(self._on_startup_toggled)
        self._cl.addWidget(self._startup_toggle)

        self._update_toggle = ToggleSetting("Auto-Update", "UP", is_on=self._cfg["update_enabled"], parent=content)
        self._update_toggle.toggled.connect(self._on_update_toggled)
        self._cl.addWidget(self._update_toggle)

        self._cl.addSpacing(Sizes.SPACING_LG)

        # ---- Remote Control
        self._cl.addWidget(_SectionHeader("Remote Control"))
        self._remote_info = _InfoRow("Kindle Remote URL", "Starting...", content)
        self._cl.addWidget(self._remote_info)

        self._cl.addSpacing(Sizes.SPACING_LG)

        # ---- System Info
        self._cl.addWidget(_SectionHeader("System"))
        self._cl.addWidget(_InfoRow("OS", f"{platform.system()} {platform.release()}", content))
        self._cl.addWidget(_InfoRow("Hostname", socket.gethostname(), content))

        try:
            from backend.updater import get_local_version
            ver = get_local_version()
        except Exception:
            ver = "unknown"
        self._cl.addWidget(_InfoRow("App Version", ver, content))

        self._cl.addStretch()
        scroll.setWidget(content)
        root.addWidget(scroll, 1)

    def set_remote_url(self, url: str) -> None:
        self._remote_info.set_value(url)

    # ---- Initial data
    def _load_initial_values(self) -> None:
        try:
            self._volume_slider.set_value(SystemControl.get_volume())
        except Exception:
            pass
        try:
            self._brightness_slider.set_value(SystemControl.get_brightness())
        except Exception:
            pass
        self._refresh_wifi_status()
        self._refresh_bluetooth_status()
        self._refresh_dirs()

    # ---- Media directories
    def _refresh_dirs(self) -> None:
        for row in self._dir_rows:
            row.setParent(None)
            row.deleteLater()
        self._dir_rows.clear()

        dirs = self._cfg.movie_dirs
        if not dirs:
            lbl = QLabel("  Using default directories (Videos, Downloads)")
            lbl.setStyleSheet(
                f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_MUTED}; background: transparent;"
            )
            self._dirs_container.addWidget(lbl)
            self._dir_rows.append(lbl)  # type: ignore
        else:
            for d in dirs:
                row = _DirRow(d)
                row.removed.connect(self._remove_directory)
                self._dirs_container.addWidget(row)
                self._dir_rows.append(row)

    def _add_directory(self) -> None:
        folder = QFileDialog.getExistingDirectory(self, "Select Media Folder")
        if folder:
            dirs = list(self._cfg.movie_dirs)
            if folder not in dirs:
                dirs.append(folder)
                self._cfg.movie_dirs = dirs
                self._refresh_dirs()

    def _remove_directory(self, path: str) -> None:
        dirs = [d for d in self._cfg.movie_dirs if d != path]
        self._cfg.movie_dirs = dirs
        self._refresh_dirs()

    # ---- WiFi
    def _refresh_wifi_status(self) -> None:
        try:
            enabled = SystemControl.is_wifi_enabled()
            status = SystemControl.get_wifi_status()
            self._wifi_toggle.set_state(enabled)
            if status.get("connected") and status.get("network"):
                self._wifi_toggle.set_status_text(f"Connected to {status['network']}")
            elif enabled:
                self._wifi_toggle.set_status_text("Enabled - not connected")
            else:
                self._wifi_toggle.set_status_text("Disabled")
        except Exception:
            self._wifi_toggle.set_status_text("Unavailable")

    def _on_wifi_toggled(self, is_on: bool) -> None:
        self._wifi_toggle.set_status_text("Applying...")
        success, message = SystemControl.set_wifi_enabled(is_on)
        if not success:
            self._wifi_toggle.set_status_text(f"Error: {message}")
            self._network_list_label.setText("Wi-Fi toggle may need admin privileges.\nTry running as Administrator.")
            self._network_list_label.setVisible(True)
            QTimer.singleShot(500, self._refresh_wifi_status)
            return
        QTimer.singleShot(2000, self._refresh_wifi_status)

    # ---- Bluetooth
    def _refresh_bluetooth_status(self) -> None:
        try:
            enabled = SystemControl.is_bluetooth_enabled()
            self._bt_toggle.set_state(enabled)
            self._bt_toggle.set_status_text("Enabled" if enabled else "Disabled")
            if enabled:
                self._refresh_bluetooth_devices()
            else:
                self._clear_bt_devices()
        except Exception:
            self._bt_toggle.set_status_text("Unavailable")

    def _on_bluetooth_toggled(self, is_on: bool) -> None:
        self._bt_toggle.set_status_text("Applying...")
        success, message = SystemControl.set_bluetooth_enabled(is_on)
        if not success:
            self._bt_toggle.set_status_text(f"Error: {message}")
            QTimer.singleShot(500, self._refresh_bluetooth_status)
            return
        QTimer.singleShot(2000, self._refresh_bluetooth_status)

    def _refresh_bluetooth_devices(self) -> None:
        self._clear_bt_devices()
        try:
            devices = SystemControl.get_bluetooth_devices()
            if devices:
                for dev in devices:
                    row = _DeviceRow(dev["name"], dev["status"])
                    self._bt_device_rows.append(row)
                    self._bt_devices_container.addWidget(row)
            else:
                self._bt_status_label.setText("No paired devices found.")
                self._bt_status_label.setVisible(True)
        except Exception:
            self._bt_status_label.setText("Could not scan devices.")
            self._bt_status_label.setVisible(True)

    def _clear_bt_devices(self) -> None:
        for row in self._bt_device_rows:
            row.setParent(None)
            row.deleteLater()
        self._bt_device_rows.clear()

    # ---- App settings
    setting_changed = Signal(str, str)

    def _on_startup_toggled(self, is_on: bool) -> None:
        success, msg = Config.set_startup_on_boot(is_on)
        self._startup_toggle.set_status_text(msg if success else f"Error: {msg}")

    def _on_window_mode_changed(self, mode: str) -> None:
        self._cfg["window_mode"] = mode
        Config.save()
        self.setting_changed.emit("window_mode", mode)

    def _on_update_toggled(self, is_on: bool) -> None:
        self._cfg["update_enabled"] = is_on
        self._update_toggle.set_status_text("Enabled" if is_on else "Disabled (for testing)")

    def _on_surround_toggled(self, is_on: bool) -> None:
        self._cfg["surround_sound"] = is_on
        from backend.spotify_control import SpotifyControl
        success, msg = SpotifyControl.enable_speaker_fill(is_on)
        self._surround_toggle.set_status_text(msg if success else f"Error: {msg}")

    # ---- Navigation
    def keyPressEvent(self, event: QKeyEvent) -> None:
        if event.key() == Qt.Key.Key_Escape:
            self.go_back.emit()
            return
        super().keyPressEvent(event)

    def showEvent(self, event) -> None:
        super().showEvent(event)
        self._load_initial_values()
