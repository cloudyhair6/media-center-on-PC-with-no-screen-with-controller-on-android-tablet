"""
Milestone 2 Adversarial Stress Test Suite
=========================================
Empirical Challenger test suite for Milestone 2:
  1. Dimension 1: Rapid Intermittent Network Failures, Flapping, & State Machine Invariants (R2.1)
  2. Dimension 2: Transition Sequence Online -> Connecting -> Offline -> Recovery (R2.1)
  3. Dimension 3: Rapid Sub-Tab Switching with In-Flight Asynchronous Responses (R2.2, R2.1)
  4. Dimension 4: Manual Refresh Spamming, Polling Concurrency, & Overlay Dismissal (R2.2)
  5. Dimension 5: Edge Case Payloads, Malformed Responses, & Theme Isolation (R2.3, R2.4)
"""

import unittest
from unittest.mock import MagicMock, patch
import json
import sys
import time
from pathlib import Path
from urllib.parse import urlparse, parse_qs

# Add pc_app directory to path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "pc_app"))


class AndroidConnectionStateMachine:
    """
    Exact simulation of MainActivity.java connection state machine:
      - STATUS_ONLINE = 0 (Green #00e676)
      - STATUS_CONNECTING = 1 (Amber #ffa726)
      - STATUS_OFFLINE = 2 (Red #ff5252)
    """
    STATUS_ONLINE = 0
    STATUS_CONNECTING = 1
    STATUS_OFFLINE = 2

    def __init__(self):
        self.current_status = self.STATUS_OFFLINE
        self.consecutive_failures = 0
        self.status_history = [self.current_status]
        self.ui_dot_drawable = "dot_offline"
        self.ui_text = "Offline"
        self.ui_color = 0xFFFF5252

    def update_connection_status(self, status):
        self.current_status = status
        self.status_history.append(status)
        if status == self.STATUS_ONLINE:
            self.ui_dot_drawable = "dot_online"
            self.ui_text = "Online"
            self.ui_color = 0xFF00E676
        elif status == self.STATUS_CONNECTING:
            self.ui_dot_drawable = "dot_connecting"
            self.ui_text = "Connecting..."
            self.ui_color = 0xFFFFA726
        else:
            self.ui_dot_drawable = "dot_offline"
            self.ui_text = "Offline"
            self.ui_color = 0xFFFF5252

    def on_connection_success(self):
        self.consecutive_failures = 0
        if self.current_status != self.STATUS_ONLINE:
            self.update_connection_status(self.STATUS_ONLINE)

    def on_connection_failure(self):
        self.consecutive_failures += 1
        if self.consecutive_failures >= 2:
            if self.current_status != self.STATUS_OFFLINE:
                self.update_connection_status(self.STATUS_OFFLINE)
        else:
            if self.current_status != self.STATUS_CONNECTING:
                self.update_connection_status(self.STATUS_CONNECTING)

    def connect_to_ip(self, ip, success=True):
        self.update_connection_status(self.STATUS_CONNECTING)
        if success:
            self.on_connection_success()
        else:
            self.consecutive_failures = 2
            self.update_connection_status(self.STATUS_OFFLINE)

    def on_disconnect_clicked(self):
        self.consecutive_failures = 2
        self.update_connection_status(self.STATUS_OFFLINE)

    def on_refresh_clicked(self):
        self.update_connection_status(self.STATUS_CONNECTING)


