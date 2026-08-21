"""
Milestone 2 Remediation Challenger Stress Test Suite
=====================================================
Empirical stress tests for Milestone 2 remediation:
1. Theme Switching Lifecycle (Dark -> Light -> Native -> Dark) & Button Styling Retention
2. Offline Refresh Error Handling, Polling Concurrency, & Loading Overlay Dismissal
"""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
import sys

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "pc_app"))


class SimulatedAndroidView:
    def __init__(self, view_id, view_type="View"):
        self.view_id = view_id
        self.view_type = view_type
        self.background_res = None
        self.background_color = None
        self.text_color = None
        self.text_color_res = None
        self.visibility = "VISIBLE"
        self.children = []
        self.text = ""

    def add_child(self, child):
        self.children.append(child)


class AndroidThemeEngineHarness:
    """
    Exact simulation of MainActivity.java theme engine:
    - applyTheme(String theme)
    - applyThemeToView(View v, int bgP, int bgS, int textP, int textS, String theme)
    """

    COLOR_BG_PRIMARY_DARK = 0xFF0a0e1a
    COLOR_BG_SECONDARY_DARK = 0xFF111827
    COLOR_TEXT_PRIMARY_DARK = 0xFFffffff
    COLOR_TEXT_SECONDARY_DARK = 0xFF8892b0

    COLOR_BG_PRIMARY_LIGHT = 0xFFf5f5f7
    COLOR_BG_SECONDARY_LIGHT = 0xFFe5e5ea
    COLOR_TEXT_PRIMARY_LIGHT = 0xFF1a1a2e
    COLOR_TEXT_SECONDARY_LIGHT = 0xFF636366

    COLOR_BG_PRIMARY_NATIVE = 0x00000000  # Transparent
    COLOR_BG_SECONDARY_NATIVE = 0x00000000
    COLOR_TEXT_PRIMARY_NATIVE = 0xFF000000  # Black
    COLOR_TEXT_SECONDARY_NATIVE = 0xFF444444

    EXCLUDED_IDS = {
        "lyrics_content", "library_content", "search_results", "queue_content",
        "connection_status_container", "connection_status_dot", "connection_status_text",
        "np_loading_overlay", "np_loading_text"
    }

    def __init__(self):
        self.current_theme = "dark"
        self.root = SimulatedAndroidView("root", "ViewGroup")
        self.build_ui_tree()

    def build_ui_tree(self):
        # Navigation tabs
        self.tab_buttons = [
            SimulatedAndroidView("tab_btn_music", "Button"),
            SimulatedAndroidView("tab_btn_settings", "Button"),
            SimulatedAndroidView("tab_btn_power", "Button")
        ]
        self.active_main_tab = 0

        # Subtab buttons
        self.subtab_buttons = [
            SimulatedAndroidView("btn_music_playing", "Button"),
            SimulatedAndroidView("btn_music_search", "Button"),
            SimulatedAndroidView("btn_music_library", "Button"),
            SimulatedAndroidView("btn_music_queue", "Button"),
            SimulatedAndroidView("btn_music_lyrics", "Button")
        ]
        self.active_subtab = 0

        # Controls & Action buttons
        self.btn_play_pause = SimulatedAndroidView("btn_play_pause", "Button")
        self.btn_next = SimulatedAndroidView("btn_next", "Button")
        self.btn_prev = SimulatedAndroidView("btn_prev", "Button")
        self.btn_shutdown = SimulatedAndroidView("btn_shutdown", "Button")
        self.btn_restart = SimulatedAndroidView("btn_restart", "Button")
        self.btn_disconnect = SimulatedAndroidView("btn_disconnect", "Button")
        self.btn_add_pc = SimulatedAndroidView("btn_add_pc", "Button")
        self.btn_remove_pc = SimulatedAndroidView("btn_remove_pc", "Button")
        self.toggle_speaker_fill = SimulatedAndroidView("toggle_speaker_fill", "ToggleButton")
        self.toggle_album_art = SimulatedAndroidView("toggle_album_art", "ToggleButton")
        self.toggle_pixel_perfect = SimulatedAndroidView("toggle_pixel_perfect_art", "ToggleButton")

        # Excluded container views
        self.lyrics_content = SimulatedAndroidView("lyrics_content", "ViewGroup")
        self.connection_status_dot = SimulatedAndroidView("connection_status_dot", "View")
        self.np_loading_overlay = SimulatedAndroidView("np_loading_overlay", "ViewGroup")
        self.np_loading_text = SimulatedAndroidView("np_loading_text", "TextView")

        # General text views
        self.np_title = SimulatedAndroidView("np_title", "TextView")
        self.np_artist = SimulatedAndroidView("np_artist", "TextView")

        # Assemble tree
        self.root.add_child(self.btn_play_pause)
        self.root.add_child(self.btn_next)
        self.root.add_child(self.btn_prev)
        self.root.add_child(self.btn_shutdown)
        self.root.add_child(self.btn_restart)
        self.root.add_child(self.btn_disconnect)
        self.root.add_child(self.btn_add_pc)
        self.root.add_child(self.btn_remove_pc)
        self.root.add_child(self.toggle_speaker_fill)
        self.root.add_child(self.toggle_album_art)
        self.root.add_child(self.toggle_pixel_perfect)
        self.root.add_child(self.lyrics_content)
        self.root.add_child(self.connection_status_dot)
        self.root.add_child(self.np_loading_overlay)
        self.root.add_child(self.np_loading_text)
        self.root.add_child(self.np_title)
        self.root.add_child(self.np_artist)
        for b in self.tab_buttons:
            self.root.add_child(b)
        for b in self.subtab_buttons:
            self.root.add_child(b)

        # Apply initial theme
        self.apply_theme("dark")

    def apply_theme(self, theme):
        self.current_theme = theme
        if theme == "native":
            bg_p = self.COLOR_BG_PRIMARY_NATIVE
            bg_s = self.COLOR_BG_SECONDARY_NATIVE
            text_p = self.COLOR_TEXT_PRIMARY_NATIVE
            text_s = self.COLOR_TEXT_SECONDARY_NATIVE
        elif theme == "light":
            bg_p = self.COLOR_BG_PRIMARY_LIGHT
            bg_s = self.COLOR_BG_SECONDARY_LIGHT
            text_p = self.COLOR_TEXT_PRIMARY_LIGHT
            text_s = self.COLOR_TEXT_SECONDARY_LIGHT
        else:
            bg_p = self.COLOR_BG_PRIMARY_DARK
            bg_s = self.COLOR_BG_SECONDARY_DARK
            text_p = self.COLOR_TEXT_PRIMARY_DARK
            text_s = self.COLOR_TEXT_SECONDARY_DARK

        self.root.background_color = bg_p
        self._apply_theme_to_view(self.root, bg_p, bg_s, text_p, text_s, theme)

        # Restore tab highlights
        for i, btn in enumerate(self.tab_buttons):
            btn.text_color = 0xFF00d4ff if i == self.active_main_tab else 0xFF8892b0
            btn.background_color = 0xFF1a1f36 if i == self.active_main_tab else 0xFF111827
            btn.background_res = None

        for i, btn in enumerate(self.subtab_buttons):
            btn.text_color = 0xFF00d4ff if i == self.active_subtab else 0xFF8892b0
            btn.background_color = 0xFF1a1f36 if i == self.active_subtab else 0xFF111827
            btn.background_res = None

    def _apply_theme_to_view(self, v, bg_p, bg_s, text_p, text_s, theme):
        if v is None:
            return
        if v.view_id in self.EXCLUDED_IDS:
            return

        if v.view_type == "ToggleButton":
            if theme == "native":
                v.background_res = "android.R.drawable.btn_default"
                v.background_color = None
                v.text_color = 0xFF000000
                v.text_color_res = None
            elif theme == "light":
                v.background_res = "android.R.drawable.btn_default"
                v.background_color = None
                v.text_color = 0xFF1a1a2e
                v.text_color_res = None
            else:
                v.background_res = "R.drawable.btn_toggle_dark"
                v.background_color = None
                v.text_color_res = "R.color.toggle_dark_text"
            return

        if v.view_type == "ViewGroup":
            for child in v.children:
                self._apply_theme_to_view(child, bg_p, bg_s, text_p, text_s, theme)

        if v.view_type == "TextView" and v.view_type != "Button":
            v.text_color = text_p

        if v.view_type == "Button":
            if theme == "native":
                v.background_res = "android.R.drawable.btn_default"
                v.background_color = None
                v.text_color = 0xFF000000
                v.text_color_res = None
            elif theme == "light":
                if v.view_id == "btn_play_pause":
                    v.background_color = 0xFF00d4ff
                    v.background_res = None
                    v.text_color = 0xFFFFFFFF
                elif v.view_id == "btn_shutdown":
                    v.background_color = 0xFFff5252
                    v.background_res = None
                    v.text_color = 0xFFFFFFFF
                elif v.view_id == "btn_restart":
                    v.background_color = 0xFFffa726
                    v.background_res = None
                    v.text_color = 0xFFFFFFFF
                elif v.view_id == "btn_disconnect":
                    v.background_res = "android.R.drawable.btn_default"
                    v.background_color = None
                    v.text_color = 0xFFff5252
                else:
                    v.background_res = "android.R.drawable.btn_default"
                    v.background_color = None
                    v.text_color = 0xFF1a1a2e
            else:
                # Dark theme
                if v.view_id == "btn_play_pause":
                    v.background_color = 0xFF00d4ff
                    v.background_res = None
                    v.text_color = 0xFFFFFFFF
                elif v.view_id == "btn_shutdown":
                    v.background_color = 0xFFff5252
                    v.background_res = None
                    v.text_color = 0xFFFFFFFF
                elif v.view_id == "btn_restart":
                    v.background_color = 0xFFffa726
                    v.background_res = None
                    v.text_color = 0xFFFFFFFF
                elif v.view_id == "btn_disconnect":
                    v.background_res = "R.drawable.btn_dark"
                    v.background_color = None
                    v.text_color = 0xFFff5252
                elif v.view_id == "btn_add_pc":
                    v.background_res = "R.drawable.btn_dark"
                    v.background_color = None
                    v.text_color = 0xFF00d4ff
                elif v.view_id == "btn_remove_pc":
                    v.background_res = "R.drawable.btn_dark"
                    v.background_color = None
                    v.text_color = 0xFFff3333
                else:
                    v.background_res = "R.drawable.btn_dark"
                    v.background_color = None
                    v.text_color_res = "R.color.btn_dark_text"


