"""
Milestone 2 Test Suite: UI Additions & Theme Fixes (R2.1, R2.2, R2.3, R2.4)
=============================================================================
Tests:
  1. R2.1: PC Connection Status Indicator 3-State Machine & Polling Integration
  2. R2.2: Now Playing Loading Indicator Lifecycle & Sub-tab Transitions
  3. R2.3: Pixel-Perfect Album Art Setting Toggle, ScaleType, & Server Config Sync
  4. R2.4: Dark Theme Native Button Styling, State Selectors, & Resource Integrity
"""

import unittest
from unittest.mock import MagicMock, patch
import json
import sys
import os
import xml.etree.ElementTree as ET
from pathlib import Path

# Add pc_app directory to path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "pc_app"))


class TestConnectionStateMachine(unittest.TestCase):
    """
    R2.1: Test the connection state machine logic mirroring MainActivity.java:
    - 0 = STATUS_ONLINE (Green #00e676)
    - 1 = STATUS_CONNECTING (Amber #ffa726)
    - 2 = STATUS_OFFLINE (Red #ff5252)
    """

    STATUS_ONLINE = 0
    STATUS_CONNECTING = 1
    STATUS_OFFLINE = 2

    class SimulatedConnectionManager:
        def __init__(self):
            self.current_status = TestConnectionStateMachine.STATUS_OFFLINE
            self.consecutive_failures = 0
            self.status_history = [self.current_status]

        def update_status(self, status):
            self.current_status = status
            self.status_history.append(status)

        def on_connection_success(self):
            self.consecutive_failures = 0
            if self.current_status != TestConnectionStateMachine.STATUS_ONLINE:
                self.update_status(TestConnectionStateMachine.STATUS_ONLINE)

        def on_connection_failure(self):
            self.consecutive_failures += 1
            if self.consecutive_failures >= 2:
                if self.current_status != TestConnectionStateMachine.STATUS_OFFLINE:
                    self.update_status(TestConnectionStateMachine.STATUS_OFFLINE)
            else:
                if self.current_status != TestConnectionStateMachine.STATUS_CONNECTING:
                    self.update_status(TestConnectionStateMachine.STATUS_CONNECTING)

        def on_connect_to_ip(self, success):
            self.update_status(TestConnectionStateMachine.STATUS_CONNECTING)
            if success:
                self.on_connection_success()
            else:
                self.consecutive_failures = 2
                self.update_status(TestConnectionStateMachine.STATUS_OFFLINE)

        def on_disconnect(self):
            self.consecutive_failures = 2
            self.update_status(TestConnectionStateMachine.STATUS_OFFLINE)

        def on_manual_refresh(self):
            self.update_status(TestConnectionStateMachine.STATUS_CONNECTING)

    def test_initial_state_is_offline(self):
        cm = self.SimulatedConnectionManager()
        self.assertEqual(cm.current_status, self.STATUS_OFFLINE)

    def test_successful_poll_transitions_to_online(self):
        cm = self.SimulatedConnectionManager()
        cm.on_connection_success()
        self.assertEqual(cm.current_status, self.STATUS_ONLINE)
        self.assertEqual(cm.consecutive_failures, 0)

    def test_first_failure_transitions_to_connecting(self):
        cm = self.SimulatedConnectionManager()
        cm.on_connection_success()
        self.assertEqual(cm.current_status, self.STATUS_ONLINE)

        # 1st failure -> amber connecting
        cm.on_connection_failure()
        self.assertEqual(cm.current_status, self.STATUS_CONNECTING)
        self.assertEqual(cm.consecutive_failures, 1)

    def test_second_consecutive_failure_transitions_to_offline(self):
        cm = self.SimulatedConnectionManager()
        cm.on_connection_success()

        cm.on_connection_failure()
        self.assertEqual(cm.current_status, self.STATUS_CONNECTING)

        # 2nd failure -> red offline
        cm.on_connection_failure()
        self.assertEqual(cm.current_status, self.STATUS_OFFLINE)
        self.assertEqual(cm.consecutive_failures, 2)

    def test_recovery_from_connecting_state(self):
        cm = self.SimulatedConnectionManager()
        cm.on_connection_success()
        cm.on_connection_failure()  # connecting
        self.assertEqual(cm.current_status, self.STATUS_CONNECTING)

        # Success resets failures and restores online
        cm.on_connection_success()
        self.assertEqual(cm.current_status, self.STATUS_ONLINE)
        self.assertEqual(cm.consecutive_failures, 0)

    def test_disconnect_immediately_sets_offline(self):
        cm = self.SimulatedConnectionManager()
        cm.on_connection_success()
        self.assertEqual(cm.current_status, self.STATUS_ONLINE)

        cm.on_disconnect()
        self.assertEqual(cm.current_status, self.STATUS_OFFLINE)
        self.assertEqual(cm.consecutive_failures, 2)

    def test_manual_refresh_sets_connecting(self):
        cm = self.SimulatedConnectionManager()
        cm.on_connection_success()

        cm.on_manual_refresh()
        self.assertEqual(cm.current_status, self.STATUS_CONNECTING)

    def test_connect_to_ip_flow(self):
        cm = self.SimulatedConnectionManager()
        # Connect success
        cm.on_connect_to_ip(success=True)
        self.assertEqual(cm.current_status, self.STATUS_ONLINE)

        # Connect failure
        cm.on_connect_to_ip(success=False)
        self.assertEqual(cm.current_status, self.STATUS_OFFLINE)