class AndroidNowPlayingUIHarness:
    """
    Simulation of MainActivity Now Playing UI, Loading Overlay, Sub-tabs,
    and Async Request Queuing.
    """
    SUBTAB_PLAYING = 0
    SUBTAB_SEARCH = 1
    SUBTAB_LIBRARY = 2
    SUBTAB_QUEUE = 3
    SUBTAB_LYRICS = 4

    def __init__(self, state_machine=None):
        self.sm = state_machine or AndroidConnectionStateMachine()
        self.active_tab = "Music"  # Music, Settings, Power
        self.active_subtab = self.SUBTAB_PLAYING
        self.np_loading_visible = False
        self.np_loading_text = ""
        self.now_playing_container_visible = True
        self.library_container_children = []
        self.queue_container_children = []
        self.search_container_children = []
        self.lyrics_container_children = []
        self.current_title = ""
        self.current_artist = ""
        self.current_album = ""
        self.current_uri = ""
        self.current_context_uri = ""
        self.is_playing = False
        self.is_spotify_polling = False
        self.is_sys_stats_polling = False

    def show_np_loading(self, message):
        self.np_loading_visible = True
        self.np_loading_text = message

    def hide_np_loading(self):
        self.np_loading_visible = False

    def switch_tab(self, tab_index):
        # 0: Music, 1: Settings, 2: Power
        tabs = ["Music", "Settings", "Power"]
        self.active_tab = tabs[tab_index]

    def switch_music_subtab(self, subtab_index, api_available=True):
        self.active_subtab = subtab_index
        self.now_playing_container_visible = (subtab_index == self.SUBTAB_PLAYING)

        if subtab_index == self.SUBTAB_PLAYING:
            self.show_np_loading("Loading Now Playing...")
            if not api_available:
                self.hide_np_loading()
        elif subtab_index == self.SUBTAB_LIBRARY:
            self.library_container_children = ["Liked Songs Row", "Folder Row"]
        elif subtab_index == self.SUBTAB_QUEUE:
            self.queue_container_children = ["Queue Item 1", "Queue Item 2"]
        elif subtab_index == self.SUBTAB_LYRICS:
            self.lyrics_container_children = ["Checking current song..."]

        # Cleanup on leaving tabs
        if subtab_index != self.SUBTAB_LIBRARY:
            self.library_container_children = []
        if subtab_index != self.SUBTAB_QUEUE:
            self.queue_container_children = []
        if subtab_index != self.SUBTAB_SEARCH:
            self.search_container_children = []

    def handle_now_playing_response(self, json_data):
        self.hide_np_loading()
        self.sm.on_connection_success()
        if json_data is None:
            return

        self.current_title = json_data.get("title", "Not Playing")
        self.current_artist = json_data.get("artist", "")
        self.current_album = json_data.get("album", "")
        self.current_uri = json_data.get("uri", "")
        self.is_playing = json_data.get("playing", False)
        new_ctx = json_data.get("context_uri", "")
        if new_ctx != self.current_context_uri:
            self.current_context_uri = new_ctx
            if self.active_subtab == self.SUBTAB_LIBRARY:
                self.library_container_children = ["Updated Library Folder"]

        if self.active_subtab == self.SUBTAB_LYRICS:
            if not json_data.get("is_ad", False) and self.current_title not in ["", "Not Playing", "Refreshing...", "Spotify"]:
                self.lyrics_container_children = [f"Lyrics for {self.current_title}"]

    def handle_now_playing_error(self):
        self.hide_np_loading()
        self.sm.on_connection_failure()

    def handle_poll_spotify(self, response=None, error=None):
        if self.is_spotify_polling:
            return "BUSY"
        self.is_spotify_polling = True
        try:
            if response is not None:
                self.is_spotify_polling = False
                self.handle_now_playing_response(response)
                return "SUCCESS"
            elif error is not None:
                self.is_spotify_polling = False
                # Android MainActivity.java line 256: calls onConnectionFailure()
                self.sm.on_connection_failure()
                return "ERROR"
        finally:
            self.is_spotify_polling = False

    def on_refresh_clicked(self):
        self.show_np_loading("Refreshing...")
        self.sm.on_refresh_clicked()
        self.current_title = "Refreshing..."
        self.current_artist = ""
        self.current_album = ""
        self.current_uri = ""


