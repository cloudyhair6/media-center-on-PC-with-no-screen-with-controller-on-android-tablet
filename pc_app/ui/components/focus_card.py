"""
FocusCard - A modern glass-style card widget with focus/hover animations.

Displays an emoji icon, title, and optional subtitle in a rounded card.
Supports keyboard navigation and emits a clicked signal on activation.
"""

from PySide6.QtCore import (
    Qt,
    Signal,
    QPropertyAnimation,
    QEasingCurve,
    Property,
    QRect,
)
from PySide6.QtGui import QColor, QKeyEvent, QMouseEvent
from PySide6.QtWidgets import (
    QFrame,
    QGraphicsDropShadowEffect,
    QLabel,
    QVBoxLayout,
)

from styles.theme import Colors, Sizes


class FocusCard(QFrame):
    """A premium glass-style card with icon, title, and subtitle.

    Features smooth hover/focus animations, keyboard support, and a glow
    effect when focused.

    Signals:
        clicked: Emitted when the card is clicked or activated via Enter/Return.
    """

    clicked = Signal()

    def __init__(
        self,
        icon_char: str,
        title: str,
        subtitle: str = "",
        color: str = None,
        parent=None,
    ) -> None:
        super().__init__(parent)
        self._icon_char = icon_char
        self._title = title
        self._subtitle = subtitle
        self._color = color or Colors.ACCENT
        self._is_hovered = False
        self._is_focused = False

        # Internal animated property for background opacity blending
        self._highlight_strength: float = 0.0

        self.setFocusPolicy(Qt.StrongFocus)
        self.setMinimumSize(200, 180)
        self.setCursor(Qt.PointingHandCursor)
        self.setObjectName("FocusCard")

        self._setup_ui()
        self._setup_shadow()
        self._setup_animations()
        self._apply_default_style()

    # ------------------------------------------------------------------
    # UI Setup
    # ------------------------------------------------------------------

    def _setup_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(
            Sizes.SPACING, Sizes.SPACING_LG, Sizes.SPACING, Sizes.SPACING
        )
        layout.setSpacing(Sizes.SPACING_SM)
        layout.setAlignment(Qt.AlignCenter)

        # Icon label
        self._icon_label = QLabel(self._icon_char)
        self._icon_label.setAlignment(Qt.AlignCenter)
        self._icon_label.setStyleSheet("font-size: 48px; background: transparent; border: none;")
        self._icon_label.setAttribute(Qt.WA_TransparentForMouseEvents)

        # Title label
        self._title_label = QLabel(self._title)
        self._title_label.setAlignment(Qt.AlignCenter)
        self._title_label.setWordWrap(True)
        self._title_label.setStyleSheet(
            f"font-size: {Sizes.FONT_H3}px; font-weight: bold; "
            f"color: {Colors.TEXT_PRIMARY}; background: transparent; border: none;"
        )
        self._title_label.setAttribute(Qt.WA_TransparentForMouseEvents)

        layout.addStretch()
        layout.addWidget(self._icon_label)
        layout.addWidget(self._title_label)

        # Subtitle label (optional)
        self._subtitle_label = QLabel(self._subtitle)
        self._subtitle_label.setAlignment(Qt.AlignCenter)
        self._subtitle_label.setWordWrap(True)
        self._subtitle_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_SECONDARY}; "
            f"background: transparent; border: none;"
        )
        self._subtitle_label.setAttribute(Qt.WA_TransparentForMouseEvents)
        self._subtitle_label.setVisible(bool(self._subtitle))
        layout.addWidget(self._subtitle_label)

        layout.addStretch()

    def _setup_shadow(self) -> None:
        """Create a drop-shadow / glow effect used when focused."""
        self._glow = QGraphicsDropShadowEffect(self)
        self._glow.setBlurRadius(0)
        self._glow.setOffset(0, 0)
        self._glow.setColor(QColor(self._color))
        self.setGraphicsEffect(self._glow)

    def _setup_animations(self) -> None:
        """Prepare property animations for smooth transitions."""
        self._glow_anim = QPropertyAnimation(self._glow, b"blurRadius")
        self._glow_anim.setDuration(Sizes.ANIMATION_FAST)
        self._glow_anim.setEasingCurve(QEasingCurve.OutCubic)

    # ------------------------------------------------------------------
    # Styling helpers
    # ------------------------------------------------------------------

    def _apply_default_style(self) -> None:
        self._update_style()

    def _update_style(self) -> None:
        if self._is_focused:
            bg = Colors.BG_CARD_HOVER
            border = f"2px solid {self._color}"
        elif self._is_hovered:
            bg = Colors.BG_CARD_HOVER
            border = f"1px solid {Colors.BORDER}"
        else:
            bg = Colors.BG_CARD
            border = f"1px solid transparent"

        self.setStyleSheet(
            f"""
            #FocusCard {{
                background: {bg};
                border: {border};
                border-radius: {Sizes.CARD_RADIUS}px;
            }}
            """
        )

    # ------------------------------------------------------------------
    # Focus / Hover events
    # ------------------------------------------------------------------

    def enterEvent(self, event) -> None:
        self._is_hovered = True
        self._update_style()
        if not self._is_focused:
            self._animate_glow(12)
        super().enterEvent(event)

    def leaveEvent(self, event) -> None:
        self._is_hovered = False
        self._update_style()
        if not self._is_focused:
            self._animate_glow(0)
        super().leaveEvent(event)

    def focusInEvent(self, event) -> None:
        self._is_focused = True
        self._update_style()
        self._animate_glow(24)
        super().focusInEvent(event)

    def focusOutEvent(self, event) -> None:
        self._is_focused = False
        self._update_style()
        target = 12 if self._is_hovered else 0
        self._animate_glow(target)
        super().focusOutEvent(event)

    def _animate_glow(self, target_radius: int) -> None:
        self._glow_anim.stop()
        self._glow_anim.setStartValue(self._glow.blurRadius())
        self._glow_anim.setEndValue(target_radius)
        self._glow_anim.start()

    # ------------------------------------------------------------------
    # Input events
    # ------------------------------------------------------------------

    def mousePressEvent(self, event: QMouseEvent) -> None:
        self.clicked.emit()
        super().mousePressEvent(event)

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if event.key() in (Qt.Key_Return, Qt.Key_Enter):
            self.clicked.emit()
        else:
            super().keyPressEvent(event)