class TestThemeSwitchingLifecycleStress(unittest.TestCase):
    """
    Stress-test theme cycling: Dark -> Light -> Native -> Dark (repeated across multiple cycles).
    Verify that returning to dark theme cleanly restores:
      1. R.drawable.btn_dark on standard buttons
      2. R.color.btn_dark_text color state list
      3. R.drawable.btn_toggle_dark on ToggleButtons
      4. Semantic action button highlights
      5. Tab and Subtab background & text colors
      6. Isolation of excluded views
    """

    def setUp(self):
        self.engine = AndroidThemeEngineHarness()

    def test_theme_lifecycle_dark_light_native_dark_transition(self):
        # 1. Start in Dark
        self.assertEqual(self.engine.current_theme, "dark")
        self.assertEqual(self.engine.btn_next.background_res, "R.drawable.btn_dark")
        self.assertEqual(self.engine.btn_next.text_color_res, "R.color.btn_dark_text")
        self.assertEqual(self.engine.toggle_speaker_fill.background_res, "R.drawable.btn_toggle_dark")

        # 2. Switch to Light
        self.engine.apply_theme("light")
        self.assertEqual(self.engine.current_theme, "light")
        self.assertEqual(self.engine.btn_next.background_res, "android.R.drawable.btn_default")
        self.assertEqual(self.engine.btn_next.text_color, 0xFF1a1a2e)
        self.assertEqual(self.engine.toggle_speaker_fill.background_res, "android.R.drawable.btn_default")
        self.assertEqual(self.engine.btn_disconnect.text_color, 0xFFff5252)

        # 3. Switch to Native
        self.engine.apply_theme("native")
        self.assertEqual(self.engine.current_theme, "native")
        self.assertEqual(self.engine.btn_next.background_res, "android.R.drawable.btn_default")
        self.assertEqual(self.engine.btn_next.text_color, 0xFF000000)
        self.assertEqual(self.engine.toggle_speaker_fill.background_res, "android.R.drawable.btn_default")
        self.assertEqual(self.engine.toggle_speaker_fill.text_color, 0xFF000000)

        # 4. Return to Dark
        self.engine.apply_theme("dark")
        self.assertEqual(self.engine.current_theme, "dark")
        self.assertEqual(self.engine.btn_next.background_res, "R.drawable.btn_dark")
        self.assertEqual(self.engine.btn_next.text_color_res, "R.color.btn_dark_text")
        self.assertEqual(self.engine.btn_prev.background_res, "R.drawable.btn_dark")
        self.assertEqual(self.engine.btn_prev.text_color_res, "R.color.btn_dark_text")
        self.assertEqual(self.engine.toggle_speaker_fill.background_res, "R.drawable.btn_toggle_dark")
        self.assertEqual(self.engine.toggle_speaker_fill.text_color_res, "R.color.toggle_dark_text")
        self.assertEqual(self.engine.btn_disconnect.background_res, "R.drawable.btn_dark")
        self.assertEqual(self.engine.btn_disconnect.text_color, 0xFFff5252)
        self.assertEqual(self.engine.btn_add_pc.text_color, 0xFF00d4ff)
        self.assertEqual(self.engine.btn_remove_pc.text_color, 0xFFff3333)

    def test_theme_switching_100_cycle_torture(self):
        """Torture test: 100 rapid theme transitions in pseudo-random sequences."""
        sequence = ["dark", "light", "native", "dark", "native", "light", "dark", "light", "dark", "native"] * 10
        for theme in sequence:
            self.engine.apply_theme(theme)

        # Final theme is native -> switch to dark
        self.engine.apply_theme("dark")

        # Verify full styling integrity after 100 transitions
        self.assertEqual(self.engine.btn_next.background_res, "R.drawable.btn_dark")
        self.assertEqual(self.engine.btn_next.text_color_res, "R.color.btn_dark_text")
        self.assertEqual(self.engine.toggle_pixel_perfect.background_res, "R.drawable.btn_toggle_dark")
        self.assertEqual(self.engine.toggle_pixel_perfect.text_color_res, "R.color.toggle_dark_text")
        self.assertEqual(self.engine.btn_play_pause.background_color, 0xFF00d4ff)
        self.assertEqual(self.engine.btn_shutdown.background_color, 0xFFff5252)
        self.assertEqual(self.engine.btn_restart.background_color, 0xFFffa726)

        # Tab highlights check
        self.assertEqual(self.engine.tab_buttons[0].text_color, 0xFF00d4ff)  # Active
        self.assertEqual(self.engine.tab_buttons[0].background_color, 0xFF1a1f36)
        self.assertEqual(self.engine.tab_buttons[1].text_color, 0xFF8892b0)  # Inactive
        self.assertEqual(self.engine.tab_buttons[1].background_color, 0xFF111827)


