"""
ToggleSetting - A horizontal setting row with icon, label, status text,
and an animated pill-shaped toggle switch.
"""

from PySide6.QtCore import (
    Qt,
    Signal,
    QPropertyAnimation,
    QEasingCurve,
    QRectF,
    QPointF,
    Property,
)
from PySide6.QtGui import (
    QPainter,
    QColor,
    QBrush,
    QPen,
    QLinearGradient,
    QKeyEvent,
    QMouseEvent,
)
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QVBoxLayout,
    QLabel,
    QSizePolicy,
    QWidget,
)

from styles.theme import Colors, Sizes


# ======================================================================
# Custom animated toggle switch
# ======================================================================

class _ToggleSwitch(QWidget):
    """A pill-shaped toggle switch with animated handle position."""

    toggled = Signal(bool)

    TRACK_WIDTH = 52
    TRACK_HEIGHT = 28
    HANDLE_SIZE = 22
    HANDLE_MARGIN = 3  # (28 - 22) / 2

    OFF_COLOR = QColor("#2d3748")
    HANDLE_COLOR = QColor("#ffffff")

    def __init__(self, is_on: bool = False, parent=None) -> None:
        super().__init__(parent)
        self._is_on = is_on
        self.setFixedSize(self.TRACK_WIDTH, self.TRACK_HEIGHT)
        self.setCursor(Qt.PointingHandCursor)

        # Handle x-position is animated between left and right stops
        self._handle_x: float = self._target_x()

        self._anim = QPropertyAnimation(self, b"handleX")
        self._anim.setDuration(Sizes.ANIMATION_FAST)
        self._anim.setEasingCurve(QEasingCurve.InOutCubic)

        # Parse accent gradient colours for the ON track
        self._parse_accent_colors()

    # ------------------------------------------------------------------
    # Animated property
    # ------------------------------------------------------------------

    def _get_handle_x(self) -> float:
        return self._handle_x

    def _set_handle_x(self, val: float) -> None:
        self._handle_x = val
        self.update()

    handleX = Property(float, _get_handle_x, _set_handle_x)

    # ------------------------------------------------------------------
    # Geometry helpers
    # ------------------------------------------------------------------

    def _left_x(self) -> float:
        return float(self.HANDLE_MARGIN)

    def _right_x(self) -> float:
        return float(self.TRACK_WIDTH - self.HANDLE_SIZE - self.HANDLE_MARGIN)

    def _target_x(self) -> float:
        return self._right_x() if self._is_on else self._left_x()

    # ------------------------------------------------------------------
    # Accent gradient parsing
    # ------------------------------------------------------------------

    def _parse_accent_colors(self) -> None:
        """Best-effort parse of Colors.ACCENT_GRADIENT to get two stop colours."""
        # Fallback to solid accent
        self._on_color_start = QColor(Colors.ACCENT)
        self._on_color_end = QColor(Colors.ACCENT)
        try:
            grad = Colors.ACCENT_GRADIENT
            # Typical format: "qlineargradient(... stop:0 #color1, stop:1 #color2)"
            if "stop:" in grad:
                parts = grad.split("stop:")
                colors: list[QColor] = []
                for part in parts[1:]:
                    # e.g. "0 #6366f1, " or "1 #8b5cf6)"
                    tokens = part.strip().split()
                    if len(tokens) >= 2:
                        raw = tokens[1].rstrip(",).;")
                        c = QColor(raw)
                        if c.isValid():
                            colors.append(c)
                if len(colors) >= 2:
                    self._on_color_start = colors[0]
                    self._on_color_end = colors[1]
                elif len(colors) == 1:
                    self._on_color_start = colors[0]
                    self._on_color_end = colors[0]
        except Exception:
            pass

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def is_on(self) -> bool:
        return self._is_on

    def set_state(self, on: bool, animate: bool = True) -> None:
        if on == self._is_on:
            return
        self._is_on = on
        if animate:
            self._animate()
        else:
            self._handle_x = self._target_x()
            self.update()

    def toggle(self) -> None:
        self.set_state(not self._is_on)
        self.toggled.emit(self._is_on)

    # ------------------------------------------------------------------
    # Animation
    # ------------------------------------------------------------------

    def _animate(self) -> None:
        self._anim.stop()
        self._anim.setStartValue(self._handle_x)
        self._anim.setEndValue(self._target_x())
        self._anim.start()

    # ------------------------------------------------------------------
    # Painting
    # ------------------------------------------------------------------

    def paintEvent(self, event) -> None:
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        track_rect = QRectF(0, 0, self.TRACK_WIDTH, self.TRACK_HEIGHT)
        radius = self.TRACK_HEIGHT / 2.0

        # Draw track
        if self._is_on:
            gradient = QLinearGradient(0, 0, self.TRACK_WIDTH, 0)
            gradient.setColorAt(0.0, self._on_color_start)
            gradient.setColorAt(1.0, self._on_color_end)
            painter.setBrush(QBrush(gradient))
        else:
            painter.setBrush(QBrush(self.OFF_COLOR))

        painter.setPen(Qt.NoPen)
        painter.drawRoundedRect(track_rect, radius, radius)

        # Draw handle with slight shadow
        handle_y = float(self.HANDLE_MARGIN)
        handle_rect = QRectF(
            self._handle_x, handle_y, self.HANDLE_SIZE, self.HANDLE_SIZE
        )

        # Shadow
        shadow_rect = handle_rect.translated(0, 1)
        painter.setBrush(QBrush(QColor(0, 0, 0, 40)))
        painter.drawEllipse(shadow_rect)

        # Handle
        painter.setBrush(QBrush(self.HANDLE_COLOR))
        painter.drawEllipse(handle_rect)

        painter.end()

    # ------------------------------------------------------------------
    # Input
    # ------------------------------------------------------------------

    def mousePressEvent(self, event: QMouseEvent) -> None:
        self.toggle()


