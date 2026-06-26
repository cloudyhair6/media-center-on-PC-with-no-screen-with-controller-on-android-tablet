"""Home screen with clock display and navigation cards."""

from datetime import datetime

from PySide6.QtCore import Qt, Signal, QTimer
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QSizePolicy,
)

from styles.theme import Colors, Sizes
from ui.components import FocusCard


class HomeScreen(QWidget):
    """Main home screen with live clock and navigation cards."""

    navigate_to = Signal(str)  # emits 'movies', 'settings', 'power'

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("HomeScreen")
        self._cards: list[FocusCard] = []
        self._setup_ui()
        self._setup_clock()
        self._setup_navigation()

    # ------------------------------------------------------------------ UI
    def _setup_ui(self) -> None:
        """Build the home screen layout."""
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(
            Sizes.SPACING_XL * 2, Sizes.SPACING_XL * 2,
            Sizes.SPACING_XL * 2, Sizes.SPACING_XL * 2,
        )
        main_layout.setSpacing(Sizes.SPACING)

        # ---------- top stretch to vertically center content
        main_layout.addStretch(2)

        # ---------- time display
        self._time_label = QLabel()
        self._time_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._time_label.setStyleSheet(
            f"font-size: {Sizes.FONT_HERO}px; "
            f"font-weight: 700; "
            f"color: {Colors.TEXT_PRIMARY}; "
            f"letter-spacing: 2px; "
            f"background: transparent;"
        )
        main_layout.addWidget(self._time_label)

        # ---------- date display
        self._date_label = QLabel()
        self._date_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._date_label.setStyleSheet(
            f"font-size: {Sizes.FONT_H3}px; "
            f"color: {Colors.TEXT_SECONDARY}; "
            f"margin-top: 4px; "
            f"background: transparent;"
        )
        main_layout.addWidget(self._date_label)

        # ---------- spacer
        main_layout.addSpacing(Sizes.SPACING_XL)

        # ---------- welcome text
        welcome = QLabel("What would you like to do?")
        welcome.setAlignment(Qt.AlignmentFlag.AlignCenter)
        welcome.setStyleSheet(
            f"font-size: {Sizes.FONT_H2}px; "
            f"font-weight: 500; "
            f"color: {Colors.TEXT_SECONDARY}; "
            f"background: transparent;"
        )
        main_layout.addWidget(welcome)

        # ---------- spacer before cards
        main_layout.addSpacing(Sizes.SPACING_LG)

        # ---------- cards row
        cards_layout = QHBoxLayout()
        cards_layout.setSpacing(Sizes.SPACING_LG)
        cards_layout.setContentsMargins(0, 0, 0, 0)

        # Push cards to center
        cards_layout.addStretch(1)

        card_data = [
            ("M", "Movies", "Browse & play your media", None, "movies"),
            ("♪", "Music", "Spotify playback", None, "music"),
            ("S", "Settings", "Adjust your system", None, "settings"),
            ("P", "Power", "Shutdown, restart, sleep", Colors.DANGER, "power"),
        ]

        for icon, title, subtitle, color, target in card_data:
            card = FocusCard(
                icon_char=icon,
                title=title,
                subtitle=subtitle,
                color=color,
                parent=self,
            )
            card.setMinimumSize(240, 190)
            card.setMaximumSize(300, 210)
            card.setSizePolicy(
                QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Fixed,
            )
            card.setFocusPolicy(Qt.FocusPolicy.StrongFocus)

            # Capture *target* by value in the lambda default
            card.clicked.connect(lambda t=target: self.navigate_to.emit(t))
            cards_layout.addWidget(card)
            self._cards.append(card)

        cards_layout.addStretch(1)
        main_layout.addLayout(cards_layout)

        # ---------- IP display at bottom
        import socket
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
        except Exception:
            ip = "127.0.0.1"

        ip_label = QLabel(f"PC IP Address: {ip}")
        ip_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignBottom)
        ip_label.setStyleSheet(
            f"font-size: {Sizes.FONT_BODY}px; "
            f"color: {Colors.TEXT_MUTED}; "
            f"background: transparent;"
        )
        main_layout.addWidget(ip_label)

        # ---------- bottom stretch
        main_layout.addStretch(1)

    # -------------------------------------------------------------- Clock
    def _setup_clock(self) -> None:
        """Start the clock timer and perform the initial update."""
        self._clock_timer = QTimer(self)
        self._clock_timer.setInterval(1000)
        self._clock_timer.timeout.connect(self._update_clock)
        self._clock_timer.start()
        self._update_clock()

    def _update_clock(self) -> None:
        """Refresh the time and date labels."""
        now = datetime.now()
        self._time_label.setText(now.strftime("%#I:%M %p"))
        self._date_label.setText(now.strftime("%A, %B %d, %Y"))

    # --------------------------------------------------------- Navigation
    def _setup_navigation(self) -> None:
        """Set up keyboard tab order between cards."""
        for i in range(len(self._cards) - 1):
            self.setTabOrder(self._cards[i], self._cards[i + 1])

        # Give initial focus to the first card
        if self._cards:
            self._cards[0].setFocus()

    # ------------------------------------------------------ Key handling
    def keyPressEvent(self, event) -> None:  # noqa: N802
        """Arrow‑key navigation between cards."""
        key = event.key()
        focused = self.focusWidget()

        if focused in self._cards:
            idx = self._cards.index(focused)
            if key == Qt.Key.Key_Left and idx > 0:
                self._cards[idx - 1].setFocus()
                return
            if key == Qt.Key.Key_Right and idx < len(self._cards) - 1:
                self._cards[idx + 1].setFocus()
                return

        super().keyPressEvent(event)

    def showEvent(self, event) -> None:  # noqa: N802
        """Give focus to the first card whenever the screen is shown."""
        super().showEvent(event)
        if self._cards:
            self._cards[0].setFocus()
