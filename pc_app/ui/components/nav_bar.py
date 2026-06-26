"""
NavBar - A vertical sidebar navigation bar with emoji icons and labels.

Supports keyboard navigation (Up/Down/Enter) and emits item_clicked when
an item is selected.
"""

from PySide6.QtCore import Qt, Signal, QPropertyAnimation, QEasingCurve
from PySide6.QtGui import QKeyEvent
from PySide6.QtWidgets import (
    QFrame,
    QLabel,
    QVBoxLayout,
    QHBoxLayout,
    QSizePolicy,
)

from styles.theme import Colors, Sizes


class _NavItem(QFrame):
    """A single navigation item displaying an emoji icon and a label."""

    clicked = Signal()

    def __init__(self, icon_char: str, label: str, parent=None) -> None:
        super().__init__(parent)
        self._icon_char = icon_char
        self._label_text = label
        self._is_active = False

        self.setCursor(Qt.PointingHandCursor)
        self.setObjectName("NavItem")
        self._setup_ui()
        self._apply_style()

    def _setup_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 8, 0, 8)
        layout.setSpacing(2)
        layout.setAlignment(Qt.AlignCenter)

        self._icon_label = QLabel(self._icon_char)
        self._icon_label.setAlignment(Qt.AlignCenter)
        self._icon_label.setStyleSheet(
            "font-size: 28px; background: transparent; border: none;"
        )
        self._icon_label.setAttribute(Qt.WA_TransparentForMouseEvents)

        self._text_label = QLabel(self._label_text)
        self._text_label.setAlignment(Qt.AlignCenter)
        self._text_label.setAttribute(Qt.WA_TransparentForMouseEvents)

        layout.addWidget(self._icon_label)
        layout.addWidget(self._text_label)

    def set_active(self, active: bool) -> None:
        self._is_active = active
        self._apply_style()

    @property
    def is_active(self) -> bool:
        return self._is_active

    def _apply_style(self) -> None:
        if self._is_active:
            text_color = Colors.TEXT_PRIMARY
            border_left = f"3px solid {Colors.ACCENT}"
            bg = "rgba(255, 255, 255, 0.05)"
        else:
            text_color = Colors.TEXT_MUTED
            border_left = "3px solid transparent"
            bg = "transparent"

        self.setStyleSheet(
            f"""
            #NavItem {{
                background: {bg};
                border-left: {border_left};
                border-top: none;
                border-right: none;
                border-bottom: none;
                border-radius: 0px;
                padding: 4px 0px;
            }}
            #NavItem:hover {{
                background: rgba(255, 255, 255, 0.08);
            }}
            """
        )
        self._text_label.setStyleSheet(
            f"font-size: 10px; color: {text_color}; background: transparent; border: none;"
        )

    def mousePressEvent(self, event) -> None:
        self.clicked.emit()
        super().mousePressEvent(event)


class NavBar(QFrame):
    """Vertical sidebar navigation bar.

    Signals:
        item_clicked(int): Emitted with the index of the clicked/selected item.
    """

    item_clicked = Signal(int)

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._items: list[_NavItem] = []
        self._active_index: int = -1

        self.setObjectName("NavBar")
        self.setFixedWidth(Sizes.NAV_WIDTH)
        self.setFocusPolicy(Qt.StrongFocus)
        self.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Expanding)

        self._setup_ui()
        self._apply_style()

    def _setup_ui(self) -> None:
        self._layout = QVBoxLayout(self)
        self._layout.setContentsMargins(0, Sizes.SPACING, 0, Sizes.SPACING)
        self._layout.setSpacing(4)
        self._layout.setAlignment(Qt.AlignTop)

        # Bottom stretch keeps items at the top
        self._layout.addStretch()

    def _apply_style(self) -> None:
        self.setStyleSheet(
            f"""
            #NavBar {{
                background: {Colors.NAV_BG};
                border: none;
                border-right: 1px solid {Colors.BORDER};
            }}
            """
        )

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def add_item(self, icon_char: str, label: str) -> int:
        """Add a navigation item and return its index."""
        item = _NavItem(icon_char, label, self)
        index = len(self._items)
        item.clicked.connect(lambda idx=index: self._on_item_clicked(idx))
        self._items.append(item)

        # Insert before the stretch item
        self._layout.insertWidget(self._layout.count() - 1, item)

        # Auto-activate first item
        if index == 0:
            self.set_active(0)

        return index

    def set_active(self, index: int) -> None:
        """Set the active navigation item by index."""
        if index < 0 or index >= len(self._items):
            return
        for i, item in enumerate(self._items):
            item.set_active(i == index)
        self._active_index = index

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    def _on_item_clicked(self, index: int) -> None:
        self.set_active(index)
        self.item_clicked.emit(index)

    # ------------------------------------------------------------------
    # Keyboard navigation
    # ------------------------------------------------------------------

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if not self._items:
            super().keyPressEvent(event)
            return

        if event.key() == Qt.Key_Down:
            new_index = min(self._active_index + 1, len(self._items) - 1)
            self.set_active(new_index)
            self.item_clicked.emit(new_index)
        elif event.key() == Qt.Key_Up:
            new_index = max(self._active_index - 1, 0)
            self.set_active(new_index)
            self.item_clicked.emit(new_index)
        elif event.key() in (Qt.Key_Return, Qt.Key_Enter):
            if self._active_index >= 0:
                self.item_clicked.emit(self._active_index)
        else:
            super().keyPressEvent(event)

    # ------------------------------------------------------------------
    # Focus indicator
    # ------------------------------------------------------------------

    def focusInEvent(self, event) -> None:
        self.setStyleSheet(
            f"""
            #NavBar {{
                background: {Colors.NAV_BG};
                border: none;
                border-right: 1px solid {Colors.BORDER};
                outline: none;
            }}
            """
        )
        super().focusInEvent(event)

    def focusOutEvent(self, event) -> None:
        self._apply_style()
        super().focusOutEvent(event)
