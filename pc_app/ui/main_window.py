"""MiniPC v2.0 — Passive display showing time and IP."""
from __future__ import annotations
import json
from pathlib import Path
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QKeyEvent
from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QLabel, QApplication, QHBoxLayout, QProgressBar
)
from styles.theme import Colors, Sizes
from backend.remote_server import RemoteServer, RemoteCommandDispatcher, get_local_ip
from backend.system_control import SystemControl
from styles.theme import get_active_colors


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle('MiniPC')
        self.setObjectName('MainWindow')
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint | Qt.WindowType.WindowStaysOnTopHint
        )
        self.setStyleSheet(f'QMainWindow#MainWindow {{ background-color: {get_active_colors().BG_PRIMARY}; }}')
        self._last_config_mtime = 0
        
        self._setup_ui()
        self._setup_remote_server()
        self._auto_launch_spotify()
        self.showFullScreen()
    
    def _setup_ui(self):
        central = QWidget()
        central.setStyleSheet('background: transparent;')
        self.setCentralWidget(central)
        
        layout = QVBoxLayout(central)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setSpacing(20)
        
        # Spacer
        layout.addStretch(3)
        
        # Clock
        self._clock = QLabel('00:00')
        self._clock.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._clock.setStyleSheet(
            f'color: {Colors.TEXT_PRIMARY}; font-size: 120px; font-weight: 300; '
            f'font-family: "Segoe UI", Arial, sans-serif; background: transparent;'
        )
        layout.addWidget(self._clock)
        
        # Date
        self._date = QLabel('')
        self._date.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._date.setStyleSheet(
            f'color: {Colors.TEXT_SECONDARY}; font-size: {Sizes.FONT_H2}px; '
            f'background: transparent; margin-bottom: 40px;'
        )
        layout.addWidget(self._date)
        
        # IP Address
        self._ip_label = QLabel(get_local_ip())
        self._ip_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._ip_label.setStyleSheet(
            f'color: {Colors.SUCCESS}; font-size: 64px; font-weight: bold; '
            f'font-family: "Courier New", monospace; background: transparent;'
        )
        layout.addWidget(self._ip_label)
        
        # Port
        self._port_label = QLabel('Port: 8080')
        self._port_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._port_label.setStyleSheet(
            f'color: {Colors.TEXT_MUTED}; font-size: {Sizes.FONT_BODY}px; '
            f'background: transparent;'
        )
        layout.addWidget(self._port_label)
        
        layout.addStretch(4)
        
        # Volume OSD (overlay)
        self._volume_osd = QWidget(central)
        self._volume_osd.setFixedSize(320, 80)
        self._volume_osd.setStyleSheet(
            f'QWidget {{ background: rgba(0,0,0,0.75); border-radius: {Sizes.CARD_RADIUS}px; }}'
        )
        osd_layout = QHBoxLayout(self._volume_osd)
        osd_layout.setContentsMargins(20, 10, 20, 10)
        osd_layout.setSpacing(15)
        
        self._volume_label = QLabel('VOL')
        self._volume_label.setStyleSheet(
            f'color: {Colors.TEXT_PRIMARY}; font-size: {Sizes.FONT_H1}px; font-weight: bold; background: transparent;'
        )
        osd_layout.addWidget(self._volume_label)
        
        self._volume_bar = QProgressBar()
        self._volume_bar.setRange(0, 100)
        self._volume_bar.setTextVisible(False)
        self._volume_bar.setFixedHeight(20)
        self._volume_bar.setStyleSheet(
            f'''
            QProgressBar {{
                background-color: rgba(255, 255, 255, 0.2);
                border-radius: 10px;
            }}
            QProgressBar::chunk {{
                background-color: {Colors.SUCCESS};
                border-radius: 10px;
            }}
            '''
        )
        osd_layout.addWidget(self._volume_bar)
        
        self._volume_osd.setVisible(False)
        
        self._osd_timer = QTimer(self)
        self._osd_timer.setSingleShot(True)
        self._osd_timer.setInterval(1500)
        self._osd_timer.timeout.connect(lambda: self._volume_osd.setVisible(False))
        
        # Clock update timer
        self._clock_timer = QTimer(self)
        self._clock_timer.setInterval(1000)
        self._clock_timer.timeout.connect(self._update_clock)
        self._clock_timer.timeout.connect(self._check_config)
        self._clock_timer.start()

    def _check_config(self):
        try:
            import os
            config_path = Path(__file__).resolve().parent.parent / 'config.json'
            if config_path.exists():
                mtime = os.path.getmtime(config_path)
                if mtime > self._last_config_mtime:
                    self._last_config_mtime = mtime
                    self._apply_theme()
        except Exception:
            pass

    def _apply_theme(self):
        colors = get_active_colors()
        self.setStyleSheet(f'QMainWindow#MainWindow {{ background-color: {colors.BG_PRIMARY}; }}')
        self._clock.setStyleSheet(
            f'color: {colors.TEXT_PRIMARY}; font-size: 120px; font-weight: 300; '
            f'font-family: "Segoe UI", Arial, sans-serif; background: transparent;'
        )
        self._date.setStyleSheet(
            f'color: {colors.TEXT_SECONDARY}; font-size: {Sizes.FONT_H2}px; '
            f'background: transparent; margin-bottom: 40px;'
        )
        self._ip_label.setStyleSheet(
            f'color: {colors.SUCCESS}; font-size: 64px; font-weight: bold; '
            f'font-family: "Courier New", monospace; background: transparent;'
        )
        self._port_label.setStyleSheet(
            f'color: {colors.TEXT_MUTED}; font-size: {Sizes.FONT_BODY}px; '
            f'background: transparent;'
        )
        self._volume_osd.setStyleSheet(
            f'QWidget {{ background: rgba(0,0,0,0.75); border-radius: {Sizes.CARD_RADIUS}px; }}'
        )
        self._volume_label.setStyleSheet(
            f'color: {colors.TEXT_PRIMARY}; font-size: {Sizes.FONT_H1}px; font-weight: bold; background: transparent;'
        )
        self._update_clock()
    
    def _update_clock(self):
        from datetime import datetime
        now = datetime.now()
        self._clock.setText(now.strftime('%H:%M'))
        self._date.setText(now.strftime('%A, %B %d, %Y'))
    
    def _show_volume_osd(self, vol):
        self._volume_label.setText(f'{vol}%')
        self._volume_bar.setValue(vol)
        cw = self.centralWidget()
        if cw:
            x = (cw.width() - self._volume_osd.width()) // 2
            self._volume_osd.move(x, 40)
        self._volume_osd.setVisible(True)
        self._volume_osd.raise_()
        self._osd_timer.start()
    
    def _setup_remote_server(self):
        self._remote_dispatcher = RemoteCommandDispatcher()
        self._remote_dispatcher.command_received.connect(self._handle_remote_command)
        self._remote_server = RemoteServer(self._remote_dispatcher, port=8080)
        try:
            url = self._remote_server.start()
            print(f'[MiniPC] Remote: {url}')
            try:
                from backend.startup_announcer import play_ready_sequence
                play_ready_sequence(get_local_ip())
            except Exception as e:
                print(f"[MiniPC] Failed to play ready sequence: {e}")
        except Exception as e:
            print(f'[MiniPC] Remote server failed: {e}')
    
    def _auto_launch_spotify(self):
        try:
            config_path = Path(__file__).resolve().parent.parent / 'config.json'
            if config_path.exists():
                cfg = json.loads(config_path.read_text())
                if cfg.get('spotify_auto_start', True):
                    from backend.spotify_control import SpotifyControl
                    if not SpotifyControl.is_running():
                        SpotifyControl.launch()
                        print('[MiniPC] Spotify auto-launched')
        except Exception as e:
            print(f'[MiniPC] Spotify auto-launch failed: {e}')
    
    def _handle_remote_command(self, command):
        if command == 'volume_up':
            vol = min(100, SystemControl.get_volume() + 5)
            SystemControl.set_volume(vol)
            self._show_volume_osd(vol)
        elif command == 'volume_down':
            vol = max(0, SystemControl.get_volume() - 5)
            SystemControl.set_volume(vol)
            self._show_volume_osd(vol)
        elif command == 'volume_update':
            vol = SystemControl.get_volume()
            self._show_volume_osd(vol)
        elif command == 'mute':
            SystemControl.set_volume(0)
            self._show_volume_osd(0)
        elif command == 'spotify_play_pause':
            from backend.spotify_control import SpotifyControl
            SpotifyControl.play_pause()
        elif command == 'spotify_next':
            from backend.spotify_control import SpotifyControl
            SpotifyControl.next_track()
        elif command == 'spotify_prev':
            from backend.spotify_control import SpotifyControl
            SpotifyControl.prev_track()
        elif command == 'spotify_launch':
            from backend.spotify_control import SpotifyControl
            SpotifyControl.launch()
    
    def keyPressEvent(self, event):
        key = event.key()
        if key == Qt.Key.Key_F11:
            if self.isFullScreen():
                self.showNormal()
            else:
                self.showFullScreen()
            return
        if key == Qt.Key.Key_Q and event.modifiers() & Qt.KeyboardModifier.ControlModifier:
            self.close()
            return
        super().keyPressEvent(event)