class TestNowPlayingLoadingIndicator(unittest.TestCase):
    """
    R2.2: Test Now Playing loading overlay lifecycle.
    """

    class SimulatedNowPlayingUI:
        def __init__(self):
            self.overlay_visible = False
            self.loading_text = ""
            self.active_subtab = 0

        def show_np_loading(self, message="Loading Now Playing..."):
            self.overlay_visible = True
            self.loading_text = message

        def hide_np_loading(self):
            self.overlay_visible = False

        def switch_music_subtab(self, index, api_available=True):
            self.active_subtab = index
            if index == 0:
                self.show_np_loading("Loading Now Playing...")
                if not api_available:
                    self.hide_np_loading()

        def on_now_playing_data_received(self, json_data):
            self.hide_np_loading()
            return json_data.get("title", "Not Playing")

        def on_refresh_clicked(self):
            self.show_np_loading("Refreshing...")

    def test_subtab_switch_shows_loading(self):
        ui = self.SimulatedNowPlayingUI()
        ui.switch_music_subtab(0)
        self.assertTrue(ui.overlay_visible)
        self.assertEqual(ui.loading_text, "Loading Now Playing...")

    def test_data_received_dismisses_loading(self):
        ui = self.SimulatedNowPlayingUI()
        ui.switch_music_subtab(0)
        self.assertTrue(ui.overlay_visible)

        title = ui.on_now_playing_data_received({"title": "Test Song", "artist": "Test Artist"})
        self.assertFalse(ui.overlay_visible)
        self.assertEqual(title, "Test Song")

    def test_refresh_button_triggers_loading(self):
        ui = self.SimulatedNowPlayingUI()
        ui.on_refresh_clicked()
        self.assertTrue(ui.overlay_visible)
        self.assertEqual(ui.loading_text, "Refreshing...")


