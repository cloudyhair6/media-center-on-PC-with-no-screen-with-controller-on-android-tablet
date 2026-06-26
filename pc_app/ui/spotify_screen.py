"""Spotify music player screen with large album art and playback controls."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal, QTimer
from PySide6.QtGui import QKeyEvent, QFont
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QSizePolicy, QFrame,
)

from styles.theme import Colors, Sizes
from backend.spotify_control import SpotifyControl


class SpotifyScreen(QWidget):
    """Full-screen Spotify player with large visuals for projector display."""

    go_back = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("SpotifyScreen")
        self._setup_ui()

        # Poll track info every 2 seconds
        self._poll_timer = QTimer(self)
        self._poll_timer.setInterval(2000)
        self._poll_timer.timeout.connect(self._refresh_now_playing)

    # ------------------------------------------------------------------ UI
    def _setup_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(
            Sizes.SPACING_XL, Sizes.SPACING_LG,
            Sizes.SPACING_XL, Sizes.SPACING_LG,
        )
        root.setSpacing(Sizes.SPACING)

        # Header
        header = QHBoxLayout()
        header.setSpacing(Sizes.SPACING)

        back_btn = QPushButton("◀ Back")
        back_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        back_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        back_btn.setStyleSheet(self._btn_flat_style())
        back_btn.clicked.connect(self.go_back.emit)
        header.addWidget(back_btn)

        title = QLabel("Music")
        title.setStyleSheet(
            f"font-size: {Sizes.FONT_H1}px; font-weight: 700; "
            f"color: {Colors.TEXT_PRIMARY}; background: transparent;"
        )
        header.addWidget(title)
        header.addStretch()

        # Spotify status
        self._status_label = QLabel("Not running")
        self._status_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_MUTED}; "
            f"background: transparent;"
        )
        header.addWidget(self._status_label)

        root.addLayout(header)
        root.addStretch(1)

        # ---- Now Playing area (centered, big)
        now_playing = QVBoxLayout()
        now_playing.setAlignment(Qt.AlignmentFlag.AlignCenter)
        now_playing.setSpacing(Sizes.SPACING_LG)

        # Music icon / album art placeholder
        self._art_label = QLabel()
        self._art_label.setFixedSize(280, 280)
        self._art_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._art_label.setStyleSheet(
            f"background: {Colors.BG_TERTIARY}; "
            f"border: 2px solid {Colors.BORDER}; "
            f"border-radius: {Sizes.CARD_RADIUS}px; "
            f"font-size: 120px; color: {Colors.ACCENT};"
        )
        self._art_label.setText("♪")
        now_playing.addWidget(self._art_label, 0, Qt.AlignmentFlag.AlignCenter)

        # Song title
        self._title_label = QLabel("No track playing")
        self._title_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._title_label.setWordWrap(True)
        self._title_label.setStyleSheet(
            f"font-size: {Sizes.FONT_H1}px; font-weight: 700; "
            f"color: {Colors.TEXT_PRIMARY}; background: transparent;"
        )
        now_playing.addWidget(self._title_label)

        # Artist
        self._artist_label = QLabel("")
        self._artist_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._artist_label.setStyleSheet(
            f"font-size: {Sizes.FONT_H2}px; font-weight: 400; "
            f"color: {Colors.TEXT_SECONDARY}; background: transparent;"
        )
        now_playing.addWidget(self._artist_label)

        root.addLayout(now_playing)
        root.addStretch(1)

        # ---- Playback controls
        controls = QHBoxLayout()
        controls.setSpacing(Sizes.SPACING_XL)
        controls.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self._prev_btn = self._make_control_btn("⏮", "Previous")
        self._prev_btn.clicked.connect(SpotifyControl.prev_track)
        controls.addWidget(self._prev_btn)

        self._play_btn = self._make_control_btn("▶", "Play/Pause", accent=True)
        self._play_btn.clicked.connect(self._on_play_pause)
        controls.addWidget(self._play_btn)

        self._next_btn = self._make_control_btn("⏭", "Next")
        self._next_btn.clicked.connect(SpotifyControl.next_track)
        controls.addWidget(self._next_btn)

        root.addLayout(controls)

        # ---- Launch Spotify button (shown when not running)
        self._launch_frame = QFrame()
        self._launch_frame.setStyleSheet("background: transparent;")
        launch_layout = QHBoxLayout(self._launch_frame)
        launch_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        launch_btn = QPushButton("Launch Spotify")
        launch_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        launch_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        launch_btn.setStyleSheet(
            f"QPushButton {{ "
            f"  background: #1DB954; color: white; "
            f"  border: none; border-radius: {Sizes.BUTTON_RADIUS}px; "
            f"  padding: 14px 40px; font-size: {Sizes.FONT_BODY}px; "
            f"  font-weight: 600; "
            f"}} "
            f"QPushButton:hover {{ background: #1ed760; }} "
            f"QPushButton:focus {{ border: 2px solid {Colors.ACCENT}; }}"
        )
        launch_btn.clicked.connect(self._launch_spotify)
        launch_layout.addWidget(launch_btn)
        root.addWidget(self._launch_frame)

        root.addStretch(1)

        # Tab order
        self.setTabOrder(self._prev_btn, self._play_btn)
        self.setTabOrder(self._play_btn, self._next_btn)

    def _make_control_btn(self, text: str, tooltip: str, accent: bool = False) -> QPushButton:
        btn = QPushButton(text)
        btn.setFixedSize(80, 80)
        btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        btn.setCursor(Qt.CursorShape.PointingHandCursor)
        btn.setToolTip(tooltip)
        if accent:
            btn.setStyleSheet(
                f"QPushButton {{ "
                f"  background: {Colors.ACCENT}; color: {Colors.BG_PRIMARY}; "
                f"  border: none; border-radius: 40px; "
                f"  font-size: 24px; font-weight: bold; "
                f"}} "
                f"QPushButton:hover {{ background: #33ddff; }} "
                f"QPushButton:focus {{ border: 3px solid {Colors.TEXT_PRIMARY}; }}"
            )
        else:
            btn.setStyleSheet(
                f"QPushButton {{ "
                f"  background: {Colors.BG_TERTIARY}; color: {Colors.TEXT_PRIMARY}; "
                f"  border: 2px solid {Colors.BORDER}; border-radius: 40px; "
                f"  font-size: 22px; font-weight: bold; "
                f"}} "
                f"QPushButton:hover {{ background: {Colors.BG_CARD_HOVER}; border-color: {Colors.ACCENT}; }} "
                f"QPushButton:focus {{ border: 2px solid {Colors.ACCENT}; }}"
            )
        return btn

    @staticmethod
    def _btn_flat_style() -> str:
        return (
            f"QPushButton {{ "
            f"  background: {Colors.BG_TERTIARY}; color: {Colors.TEXT_PRIMARY}; "
            f"  border: none; border-radius: {Sizes.BUTTON_RADIUS}px; "
            f"  padding: 10px 20px; font-size: {Sizes.FONT_BODY}px; "
            f"}} "
            f"QPushButton:hover {{ background: {Colors.BG_CARD_HOVER}; }} "
            f"QPushButton:focus {{ border: 2px solid {Colors.ACCENT}; }}"
        )

    # --------------------------------------------------------- Actions
    def _on_play_pause(self) -> None:
        SpotifyControl.play_pause()
        # Update after a short delay
        QTimer.singleShot(500, self._refresh_now_playing)

    def _launch_spotify(self) -> None:
        self._status_label.setText("Launching...")
        if SpotifyControl.launch():
            self._launched_spotify = True
            QTimer.singleShot(3000, self._refresh_now_playing)
        else:
            self._status_label.setText("Could not find Spotify")

    def _refresh_now_playing(self) -> None:
        if not SpotifyControl.is_running():
            self._title_label.setText("Spotify not running")
            self._artist_label.setText("")
            self._status_label.setText("Not running")
            self._launch_frame.setVisible(True)
            self._art_label.setText("♪")
            return

        # Restore focus to our app if we just launched Spotify and it's now running
        if getattr(self, "_launched_spotify", False):
            self._launched_spotify = False
            self.window().raise_()
            self.window().activateWindow()
            self._play_btn.setFocus()

        self._launch_frame.setVisible(False)
        info = SpotifyControl.get_now_playing()

        if info.get("playing"):
            self._title_label.setText(info.get("title", ""))
            self._artist_label.setText(info.get("artist", ""))
            self._status_label.setText("Playing")
            self._play_btn.setText("⏸")
            self._art_label.setText("♪")
            self._art_label.setStyleSheet(
                f"background: {Colors.BG_TERTIARY}; "
                f"border: 2px solid {Colors.ACCENT}; "
                f"border-radius: {Sizes.CARD_RADIUS}px; "
                f"font-size: 120px; color: {Colors.ACCENT};"
            )
        else:
            title = info.get("title", "Spotify")
            if title and title.lower() not in ("spotify", "spotify free", "spotify premium"):
                self._title_label.setText(title)
            else:
                self._title_label.setText("Paused")
            self._artist_label.setText(info.get("artist", ""))
            self._status_label.setText("Paused")
            self._play_btn.setText("▶")

    # --------------------------------------------------- Lifecycle
    def showEvent(self, event) -> None:
        super().showEvent(event)
        self._refresh_now_playing()
        self._poll_timer.start()
        if self._play_btn:
            self._play_btn.setFocus()

    def hideEvent(self, event) -> None:
        super().hideEvent(event)
        self._poll_timer.stop()

    def keyPressEvent(self, event: QKeyEvent) -> None:
        key = event.key()
        if key == Qt.Key.Key_Escape:
            self.go_back.emit()
        elif key in (Qt.Key.Key_Space, Qt.Key.Key_Return, Qt.Key.Key_Enter):
            if SpotifyControl.is_running():
                self._on_play_pause()
            else:
                self._launch_spotify()
        elif key == Qt.Key.Key_Right:
            SpotifyControl.next_track()
            QTimer.singleShot(500, self._refresh_now_playing)
        elif key == Qt.Key.Key_Left:
            SpotifyControl.prev_track()
            QTimer.singleShot(500, self._refresh_now_playing)
        else:
            super().keyPressEvent(event)
