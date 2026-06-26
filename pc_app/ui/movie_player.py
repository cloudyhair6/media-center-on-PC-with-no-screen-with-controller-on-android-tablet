"""Full-featured movie player with auto-hiding overlay controls."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal, QTimer, QUrl
from PySide6.QtGui import QKeyEvent, QMouseEvent
from PySide6.QtMultimedia import QMediaPlayer, QAudioOutput
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QSlider, QFrame, QSizePolicy,
)

from styles.theme import Colors, Sizes


def _fmt_time(ms: int) -> str:
    """Format milliseconds as HH:MM:SS."""
    total_s = max(0, ms // 1000)
    h, rem = divmod(total_s, 3600)
    m, s = divmod(rem, 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


class MoviePlayer(QWidget):
    """Video player with overlay transport controls.

    Controls bar is always visible initially and on any user input.
    Auto-hides after 3 seconds during playback.
    """

    go_back = Signal()

    _HIDE_DELAY_MS = 4000
    _SEEK_STEP_MS = 10_000
    _VOL_STEP = 5

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("MoviePlayer")
        self.setMouseTracking(True)
        self._setup_media()
        self._setup_ui()
        self._setup_hide_timer()

    # --------------------------------------------------------- Media core
    def _setup_media(self) -> None:
        self._audio = QAudioOutput(self)
        self._audio.setVolume(0.75)

        self._player = QMediaPlayer(self)
        self._player.setAudioOutput(self._audio)

        self._player.playbackStateChanged.connect(self._on_state_changed)
        self._player.positionChanged.connect(self._on_position_changed)
        self._player.durationChanged.connect(self._on_duration_changed)
        self._player.errorOccurred.connect(self._on_error)

    # ------------------------------------------------------------------ UI
    def _setup_ui(self) -> None:
        # Main vertical layout: video on top, controls bar at bottom
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # --- Video widget
        self._video_widget = QVideoWidget()
        self._video_widget.setMouseTracking(True)
        self._player.setVideoOutput(self._video_widget)
        self._video_widget.setSizePolicy(
            QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding,
        )
        root.addWidget(self._video_widget, 1)

        # --- Error label (overlaid on video area, hidden by default)
        self._error_label = QLabel(self._video_widget)
        self._error_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._error_label.setStyleSheet(
            f"font-size: {Sizes.FONT_H3}px; "
            f"color: {Colors.DANGER}; "
            f"background: {Colors.OVERLAY}; "
            f"padding: 24px; "
            f"border-radius: {Sizes.CARD_RADIUS}px;"
        )
        self._error_label.setVisible(False)
        self._error_label.setWordWrap(True)

        # --- Controls bar (fixed at bottom, always starts visible)
        self._controls_bar = QFrame()
        self._controls_bar.setFixedHeight(70)
        self._controls_bar.setStyleSheet(
            f"QFrame {{ "
            f"  background: rgba(0, 0, 0, 0.85); "
            f"  border-top: 1px solid rgba(255,255,255,0.08); "
            f"}}"
        )
        bar_layout = QHBoxLayout(self._controls_bar)
        bar_layout.setContentsMargins(
            Sizes.SPACING_LG, Sizes.SPACING_SM,
            Sizes.SPACING_LG, Sizes.SPACING_SM,
        )
        bar_layout.setSpacing(Sizes.SPACING)

        # Back button
        self._back_btn = QPushButton("◀")
        self._back_btn.setFixedSize(52, 52)
        self._back_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self._back_btn.setStyleSheet(self._btn_style(Colors.BG_TERTIARY))
        self._back_btn.clicked.connect(lambda: (self.stop(), self.go_back.emit()))
        bar_layout.addWidget(self._back_btn)

        # Play/pause button
        self._play_btn = QPushButton("▶")
        self._play_btn.setFixedSize(52, 52)
        self._play_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self._play_btn.setStyleSheet(self._btn_style(Colors.ACCENT))
        self._play_btn.clicked.connect(self._toggle_play)
        bar_layout.addWidget(self._play_btn)

        # Current time
        self._cur_time = QLabel("00:00:00")
        self._cur_time.setStyleSheet(
            f"color: {Colors.TEXT_SECONDARY}; "
            f"font-size: {Sizes.FONT_SMALL}px; "
            f"background: transparent;"
        )
        bar_layout.addWidget(self._cur_time)

        # Seek slider
        self._seek_slider = QSlider(Qt.Orientation.Horizontal)
        self._seek_slider.setRange(0, 0)
        self._seek_slider.setStyleSheet(self._slider_style())
        self._seek_slider.sliderMoved.connect(self._player.setPosition)
        bar_layout.addWidget(self._seek_slider, 1)

        # Duration label
        self._dur_time = QLabel("00:00:00")
        self._dur_time.setStyleSheet(
            f"color: {Colors.TEXT_SECONDARY}; "
            f"font-size: {Sizes.FONT_SMALL}px; "
            f"background: transparent;"
        )
        bar_layout.addWidget(self._dur_time)

        # Volume label
        vol_label = QLabel("VOL")
        vol_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; "
            f"color: {Colors.TEXT_MUTED}; "
            f"background: transparent; "
            f"font-weight: bold;"
        )
        bar_layout.addWidget(vol_label)

        # Volume slider
        self._vol_slider = QSlider(Qt.Orientation.Horizontal)
        self._vol_slider.setRange(0, 100)
        self._vol_slider.setValue(75)
        self._vol_slider.setFixedWidth(120)
        self._vol_slider.setStyleSheet(self._slider_style())
        self._vol_slider.valueChanged.connect(
            lambda v: self._audio.setVolume(v / 100.0),
        )
        bar_layout.addWidget(self._vol_slider)

        root.addWidget(self._controls_bar)

    # --------------------------------------------------------- Timer
    def _setup_hide_timer(self) -> None:
        self._hide_timer = QTimer(self)
        self._hide_timer.setSingleShot(True)
        self._hide_timer.setInterval(self._HIDE_DELAY_MS)
        self._hide_timer.timeout.connect(self._hide_controls)

    def _show_controls(self) -> None:
        self._controls_bar.setVisible(True)
        self._hide_timer.start()

    def _hide_controls(self) -> None:
        if self._player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self._controls_bar.setVisible(False)

    # --------------------------------------------------------- Styles
    @staticmethod
    def _btn_style(bg_color: str) -> str:
        return (
            f"QPushButton {{ "
            f"  background: {bg_color}; "
            f"  color: {Colors.TEXT_PRIMARY}; "
            f"  border: none; "
            f"  border-radius: 22px; "
            f"  font-size: 20px; "
            f"  font-weight: bold; "
            f"}} "
            f"QPushButton:hover {{ opacity: 0.8; }}"
        )

    @staticmethod
    def _slider_style() -> str:
        return (
            f"QSlider::groove:horizontal {{ "
            f"  height: 8px; "
            f"  background: {Colors.SLIDER_GROOVE}; "
            f"  border-radius: 4px; "
            f"}} "
            f"QSlider::handle:horizontal {{ "
            f"  width: 18px; height: 18px; "
            f"  margin: -5px 0; "
            f"  background: {Colors.ACCENT}; "
            f"  border-radius: 9px; "
            f"}} "
            f"QSlider::sub-page:horizontal {{ "
            f"  background: {Colors.ACCENT}; "
            f"  border-radius: 4px; "
            f"}}"
        )

    # ------------------------------------------------------- Public API
    def play(self, file_path: str) -> None:
        """Start playing a video file."""
        self._error_label.setVisible(False)
        self._player.setSource(QUrl.fromLocalFile(file_path))
        self._player.play()
        self._controls_bar.setVisible(True)
        self._show_controls()

    def stop(self) -> None:
        """Stop playback and reset."""
        self._hide_timer.stop()
        self._player.stop()
        self._player.setSource(QUrl())
        self._controls_bar.setVisible(True)

    # -------------------------------------------------------- Callbacks
    def _toggle_play(self) -> None:
        state = self._player.playbackState()
        if state == QMediaPlayer.PlaybackState.PlayingState:
            self._player.pause()
        else:
            self._player.play()

    def _on_state_changed(self, state: QMediaPlayer.PlaybackState) -> None:
        if state == QMediaPlayer.PlaybackState.PlayingState:
            self._play_btn.setText("⏸")
        else:
            self._play_btn.setText("▶")
            self._controls_bar.setVisible(True)
            self._hide_timer.stop()

    def _on_position_changed(self, pos: int) -> None:
        self._seek_slider.blockSignals(True)
        self._seek_slider.setValue(pos)
        self._seek_slider.blockSignals(False)
        self._cur_time.setText(_fmt_time(pos))

    def _on_duration_changed(self, dur: int) -> None:
        self._seek_slider.setRange(0, dur)
        self._dur_time.setText(_fmt_time(dur))

    def _on_error(self, error, msg: str = "") -> None:
        text = msg or "An error occurred during playback."
        self._error_label.setText(f"Error: {text}\n\nPress Escape to go back.")
        self._error_label.setVisible(True)
        self._error_label.resize(self._video_widget.size())

    # ---------------------------------------------------- Input events
    def mouseMoveEvent(self, event: QMouseEvent) -> None:
        self._show_controls()
        super().mouseMoveEvent(event)

    def mousePressEvent(self, event: QMouseEvent) -> None:
        self._show_controls()
        super().mousePressEvent(event)

    def keyPressEvent(self, event: QKeyEvent) -> None:
        key = event.key()
        self._show_controls()

        if key == Qt.Key.Key_Space:
            self._toggle_play()
        elif key == Qt.Key.Key_Escape:
            self.stop()
            self.go_back.emit()
        elif key == Qt.Key.Key_Left:
            pos = max(0, self._player.position() - self._SEEK_STEP_MS)
            self._player.setPosition(pos)
        elif key == Qt.Key.Key_Right:
            pos = min(
                self._player.duration(),
                self._player.position() + self._SEEK_STEP_MS,
            )
            self._player.setPosition(pos)
        elif key == Qt.Key.Key_Up:
            vol = min(100, self._vol_slider.value() + self._VOL_STEP)
            self._vol_slider.setValue(vol)
        elif key == Qt.Key.Key_Down:
            vol = max(0, self._vol_slider.value() - self._VOL_STEP)
            self._vol_slider.setValue(vol)
        else:
            super().keyPressEvent(event)
