"""
SliderSetting - A horizontal setting row with icon, label, slider, and value display.

Provides a styled QSlider with real-time value display and focus indication.
"""

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QSlider,
    QSizePolicy,
)

from styles.theme import Colors, Sizes


class SliderSetting(QFrame):
    """A settings row containing an icon, label, slider, and live value display.

    Signals:
        value_changed(int): Emitted when the slider value changes.
    """

    value_changed = Signal(int)

    def __init__(
        self,
        label: str,
        icon_char: str,
        min_val: int = 0,
        max_val: int = 100,
        value: int = 50,
        parent=None,
    ) -> None:
        super().__init__(parent)
        self._label_text = label
        self._icon_char = icon_char
        self._min_val = min_val
        self._max_val = max_val

        self.setObjectName("SliderSetting")
        self.setFocusPolicy(Qt.StrongFocus)
        self.setFixedHeight(70)

        self._setup_ui()
        self._apply_style()

        # Set initial value after UI is built
        self._slider.setValue(value)
        self._update_value_label(value)

    # ------------------------------------------------------------------
    # UI Setup
    # ------------------------------------------------------------------

    def _setup_ui(self) -> None:
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

        # Label
        text_label = QLabel(self._label_text)
        text_label.setStyleSheet(
            f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_PRIMARY}; "
            f"background: transparent; border: none; font-weight: 500;"
        )
        text_label.setSizePolicy(QSizePolicy.Minimum, QSizePolicy.Preferred)
        layout.addWidget(text_label)

        # Slider
        self._slider = QSlider(Qt.Horizontal)
        self._slider.setMinimum(self._min_val)
        self._slider.setMaximum(self._max_val)
        self._slider.setFocusPolicy(Qt.NoFocus)  # parent frame handles focus
        self._slider.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self._slider.setStyleSheet(self._slider_qss())
        self._slider.valueChanged.connect(self._on_value_changed)
        layout.addWidget(self._slider, stretch=1)

        # Value display
        self._value_label = QLabel("")
        self._value_label.setFixedWidth(48)
        self._value_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        self._value_label.setStyleSheet(
            f"font-size: {Sizes.FONT_BODY}px; color: {Colors.TEXT_SECONDARY}; "
            f"background: transparent; border: none; font-weight: bold;"
        )
        layout.addWidget(self._value_label)

    # ------------------------------------------------------------------
    # Styling
    # ------------------------------------------------------------------

    def _apply_style(self, focused: bool = False) -> None:
        border = (
            f"1px solid {Colors.ACCENT}" if focused else f"1px solid transparent"
        )
        self.setStyleSheet(
            f"""
            #SliderSetting {{
                background: {Colors.BG_CARD_SOLID};
                border: {border};
                border-radius: {Sizes.CARD_RADIUS_SM}px;
            }}
            """
        )
        # Re-apply slider styles (since parent stylesheet reset may affect it)
        self._slider.setStyleSheet(self._slider_qss())

    @staticmethod
    def _slider_qss() -> str:
        return f"""
            QSlider::groove:horizontal {{
                background: {Colors.SLIDER_GROOVE};
                height: 6px;
                border-radius: 3px;
            }}
            QSlider::handle:horizontal {{
                background: {Colors.ACCENT};
                width: 18px;
                height: 18px;
                margin: -6px 0;
                border-radius: 9px;
            }}
            QSlider::sub-page:horizontal {{
                background: {Colors.SLIDER_TRACK};
                border-radius: 3px;
            }}
        """

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def set_value(self, val: int) -> None:
        """Set the slider value programmatically."""
        self._slider.setValue(val)

    def get_value(self) -> int:
        """Return the current slider value."""
        return self._slider.value()

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    def _on_value_changed(self, value: int) -> None:
        self._update_value_label(value)
        self.value_changed.emit(value)

    def _update_value_label(self, value: int) -> None:
        self._value_label.setText(f"{value}%")

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
    # Forward key events to slider for keyboard control
    # ------------------------------------------------------------------

    def keyPressEvent(self, event) -> None:
        if event.key() in (Qt.Key_Left, Qt.Key_Right):
            self._slider.event(event)
        else:
            super().keyPressEvent(event)