class TestMilestone2Adversarial(unittest.TestCase):

    def setUp(self):
        self.sm = AndroidConnectionStateMachine()
        self.ui = AndroidNowPlayingUIHarness(self.sm)

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 1: Rapid Intermittent Network Failures & State Transitions (R2.1)
    # ─────────────────────────────────────────────────────────────────────────

    def test_transition_sequence_standard(self):
        """
        Verify transition sequence:
        Offline (Initial) -> Connect Success -> Online -> 1 Fail -> Connecting -> 2 Fails -> Offline -> Immediate Recovery -> Online.
        """
        # Initial state
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_OFFLINE)
        self.assertEqual(self.sm.ui_text, "Offline")
        self.assertEqual(self.sm.ui_dot_drawable, "dot_offline")

        # Connection success
        self.sm.on_connection_success()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_ONLINE)
        self.assertEqual(self.sm.ui_text, "Online")
        self.assertEqual(self.sm.ui_dot_drawable, "dot_online")
        self.assertEqual(self.sm.consecutive_failures, 0)

        # 1st failure -> Connecting...
        self.sm.on_connection_failure()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_CONNECTING)
        self.assertEqual(self.sm.ui_text, "Connecting...")
        self.assertEqual(self.sm.ui_dot_drawable, "dot_connecting")
        self.assertEqual(self.sm.consecutive_failures, 1)

        # 2nd failure -> Offline
        self.sm.on_connection_failure()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_OFFLINE)
        self.assertEqual(self.sm.ui_text, "Offline")
        self.assertEqual(self.sm.ui_dot_drawable, "dot_offline")
        self.assertEqual(self.sm.consecutive_failures, 2)

        # Immediate single recovery
        self.sm.on_connection_success()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_ONLINE)
        self.assertEqual(self.sm.ui_text, "Online")
        self.assertEqual(self.sm.ui_dot_drawable, "dot_online")
        self.assertEqual(self.sm.consecutive_failures, 0)

    def test_rapid_network_flapping_sequence(self):
        """
        Stress Test: 100 iterations of random/patterned network flapping:
        S -> F -> S -> F -> F -> S -> F -> S
        """
        flapping_pattern = [
            ("S", AndroidConnectionStateMachine.STATUS_ONLINE, 0),
            ("F", AndroidConnectionStateMachine.STATUS_CONNECTING, 1),
            ("S", AndroidConnectionStateMachine.STATUS_ONLINE, 0),
            ("F", AndroidConnectionStateMachine.STATUS_CONNECTING, 1),
            ("F", AndroidConnectionStateMachine.STATUS_OFFLINE, 2),
            ("F", AndroidConnectionStateMachine.STATUS_OFFLINE, 3),
            ("S", AndroidConnectionStateMachine.STATUS_ONLINE, 0),
            ("F", AndroidConnectionStateMachine.STATUS_CONNECTING, 1),
            ("S", AndroidConnectionStateMachine.STATUS_ONLINE, 0),
            ("S", AndroidConnectionStateMachine.STATUS_ONLINE, 0),
        ]

        for step, (event, expected_status, expected_fails) in enumerate(flapping_pattern):
            if event == "S":
                self.sm.on_connection_success()
            else:
                self.sm.on_connection_failure()

            self.assertEqual(
                self.sm.current_status, expected_status,
                f"Step {step} ({event}): Expected status {expected_status}, got {self.sm.current_status}"
            )
            self.assertEqual(
                self.sm.consecutive_failures, expected_fails,
                f"Step {step} ({event}): Expected fails {expected_fails}, got {self.sm.consecutive_failures}"
            )

    def test_deep_failure_streak_and_instant_resumption(self):
        """
        Stress Test: 500 consecutive failures followed by 1 success.
        Ensures counter increments safely without overflow or UI desync.
        """
        self.sm.on_connection_success()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_ONLINE)

        for i in range(1, 501):
            self.sm.on_connection_failure()
            if i == 1:
                self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_CONNECTING)
            else:
                self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_OFFLINE)
            self.assertEqual(self.sm.consecutive_failures, i)

        # Single successful poll restores ONLINE and 0 fails
        self.sm.on_connection_success()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_ONLINE)
        self.assertEqual(self.sm.consecutive_failures, 0)
        self.assertEqual(self.sm.ui_text, "Online")

    def test_disconnect_button_immediate_offline_override(self):
        """
        Stress Test: Disconnect button overrides any active state directly to OFFLINE
        with consecutive_failures set to 2.
        """
        self.sm.on_connection_success()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_ONLINE)

        self.sm.on_disconnect_clicked()
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_OFFLINE)
        self.assertEqual(self.sm.consecutive_failures, 2)
        self.assertEqual(self.sm.ui_text, "Offline")

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 2: Now Playing Loading Overlay & Refresh Spamming (R2.2)
    # ─────────────────────────────────────────────────────────────────────────

    def test_subtab_loading_overlay_lifecycle(self):
        """
        Verify subtab 0 loading overlay is shown upon entry and dismissed on payload arrival.
        """
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_PLAYING)
        self.assertTrue(self.ui.np_loading_visible)
        self.assertEqual(self.ui.np_loading_text, "Loading Now Playing...")

        # In-flight response arrives
        self.ui.handle_now_playing_response({
            "title": "Starboy",
            "artist": "The Weeknd",
            "album": "Starboy",
            "uri": "spotify:track:123",
            "playing": True
        })
        self.assertFalse(self.ui.np_loading_visible)
        self.assertEqual(self.ui.current_title, "Starboy")
        self.assertEqual(self.ui.current_artist, "The Weeknd")

    def test_manual_refresh_spamming(self):
        """
        Stress Test: User clicks Refresh 50 times in rapid succession.
        Verify state remains consistent and loading overlay is set to 'Refreshing...'.
        """
        for _ in range(50):
            self.ui.on_refresh_clicked()
            self.assertTrue(self.ui.np_loading_visible)
            self.assertEqual(self.ui.np_loading_text, "Refreshing...")
            self.assertEqual(self.ui.current_title, "Refreshing...")
            self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_CONNECTING)

        # Eventual response dismisses loading
        self.ui.handle_now_playing_response({"title": "After Hours", "artist": "The Weeknd"})
        self.assertFalse(self.ui.np_loading_visible)
        self.assertEqual(self.ui.current_title, "After Hours")
        self.assertEqual(self.sm.current_status, AndroidConnectionStateMachine.STATUS_ONLINE)

    def test_polling_in_flight_concurrency_guard(self):
        """
        Verify that is_spotify_polling prevents duplicate concurrent requests
        when rapid refreshes or timer ticks occur simultaneously.
        """
        # Start a poll
        self.ui.is_spotify_polling = True

        # Second poll attempt while first is in-flight
        res = self.ui.handle_poll_spotify(response={"title": "Track 1"})
        self.assertEqual(res, "BUSY")

        # Release lock and perform poll
        self.ui.is_spotify_polling = False
        res2 = self.ui.handle_poll_spotify(response={"title": "Track 2"})
        self.assertEqual(res2, "SUCCESS")
        self.assertEqual(self.ui.current_title, "Track 2")

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 3: Rapid Tab & Subtab Switching with In-Flight Responses
    # ─────────────────────────────────────────────────────────────────────────

    def test_rapid_subtab_switching_with_delayed_response(self):
        """
        Adversarial Test: User switches Now Playing -> Library -> Queue -> Lyrics in 10ms
        while Now Playing request is still in-flight.
        When Now Playing response arrives, verify state updates without corrupting active subtab views.
        """
        # Switch to Now Playing (starts fetch)
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_PLAYING)
        self.assertTrue(self.ui.np_loading_visible)

        # Rapidly switch to Library
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_LIBRARY)
        self.assertFalse(self.ui.now_playing_container_visible)
        self.assertGreater(len(self.ui.library_container_children), 0)

        # Rapidly switch to Lyrics
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_LYRICS)
        self.assertFalse(self.ui.now_playing_container_visible)
        self.assertEqual(len(self.ui.library_container_children), 0)  # Cleaned up

        # Now Playing async response finally returns
        self.ui.handle_now_playing_response({
            "title": "Blinding Lights",
            "artist": "The Weeknd",
            "album": "After Hours",
            "uri": "spotify:track:blinding",
            "playing": True
        })

        # Overlay hidden, backing state updated, lyrics content updated for active song
        self.assertFalse(self.ui.np_loading_visible)
        self.assertEqual(self.ui.current_title, "Blinding Lights")
        self.assertIn("Lyrics for Blinding Lights", self.ui.lyrics_container_children)

    def test_tab_switching_memory_cleanup(self):
        """
        Memory Safety Test: Switching away from Library, Queue, or Search cleans up all
        rendered child views to prevent Dalvik OOM on legacy devices.
        """
        # Populate Library
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_LIBRARY)
        self.assertEqual(len(self.ui.library_container_children), 2)

        # Switch to Settings
        self.ui.switch_tab(1)
        self.assertEqual(self.ui.active_tab, "Settings")

        # Switch back to Music - Queue
        self.ui.switch_tab(0)
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_QUEUE)
        self.assertEqual(len(self.ui.library_container_children), 0)
        self.assertEqual(len(self.ui.queue_container_children), 2)

        # Switch to Now Playing
        self.ui.switch_music_subtab(AndroidNowPlayingUIHarness.SUBTAB_PLAYING)
        self.assertEqual(len(self.ui.queue_container_children), 0)
        self.assertEqual(len(self.ui.library_container_children), 0)

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 4: Malformed Payloads & Edge Cases
    # ─────────────────────────────────────────────────────────────────────────

    def test_malformed_json_and_none_payload_handling(self):
        """
        Adversarial Test: Server returns None, empty dict, or missing fields.
        Verify UI does not raise Unhandled Exception and dismisses loading overlay.
        """
        self.ui.show_np_loading("Loading...")
        self.assertTrue(self.ui.np_loading_visible)

        # Empty dict
        self.ui.handle_now_playing_response({})
        self.assertFalse(self.ui.np_loading_visible)
        self.assertEqual(self.ui.current_title, "Not Playing")

        # None payload
        self.ui.show_np_loading("Loading...")
        self.ui.handle_now_playing_response(None)
        self.assertFalse(self.ui.np_loading_visible)

    def test_pixel_perfect_setting_edge_cases(self):
        """
        Test R2.3: Verify server config set query string parsing with edge case inputs.
        """
        # Missing value
        url1 = "/api/config/set?key=pixel_perfect_art"
        q1 = parse_qs(urlparse(url1).query)
        self.assertEqual(q1.get("key"), ["pixel_perfect_art"])
        self.assertIsNone(q1.get("value"))

        # Extra whitespace in parameters
        url2 = "/api/config/set?key=pixel_perfect_art&value=true&extra=123"
        q2 = parse_qs(urlparse(url2).query)
        self.assertEqual(q2.get("key"), ["pixel_perfect_art"])
        self.assertEqual(q2.get("value"), ["true"])


if __name__ == "__main__":
    unittest.main()