# ======================================================================
# ToggleSetting composite widget
# ======================================================================

class ToggleSetting(QFrame):
    """A settings row with icon, label, optional status text, and toggle switch.

    Signals:
        toggled(bool): Emitted when the toggle state changes.
    """

    toggled = Signal(bool)

    def __init__(
        self,
        label: str,
        icon_char: str,
        is_on: bool = False,
        status_text: str = "",
        parent=None,
    ) -> None:
        super().__init__(parent)
        self._label_text = label
        self._icon_char = icon_char
        self._status_text = status_text

        self.setObjectName("ToggleSetting")
        self.setFocusPolicy(Qt.StrongFocus)
        self.setFixedHeight(70)
        self.setCursor(Qt.PointingHandCursor)

        self._setup_ui(is_on)
        self._apply_style()

    # ------------------------------------------------------------------
    # UI Setup
    # ------------------------------------------------------------------

    def _setup_ui(self, is_on: bool) -> None:
        layout = QHBoxLayout(self)
        layout.setContentsMargins(Sizes.SPACING, 0, Sizes.SPACING, 0)
        layout.setSpacing(Sizes.SPACING)

        # Icon
        icon_label = QLabel(self._icon_char)
        icon_label.setFixedSize(32, 32)
        icon_label.setAlignment(Qt.AlignCenter)
        icon_label.setStyleSheet(
            "font-size: 24px; background: transparent; border: none;"
        )
        layout.addWidget(icon_label)

        # Label + status column
        text_col = QVBoxLayout()
        text_col.setContentsMargins(0, 0, 0, 0)
        text_col.setSpacing(2)

        self._label = QLabel(self._label_text)
        self._label.setStyleSheet(
            f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_PRIMARY}; "
            f"background: transparent; border: none; font-weight: 500;"
        )
        text_col.addWidget(self._label)

        self._status_label = QLabel(self._status_text)
        self._status_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; color: {Colors.TEXT_SECONDARY}; "
            f"background: transparent; border: none;"
        )
        self._status_label.setVisible(bool(self._status_text))
        text_col.addWidget(self._status_label)

        layout.addLayout(text_col)

        # Spacer
        layout.addStretch()

        # Toggle switch
        self._switch = _ToggleSwitch(is_on=is_on, parent=self)
        self._switch.toggled.connect(self._on_switch_toggled)
        layout.addWidget(self._switch)

    # ------------------------------------------------------------------
    # Styling
    # ------------------------------------------------------------------

    def _apply_style(self, focused: bool = False) -> None:
        border = (
            f"1px solid {Colors.ACCENT}" if focused else "1px solid transparent"
        )
        self.setStyleSheet(
            f"""
            #ToggleSetting {{
                background: {Colors.BG_CARD_SOLID};
                border: {border};
                border-radius: {Sizes.CARD_RADIUS_SM}px;
            }}
            """
        )

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def set_state(self, on: bool) -> None:
        """Set the toggle state programmatically."""
        self._switch.set_state(on)

    def get_state(self) -> bool:
        """Return the current toggle state."""
        return self._switch.is_on()

    def set_status_text(self, text: str) -> None:
        """Update the status text below the label."""
        self._status_label.setText(text)
        self._status_label.setVisible(bool(text))

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    def _on_switch_toggled(self, is_on: bool) -> None:
        self.toggled.emit(is_on)

    # ------------------------------------------------------------------
    # Focus indication
    # ------------------------------------------------------------------

    def focusInEvent(self, event) -> None:
        self._apply_style(focused=True)
        super().focusInEvent(event)

    def focusOutEvent(self, event) -> None:
        self._apply_style(focused=False)
        super().focusOutEvent(event)

    # ------------------------------------------------------------------
    # Keyboard input
    # ------------------------------------------------------------------

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if event.key() in (Qt.Key_Return, Qt.Key_Enter, Qt.Key_Space):
            self._switch.toggle()
        else:
            super().keyPressEvent(event)

    def mousePressEvent(self, event: QMouseEvent) -> None:
        # Clicking anywhere on the row toggles
        self._switch.toggle()
        super().mousePressEvent(event)