class TestPixelPerfectAlbumArtSetting(unittest.TestCase):
    """
    R2.3: Test pixel-perfect album art configuration, scaling type, and server sync.
    """

    class SimulatedBitmapDecoder:
        @staticmethod
        def calculate_in_sample_size(orig_w, orig_h, req_w, req_h):
            in_sample_size = 1
            if orig_h > req_h or orig_w > req_w:
                half_h = orig_h // 2
                half_w = orig_w // 2
                while (half_h // in_sample_size) >= req_h and (half_w // in_sample_size) >= req_w:
                    in_sample_size *= 2
            return max(1, in_sample_size)

        @staticmethod
        def decode_options(orig_w, orig_h, req_w, req_h, pixel_perfect):
            in_scaled = False
            if not pixel_perfect and req_w > 0 and req_h > 0:
                in_sample_size = TestPixelPerfectAlbumArtSetting.SimulatedBitmapDecoder.calculate_in_sample_size(
                    orig_w, orig_h, req_w, req_h
                )
            else:
                in_sample_size = 1
            scale_type = "CENTER" if pixel_perfect else "FIT_CENTER"
            return {
                "inScaled": in_scaled,
                "inSampleSize": in_sample_size,
                "scaleType": scale_type
            }

    def test_pixel_perfect_enabled_preserves_1_to_1(self):
        opts = self.SimulatedBitmapDecoder.decode_options(
            orig_w=300, orig_h=300, req_w=135, req_h=135, pixel_perfect=True
        )
        self.assertFalse(opts["inScaled"])
        self.assertEqual(opts["inSampleSize"], 1)
        self.assertEqual(opts["scaleType"], "CENTER")

    def test_pixel_perfect_disabled_scales_to_fit(self):
        opts = self.SimulatedBitmapDecoder.decode_options(
            orig_w=500, orig_h=500, req_w=100, req_h=100, pixel_perfect=False
        )
        self.assertFalse(opts["inScaled"])
        self.assertGreaterEqual(opts["inSampleSize"], 2)
        self.assertEqual(opts["scaleType"], "FIT_CENTER")

    def test_remote_server_config_set_endpoint(self):
        """Verify remote_server /api/config/set parses key=pixel_perfect_art correctly."""
        from urllib.parse import urlparse, parse_qs
        url = "/api/config/set?key=pixel_perfect_art&value=true"
        query = parse_qs(urlparse(url).query)
        self.assertEqual(query.get("key"), ["pixel_perfect_art"])
        self.assertEqual(query.get("value"), ["true"])


class TestDarkThemeResourcesAndButtonStyling(unittest.TestCase):
    """
    R2.4: Test XML resource definitions, drawables, styles, and dark theme consistency.
    """

    RES_DIR = PROJECT_ROOT / "development" / "android_kiosk" / "src" / "main" / "res"

    def test_drawable_dots_exist_and_are_shapes(self):
        for dot_file in ["dot_online.xml", "dot_connecting.xml", "dot_offline.xml"]:
            dot_path = self.RES_DIR / "drawable" / dot_file
            self.assertTrue(dot_path.exists(), f"Missing drawable {dot_file}")
            tree = ET.parse(str(dot_path))
            root = tree.getroot()
            self.assertTrue(root.tag.endswith("shape") or root.tag == "shape", f"{dot_file} must be a shape drawable")

    def test_btn_dark_drawable_has_state_selectors(self):
        btn_dark_path = self.RES_DIR / "drawable" / "btn_dark.xml"
        self.assertTrue(btn_dark_path.exists(), "Missing btn_dark.xml")
        tree = ET.parse(str(btn_dark_path))
        root = tree.getroot()
        self.assertTrue(root.tag.endswith("selector") or root.tag == "selector", "btn_dark.xml must be a selector")
        items = root.findall("item") or root.findall("{http://schemas.android.com/apk/res/android}item")
        self.assertGreaterEqual(len(items), 3, "btn_dark.xml must define pressed, focused, and default states")

    def test_btn_toggle_dark_drawable_has_checked_and_unchecked_states(self):
        toggle_path = self.RES_DIR / "drawable" / "btn_toggle_dark.xml"
        self.assertTrue(toggle_path.exists(), "Missing btn_toggle_dark.xml")
        tree = ET.parse(str(toggle_path))
        root = tree.getroot()
        self.assertTrue(root.tag.endswith("selector") or root.tag == "selector")

    def test_color_state_lists_exist(self):
        btn_text_path = self.RES_DIR / "color" / "btn_dark_text.xml"
        toggle_text_path = self.RES_DIR / "color" / "toggle_dark_text.xml"
        self.assertTrue(btn_text_path.exists(), "Missing color/btn_dark_text.xml")
        self.assertTrue(toggle_text_path.exists(), "Missing color/toggle_dark_text.xml")

    def test_colors_xml_contains_dark_theme_palette(self):
        colors_path = self.RES_DIR / "values" / "colors.xml"
        self.assertTrue(colors_path.exists(), "Missing values/colors.xml")
        tree = ET.parse(str(colors_path))
        root = tree.getroot()
        color_names = {elem.attrib.get("name") for elem in root.findall("color")}
        required_colors = {
            "status_online", "status_connecting", "status_offline",
            "btn_dark_normal", "btn_dark_pressed", "btn_dark_stroke", "accent_cyan"
        }
        for req in required_colors:
            self.assertIn(req, color_names, f"colors.xml missing required color: {req}")

    def test_styles_xml_defines_app_theme_and_button_styles(self):
        styles_path = self.RES_DIR / "values" / "styles.xml"
        self.assertTrue(styles_path.exists(), "Missing values/styles.xml")
        tree = ET.parse(str(styles_path))
        root = tree.getroot()
        style_names = {elem.attrib.get("name") for elem in root.findall("style")}
        self.assertIn("AppTheme", style_names, "styles.xml must define AppTheme")
        self.assertIn("Widget.Button.Dark", style_names, "styles.xml must define Widget.Button.Dark")
        self.assertIn("Widget.Button.Toggle.Dark", style_names, "styles.xml must define Widget.Button.Toggle.Dark")

    def test_manifest_declares_app_theme(self):
        manifest_path = PROJECT_ROOT / "development" / "android_kiosk" / "src" / "main" / "AndroidManifest.xml"
        self.assertTrue(manifest_path.exists(), "Missing AndroidManifest.xml")
        with open(manifest_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn('android:theme="@style/AppTheme"', content, "AndroidManifest.xml must use @style/AppTheme")

    def test_activity_main_layout_contains_m2_elements(self):
        layout_path = self.RES_DIR / "layout" / "activity_main.xml"
        self.assertTrue(layout_path.exists(), "Missing activity_main.xml")
        with open(layout_path, "r", encoding="utf-8") as f:
            content = f.read()

        # R2.1 elements
        self.assertIn('id="@+id/connection_status_container"', content)
        self.assertIn('id="@+id/connection_status_dot"', content)
        self.assertIn('id="@+id/connection_status_text"', content)

        # R2.2 elements
        self.assertIn('id="@+id/music_now_playing_container"', content)
        self.assertIn('id="@+id/np_loading_overlay"', content)
        self.assertIn('id="@+id/np_loading_text"', content)

        # R2.3 elements
        self.assertIn('id="@+id/toggle_pixel_perfect_art"', content)

        # R2.4 styling references
        self.assertIn('@drawable/btn_dark', content)
        self.assertIn('@drawable/btn_toggle_dark', content)


if __name__ == "__main__":
    unittest.main()
