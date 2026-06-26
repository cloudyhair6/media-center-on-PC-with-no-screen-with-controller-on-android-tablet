"""Movie browser with search, grid display, and media scanning."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal, QSize
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QScrollArea, QGridLayout, QFrame, QPushButton, QSizePolicy,
    QGraphicsOpacityEffect,
)
from PySide6.QtGui import QKeyEvent

from styles.theme import Colors, Sizes
from backend.media_scanner import MediaScanner, MovieInfo


class _MovieCard(QFrame):
    """Individual clickable movie card in the grid."""

    clicked = Signal(str)  # emits file path
    navigate_dir = Signal(int)  # emits Qt.Key

    def __init__(
        self, movie: MovieInfo, parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self._movie = movie
        self.setObjectName("MovieCard")
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFixedSize(200, 180)
        self._build_ui()
        self._apply_style(focused=False)

    def _build_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(
            Sizes.SPACING_SM, Sizes.SPACING_SM,
            Sizes.SPACING_SM, Sizes.SPACING_SM,
        )
        layout.setSpacing(Sizes.SPACING_SM)

        # Icon placeholder
        icon_frame = QFrame()
        icon_frame.setFixedHeight(80)
        icon_frame.setStyleSheet(
            f"background: {Colors.BG_TERTIARY}; "
            f"border-radius: {Sizes.CARD_RADIUS_SM}px;"
        )
        icon_layout = QVBoxLayout(icon_frame)
        icon_layout.setContentsMargins(0, 0, 0, 0)
        icon_label = QLabel("🎬")
        icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        icon_label.setStyleSheet(
            "font-size: 36px; background: transparent;"
        )
        icon_layout.addWidget(icon_label)
        layout.addWidget(icon_frame)

        # Movie name (elided)
        name_label = QLabel(self._movie.name)
        name_label.setWordWrap(False)
        name_label.setAlignment(Qt.AlignmentFlag.AlignLeft)
        name_label.setStyleSheet(
            f"font-size: {Sizes.FONT_BODY}px; "
            f"font-weight: 600; "
            f"color: {Colors.TEXT_PRIMARY}; "
            f"background: transparent; "
            f"padding: 0 2px;"
        )
        name_label.setMaximumWidth(184)
        # Use elide via setting a fixed width — Qt will clip text
        layout.addWidget(name_label)

        # Meta line: size + date
        meta = QLabel(
            f"{self._movie.size_display}  •  {self._movie.modified_display}"
        )
        meta.setStyleSheet(
            f"font-size: {Sizes.FONT_TINY}px; "
            f"color: {Colors.TEXT_SECONDARY}; "
            f"background: transparent; "
            f"padding: 0 2px;"
        )
        meta.setMaximumWidth(184)
        layout.addWidget(meta)

        layout.addStretch()

    # ------------------------------------------------------------- Style
    def _apply_style(self, focused: bool = False) -> None:
        border_color = Colors.ACCENT if focused else "transparent"
        bg = Colors.BG_CARD_HOVER if focused else Colors.BG_CARD
        self.setStyleSheet(
            f"QFrame#MovieCard {{ "
            f"  background: {bg}; "
            f"  border: 2px solid {border_color}; "
            f"  border-radius: {Sizes.CARD_RADIUS}px; "
            f"}}"
        )

    def focusInEvent(self, event) -> None:  # noqa: N802
        self._apply_style(focused=True)
        super().focusInEvent(event)

    def focusOutEvent(self, event) -> None:  # noqa: N802
        self._apply_style(focused=False)
        super().focusOutEvent(event)

    def mousePressEvent(self, event) -> None:  # noqa: N802
        self.clicked.emit(str(self._movie.path))

    def keyPressEvent(self, event: QKeyEvent) -> None:  # noqa: N802
        key = event.key()
        if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
            self.clicked.emit(str(self._movie.path))
        elif key in (Qt.Key.Key_Up, Qt.Key.Key_Down, Qt.Key.Key_Left, Qt.Key.Key_Right):
            self.navigate_dir.emit(key)
        else:
            super().keyPressEvent(event)


class MovieBrowser(QWidget):
    """Browsable, searchable grid of movie files."""

    play_movie = Signal(str)  # emits file path
    go_back = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("MovieBrowser")
        self._movies: list[MovieInfo] = []
        self._cards: list[_MovieCard] = []
        self._columns = 4
        self._setup_ui()
        self._start_scan()

    # ------------------------------------------------------------------ Callbacks
    def _on_card_navigate(self, key: int) -> None:
        """Handle custom navigation requests from cards."""
        current_idx = -1
        for i, card in enumerate(self._cards):
            # Check logical focus since native focus might be lost if app is in background
            if card.hasFocus() or card == self.window().focusWidget():
                current_idx = i
                break

        if current_idx == -1:
            return

        cols = self._columns
        new_idx = current_idx

        if key == Qt.Key.Key_Right:
            if current_idx < len(self._cards) - 1:
                new_idx = current_idx + 1
        elif key == Qt.Key.Key_Left:
            if current_idx > 0:
                new_idx = current_idx - 1
            else:
                self.go_back.emit()
                return
        elif key == Qt.Key.Key_Down:
            if current_idx + cols < len(self._cards):
                new_idx = current_idx + cols
            else:
                new_idx = len(self._cards) - 1
        elif key == Qt.Key.Key_Up:
            if current_idx - cols >= 0:
                new_idx = current_idx - cols
            else:
                self._search.setFocus()
                return

        if new_idx != current_idx:
            self._cards[new_idx].setFocus()
            self._scroll.ensureWidgetVisible(self._cards[new_idx])

    def keyPressEvent(self, event: QKeyEvent) -> None:
        key = event.key()
        if not self._cards:
            super().keyPressEvent(event)
            return

        # Handle case where focus is completely lost from the cards
        current_idx = -1
        for i, card in enumerate(self._cards):
            if card.hasFocus() or card == self.window().focusWidget():
                current_idx = i
                break

        if current_idx == -1:
            if key in (Qt.Key.Key_Down, Qt.Key.Key_Right):
                self._cards[0].setFocus()
                event.accept()
                return
            super().keyPressEvent(event)
            return
        
        # If we got here, a card has focus but the event wasn't intercepted by it?
        # This shouldn't happen with our new navigate_dir signal, but just in case:
        self._on_card_navigate(key)
        event.accept()

    def showEvent(self, event) -> None:
        super().showEvent(event)
        # Force focus to the first card if available, else the search bar
        if self._cards:
            self._cards[0].setFocus()
        else:
            self._search.setFocus()

    # ------------------------------------------------------------------ UI
    def _setup_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(
            Sizes.SPACING_LG, Sizes.SPACING_LG,
            Sizes.SPACING_LG, Sizes.SPACING_LG,
        )
        root.setSpacing(Sizes.SPACING)

        # ---- header row
        header = QHBoxLayout()
        header.setSpacing(Sizes.SPACING)

        back_btn = QPushButton("←  Back")
        back_btn.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        back_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        back_btn.setStyleSheet(
            f"QPushButton {{ "
            f"  background: {Colors.BG_TERTIARY}; "
            f"  color: {Colors.TEXT_PRIMARY}; "
            f"  border: none; "
            f"  border-radius: {Sizes.BUTTON_RADIUS}px; "
            f"  padding: 10px 20px; "
            f"  font-size: {Sizes.FONT_BODY}px; "
            f"}} "
            f"QPushButton:hover {{ background: {Colors.BG_CARD_HOVER}; }} "
            f"QPushButton:focus {{ border: 2px solid {Colors.ACCENT}; }}"
        )
        back_btn.clicked.connect(self.go_back.emit)
        header.addWidget(back_btn)

        title = QLabel("Movies")
        title.setStyleSheet(
            f"font-size: {Sizes.FONT_H1}px; "
            f"font-weight: 700; "
            f"color: {Colors.TEXT_PRIMARY}; "
            f"background: transparent;"
        )
        header.addWidget(title)

        header.addStretch()

        self._search = QLineEdit()
        self._search.setPlaceholderText("Search movies...")
        self._search.setFixedWidth(280)
        self._search.setStyleSheet(
            f"QLineEdit {{ "
            f"  background: {Colors.BG_TERTIARY}; "
            f"  color: {Colors.TEXT_PRIMARY}; "
            f"  border: 2px solid transparent; "
            f"  border-radius: {Sizes.BUTTON_RADIUS}px; "
            f"  padding: 10px 16px; "
            f"  font-size: {Sizes.FONT_BODY}px; "
            f"}} "
            f"QLineEdit:focus {{ border-color: {Colors.ACCENT}; }} "
            f"QLineEdit::placeholder {{ color: {Colors.TEXT_MUTED}; }}"
        )
        self._search.textChanged.connect(self._filter_movies)
        header.addWidget(self._search)

        self._count_label = QLabel("0 movies")
        self._count_label.setStyleSheet(
            f"font-size: {Sizes.FONT_SMALL}px; "
            f"color: {Colors.TEXT_SECONDARY}; "
            f"background: transparent; "
            f"margin-left: 8px;"
        )
        header.addWidget(self._count_label)

        root.addLayout(header)

        # ---- loading indicator
        self._loading_label = QLabel("⏳  Scanning for movies…")
        self._loading_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._loading_label.setStyleSheet(
            f"font-size: {Sizes.FONT_H3}px; "
            f"color: {Colors.TEXT_SECONDARY}; "
            f"padding: 60px; "
            f"background: transparent;"
        )
        root.addWidget(self._loading_label)

        # ---- scroll area with grid
        self._scroll = QScrollArea()
        self._scroll.setWidgetResizable(True)
        self._scroll.setHorizontalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAlwaysOff,
        )
        self._scroll.setStyleSheet(
            "QScrollArea { border: none; background: transparent; } "
            "QScrollBar:vertical { width: 6px; background: transparent; } "
            f"QScrollBar::handle:vertical {{ "
            f"  background: {Colors.TEXT_MUTED}; border-radius: 3px; "
            f"}} "
            "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical "
            "{ height: 0; }"
        )

        self._grid_widget = QWidget()
        self._grid_widget.setStyleSheet("background: transparent;")
        self._grid_layout = QGridLayout(self._grid_widget)
        self._grid_layout.setSpacing(Sizes.SPACING)
        self._grid_layout.setContentsMargins(0, 0, 0, 0)
        self._scroll.setWidget(self._grid_widget)
        self._scroll.setVisible(False)
        root.addWidget(self._scroll, 1)

    # ------------------------------------------------------------ Scanner
    def _start_scan(self) -> None:
        self._scanner = MediaScanner()
        self._scanner.scan_complete.connect(self._on_scan_complete)
        self._scanner.progress.connect(self._on_scan_progress)
        self._scanner.error.connect(self._on_scan_error)
        self._scanner.start()

    def _on_scan_progress(self, current: int, total: int) -> None:
        self._loading_label.setText(
            f"⏳  Scanning… ({current}/{total})"
        )

    def _on_scan_complete(self, movies: list) -> None:
        self._movies = movies
        self._loading_label.setVisible(False)
        self._scroll.setVisible(True)
        self._populate_grid(self._movies)

    def _on_scan_error(self, msg: str) -> None:
        self._loading_label.setText(f"⚠️  {msg}")

    # ----------------------------------------------------------- Grid ops
    def _clear_grid(self) -> None:
        """Remove all cards from the grid layout."""
        while self._grid_layout.count():
            item = self._grid_layout.takeAt(0)
            widget = item.widget()
            if widget:
                widget.setParent(None)
                widget.deleteLater()
        self._cards.clear()

    def _populate_grid(self, movies: list[MovieInfo]) -> None:
        self._clear_grid()
        for idx, movie in enumerate(movies):
            card = _MovieCard(movie, parent=self._grid_widget)
            card.clicked.connect(self.play_movie.emit)
            card.navigate_dir.connect(self._on_card_navigate)
            row, col = divmod(idx, self._columns)
            self._grid_layout.addWidget(card, row, col)
            self._cards.append(card)

        # Spacer to push cards to the top-left
        self._grid_layout.setRowStretch(
            len(movies) // self._columns + 1, 1,
        )
        self._count_label.setText(
            f"{len(movies)} movie{'s' if len(movies) != 1 else ''}"
        )

        # Focus first card
        if self._cards:
            self._cards[0].setFocus()

    def _filter_movies(self, text: str) -> None:
        query = text.strip().lower()
        if not query:
            self._populate_grid(self._movies)
            return
        filtered = [m for m in self._movies if query in m.name.lower()]
        self._populate_grid(filtered)

    # --------------------------------------------------------- Keyboard
    def keyPressEvent(self, event: QKeyEvent) -> None:  # noqa: N802
        key = event.key()

        if key == Qt.Key.Key_Escape:
            self.go_back.emit()
            return

        focused = self.focusWidget()
        if isinstance(focused, _MovieCard) and focused in self._cards:
            idx = self._cards.index(focused)
            new_idx = idx
            if key == Qt.Key.Key_Right:
                new_idx = min(idx + 1, len(self._cards) - 1)
            elif key == Qt.Key.Key_Left:
                new_idx = max(idx - 1, 0)
            elif key == Qt.Key.Key_Down:
                new_idx = min(idx + self._columns, len(self._cards) - 1)
            elif key == Qt.Key.Key_Up:
                new_idx = max(idx - self._columns, 0)
            else:
                super().keyPressEvent(event)
                return
            if new_idx != idx:
                self._cards[new_idx].setFocus()
                self._scroll.ensureWidgetVisible(self._cards[new_idx])
            return

        super().keyPressEvent(event)

    def showEvent(self, event) -> None:  # noqa: N802
        super().showEvent(event)
        if self._cards:
            self._cards[0].setFocus()
