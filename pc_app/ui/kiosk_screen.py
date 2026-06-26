"""Tablet Kiosk Manager screen."""

from __future__ import annotations

import socket

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QFrame
)

from styles.theme import Colors, Sizes


class KioskScreen(QWidget):
    """Screen for displaying the PC IP to the tablet."""

    go_back = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("KioskScreen")
        self._setup_ui()

    def _get_local_ip(self) -> str:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            try:
                return socket.gethostbyname(socket.gethostname())
            except Exception:
                return "127.0.0.1"

    def _setup_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(
            Sizes.SPACING_LG, Sizes.SPACING_LG,
            Sizes.SPACING_LG, Sizes.SPACING_LG,
        )
        root.setSpacing(Sizes.SPACING)

        # Header
        header = QHBoxLayout()
        header.setSpacing(Sizes.SPACING)

        back_btn = QPushButton("<  Back")
        back_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        back_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        back_btn.clicked.connect(self.go_back.emit)
        header.addWidget(back_btn)

        title = QLabel("Tablet Remote Setup")
        title.setStyleSheet(
            f"font-size: {Sizes.FONT_H1}px; "
            f"font-weight: 700; "
            f"color: {Colors.TEXT_PRIMARY}; "
        )
        header.addWidget(title)
        header.addStretch()
        root.addLayout(header)

        root.addStretch(1)
        
        info_label = QLabel("Enter this IP address into your Tablet app to control this PC:")
        info_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        info_label.setStyleSheet(f"color: {Colors.TEXT_SECONDARY}; font-size: {Sizes.FONT_H2}px;")
        root.addWidget(info_label)

        # IP Display
        ip_frame = QFrame()
        ip_frame.setStyleSheet(
            f"background: {Colors.BG_CARD_SOLID}; "
            f"border: 2px solid {Colors.BORDER}; "
            f"border-radius: {Sizes.CARD_RADIUS}px; "
        )
        ip_layout = QVBoxLayout(ip_frame)
        ip_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ip_layout.setContentsMargins(0, 60, 0, 60)
        
        self.ip_label = QLabel(self._get_local_ip())
        self.ip_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.ip_label.setStyleSheet(
            f"color: {Colors.SUCCESS}; "
            f"font-size: 100px; "
            f"font-weight: bold; "
            f"font-family: 'Courier New', monospace;"
        )
        ip_layout.addWidget(self.ip_label)
        
        root.addWidget(ip_frame)
        
        root.addStretch(1)
        
        # Force Unlock button
        self.unlock_btn = QPushButton("Unlock Tablet")
        self.unlock_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.unlock_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        self.unlock_btn.setStyleSheet(
            f"background: {Colors.DANGER};"
            f"color: white;"
            f"font-size: {Sizes.FONT_H3}px;"
            f"font-weight: bold;"
            f"padding: 20px;"
            f"border-radius: {Sizes.BUTTON_RADIUS}px;"
        )
        self.unlock_btn.clicked.connect(self._force_unlock)
        root.addWidget(self.unlock_btn)

        root.addStretch(2)

    def _force_unlock(self) -> None:
        from backend.remote_server import RemoteServer
        RemoteServer.force_unlock = True