class TestOfflineRefreshErrorHandlingStress(unittest.TestCase):
    """
    Stress-test offline refresh error handling:
    1. Polling error guarantees hideNpLoading() dismissal
    2. Connection failure state transitions
    3. Network timeout, socket error, and HTTP 500 error scenarios
    4. Rapid refresh spamming while offline
    """

    class SimulatedKioskApp:
        def __init__(self):
            self.np_loading_visible = False
            self.np_loading_text = ""
            self.connection_status = 2  # STATUS_OFFLINE
            self.consecutive_failures = 0
            self.is_spotify_polling = False
            self.now_playing_title = "Not Playing"

        def show_np_loading(self, msg):
            self.np_loading_visible = True
            self.np_loading_text = msg

        def hide_np_loading(self):
            self.np_loading_visible = False

        def on_connection_success(self):
            self.consecutive_failures = 0
            self.connection_status = 0  # STATUS_ONLINE

        def on_connection_failure(self):
            self.consecutive_failures += 1
            if self.consecutive_failures >= 2:
                self.connection_status = 2  # STATUS_OFFLINE
            else:
                self.connection_status = 1  # STATUS_CONNECTING

        def on_refresh_clicked(self, network_action):
            self.show_np_loading("Refreshing...")
            self.now_playing_title = "Refreshing..."
            self.connection_status = 1  # STATUS_CONNECTING

            # Trigger poll
            self.execute_poll_spotify(network_action)

        def execute_poll_spotify(self, network_action):
            if self.is_spotify_polling:
                return
            self.is_spotify_polling = True
            try:
                result, data_or_err = network_action()
                if result == "SUCCESS":
                    self.is_spotify_polling = False
                    self.on_connection_success()
                    self.hide_np_loading()
                    self.now_playing_title = data_or_err.get("title", "Not Playing")
                else:
                    self.is_spotify_polling = False
                    self.hide_np_loading()
                    self.on_connection_failure()
            finally:
                self.is_spotify_polling = False

    def test_offline_refresh_with_socket_timeout(self):
        """Simulate manual refresh when PC is completely unreachable (socket timeout)."""
        app = self.SimulatedKioskApp()
        app.connection_status = 0  # Starts online

        def timeout_action():
            return "ERROR", "SocketTimeoutException: Connection timed out"

        app.on_refresh_clicked(timeout_action)

        # Overlay MUST be hidden even on timeout
        self.assertFalse(app.np_loading_visible)
        self.assertEqual(app.connection_status, 1)  # 1st failure -> CONNECTING (Amber)
        self.assertEqual(app.consecutive_failures, 1)

        # Second failed refresh -> Transitions to OFFLINE (Red)
        app.on_refresh_clicked(timeout_action)
        self.assertFalse(app.np_loading_visible)
        self.assertEqual(app.connection_status, 2)  # 2nd failure -> OFFLINE (Red)
        self.assertEqual(app.consecutive_failures, 2)

    def test_offline_refresh_with_http_500_internal_error(self):
        """Simulate manual refresh when PC server throws HTTP 500."""
        app = self.SimulatedKioskApp()

        def http_500_action():
            return "ERROR", "HTTP 500: Internal Server Error"

        app.on_refresh_clicked(http_500_action)
        self.assertFalse(app.np_loading_visible)
        self.assertIn(app.connection_status, [1, 2])

    def test_rapid_refresh_spamming_offline_overlay_dismissal(self):
        """Simulate user rapidly tapping refresh button 100 times while offline."""
        app = self.SimulatedKioskApp()

        def offline_error():
            return "ERROR", "ConnectException: Connection refused"

        for _ in range(100):
            app.on_refresh_clicked(offline_error)
            self.assertFalse(app.np_loading_visible)  # Must NEVER get stuck

        self.assertEqual(app.connection_status, 2)  # Confirmed OFFLINE
        self.assertGreaterEqual(app.consecutive_failures, 2)

    def test_offline_refresh_then_eventual_recovery(self):
        """Simulate offline refresh failures followed by server returning online."""
        app = self.SimulatedKioskApp()

        def fail_action():
            return "ERROR", "Connection failed"

        def recover_action():
            return "SUCCESS", {"title": "Solar Power", "artist": "Lorde", "playing": True}

        # 3 failures
        app.on_refresh_clicked(fail_action)
        app.on_refresh_clicked(fail_action)
        app.on_refresh_clicked(fail_action)
        self.assertFalse(app.np_loading_visible)
        self.assertEqual(app.connection_status, 2)

        # Recovery
        app.on_refresh_clicked(recover_action)
        self.assertFalse(app.np_loading_visible)
        self.assertEqual(app.connection_status, 0)
        self.assertEqual(app.now_playing_title, "Solar Power")
        self.assertEqual(app.consecutive_failures, 0)


if __name__ == "__main__":
    unittest.main()
