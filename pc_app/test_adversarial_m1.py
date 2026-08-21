"""
Milestone 1 Adversarial Stress Test Suite
=========================================
Adversarial challenge tests covering:
  1. Rapid consecutive seeks (e.g. 5 seeks in rapid succession) & reconciliation state
  2. Out-of-bounds seeks (negative offsets, beyond track duration, zero track duration, malformed inputs)
  3. SMTC session switching, multi-session prioritization, and fallback behavior (Spotify minimized vs closed)
  4. Timeline extrapolation precision, bounds clamping, paused state stability, and clock skew resistance
"""

import unittest
from unittest.mock import MagicMock, patch
import json
import sys
import time
from datetime import datetime, timezone, timedelta
from pathlib import Path

# Add pc_app directory to path
sys.path.insert(0, str(Path(__file__).resolve().parent))

from backend.spotify_control import SpotifyControl


class TestMilestone1Adversarial(unittest.TestCase):

    def setUp(self):
        # Reset SpotifyControl internal state before each test
        SpotifyControl._last_title = ""
        SpotifyControl._last_artist = ""
        SpotifyControl._last_album = ""
        SpotifyControl._last_uri = ""
        SpotifyControl._last_artwork_url = ""
        SpotifyControl._last_context_uri = ""
        SpotifyControl._last_context_desc = ""
        SpotifyControl._last_seek_time = 0.0
        SpotifyControl._last_seek_pos_ms = 0
        SpotifyControl._last_seek_is_active = False
        SpotifyControl._last_metadata = {
            "playing": False, "artist": "", "title": "Spotify", "album": "",
            "uri": "", "artwork": "", "position_s": 0, "length_s": 0,
            "position_ms": 0, "length_ms": 0, "shuffle": False, "repeat": 0,
            "context": "", "context_uri": "", "is_ad": False
        }

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 1: Rapid Consecutive Seeks & Reconciliation Dynamics
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, '_run_cli')
    def test_rapid_consecutive_relative_seeks(self, mock_cli):
        """Stress Test: 5 consecutive relative seeks (+15s each) in rapid sequence."""
        SpotifyControl._last_metadata["position_ms"] = 10000
        SpotifyControl._last_metadata["length_ms"] = 300000

        for i in range(5):
            SpotifyControl.seek("15000", relative=True)
            expected_pos = 10000 + (i + 1) * 15000
            self.assertTrue(SpotifyControl._last_seek_is_active)
            self.assertEqual(SpotifyControl._last_seek_pos_ms, expected_pos)
            self.assertEqual(SpotifyControl._last_metadata["position_ms"], expected_pos)
            self.assertEqual(SpotifyControl._last_metadata["position_s"], int(expected_pos / 1000))

        # Final position after 5 x +15s seeks should be 85000ms (85s)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 85000)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 85000)
        self.assertEqual(mock_cli.call_count, 5)

    @patch.object(SpotifyControl, '_run_cli')
    def test_rapid_alternating_relative_and_absolute_seeks(self, mock_cli):
        """Stress Test: Rapid mix of absolute and relative seeks."""
        SpotifyControl._last_metadata["position_ms"] = 0
        SpotifyControl._last_metadata["length_ms"] = 200000

        # Jump to 100s
        SpotifyControl.seek("100000", relative=False)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 100000)

        # Relative back 30s -> 70s
        SpotifyControl.seek("-30000", relative=True)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 70000)

        # Relative forward 15s -> 85s
        SpotifyControl.seek("15000", relative=True)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 85000)

        # Absolute jump to 10s
        SpotifyControl.seek("10000", relative=False)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 10000)

        # Relative back 15s -> clamped to 0
        SpotifyControl.seek("-15000", relative=True)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 0)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 0)

    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_seek_reconciliation_window_expiration(self, mock_smtc):
        """Verify that seek reconciliation expires cleanly after 2.5 seconds."""
        mock_smtc.return_value = None
        SpotifyControl._last_metadata["length_ms"] = 180000

        # Seek to 60s
        SpotifyControl.seek("60000", relative=False)
        self.assertTrue(SpotifyControl._last_seek_is_active)

        # Immediately check playback progress -> returns 60000
        prog = SpotifyControl.get_playback_progress()
        self.assertEqual(prog["position_ms"], 60000)

        # Manually age the seek beyond 2.5s window
        SpotifyControl._last_seek_time = time.time() - 3.0
        # If SMTC is not available and seek expired, fallback to last metadata
        SpotifyControl._last_metadata["position_ms"] = 5000  # Stale metadata
        prog_expired = SpotifyControl.get_playback_progress()
        self.assertEqual(prog_expired["position_ms"], 5000)

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 2: Out-of-Bounds & Negative Seeks
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, '_run_cli')
    def test_seek_clamping_negative_relative_beyond_zero(self, mock_cli):
        """Boundary Test: Negative relative seek exceeding current position clamps to 0."""
        SpotifyControl._last_metadata["position_ms"] = 5000
        SpotifyControl._last_metadata["length_ms"] = 180000

        SpotifyControl.seek("-20000", relative=True)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 0)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 0)
        self.assertEqual(SpotifyControl._last_metadata["position_s"], 0)

    @patch.object(SpotifyControl, '_run_cli')
    def test_seek_clamping_negative_absolute(self, mock_cli):
        """Boundary Test: Negative absolute seek clamps to 0."""
        SpotifyControl._last_metadata["position_ms"] = 50000
        SpotifyControl._last_metadata["length_ms"] = 180000

        SpotifyControl.seek("-10000", relative=False)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 0)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 0)

    @patch.object(SpotifyControl, '_run_cli')
    def test_seek_clamping_exceeding_track_duration(self, mock_cli):
        """Boundary Test: Seek beyond total length clamps to length_ms."""
        SpotifyControl._last_metadata["position_ms"] = 170000
        SpotifyControl._last_metadata["length_ms"] = 180000

        SpotifyControl.seek("30000", relative=True)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 180000)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 180000)
        self.assertEqual(SpotifyControl._last_metadata["position_s"], 180)

        # Absolute seek beyond length
        SpotifyControl.seek("999999", relative=False)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 180000)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 180000)

    @patch.object(SpotifyControl, '_run_cli')
    def test_seek_when_length_is_zero(self, mock_cli):
        """Boundary Test: Seek when track length is unknown (0ms) does not crash or divide by zero."""
        SpotifyControl._last_metadata["position_ms"] = 0
        SpotifyControl._last_metadata["length_ms"] = 0

        SpotifyControl.seek("15000", relative=True)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 15000)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 15000)
        self.assertEqual(SpotifyControl._last_metadata["position_s"], 15)

    @patch.object(SpotifyControl, '_run_cli')
    def test_seek_malformed_string_input(self, mock_cli):
        """Adversarial Test: Non-numeric strings in seek do not raise unhandled exceptions."""
        SpotifyControl._last_metadata["position_ms"] = 10000
        SpotifyControl._last_metadata["length_ms"] = 180000

        # Should safely catch exception and pass command to CLI without throwing
        try:
            SpotifyControl.seek("invalid_ms", relative=True)
        except Exception as e:
            self.fail(f"SpotifyControl.seek raised unexpected exception on invalid string: {e}")

        # Position should remain untouched
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 10000)

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 3: SMTC Session Switching & Fallback Logic
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, 'is_running')
    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_smtc_fallback_when_spotify_closed(self, mock_smtc, mock_cli, mock_running):
        """Verify clean fallback state when Spotify is closed completely."""
        mock_smtc.return_value = None
        mock_cli.return_value = ""
        mock_running.return_value = False

        res = SpotifyControl.get_now_playing(force_fetch=True)
        self.assertFalse(res["playing"])
        self.assertEqual(res["title"], "Not Playing")
        self.assertEqual(res["artist"], "")
        self.assertEqual(res["position_ms"], 0)
        self.assertEqual(res["length_ms"], 0)

    @patch.object(SpotifyControl, 'is_running')
    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_smtc_fallback_when_spotify_running_but_no_cli_output(self, mock_smtc, mock_cli, mock_running):
        """Verify fallback when Spotify process exists but CLI is temporarily busy."""
        mock_smtc.return_value = None
        mock_cli.return_value = ""
        mock_running.return_value = True

        SpotifyControl._last_metadata = {
            "playing": True, "artist": "Artist X", "title": "Song X", "album": "Album X",
            "uri": "spotify:track:x", "artwork": "", "position_s": 42, "length_s": 200,
            "position_ms": 42000, "length_ms": 200000, "shuffle": False, "repeat": 0,
            "context": "", "context_uri": "", "is_ad": False
        }

        res = SpotifyControl.get_now_playing(force_fetch=True)
        self.assertEqual(res["title"], "Song X")
        self.assertEqual(res["position_ms"], 42000)

    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_advertisement_track_detection_and_flagging(self, mock_smtc, mock_cli):
        """Verify advertisement detection sets is_ad=True and formats titles properly."""
        mock_smtc.return_value = {
            "title": "Spotify Ad",
            "artist": "",
            "album": "",
            "is_playing": True,
            "shuffle": False,
            "repeat": 0,
            "position_s": 5,
            "length_s": 30,
            "position_ms": 5000,
            "length_ms": 30000,
        }
        mock_cli.return_value = json.dumps({
            "currently_playing": {"uri": "spotify:ad:commercial123"}
        })

        info = SpotifyControl.get_now_playing()
        self.assertTrue(info["is_ad"])
        self.assertEqual(info["uri"], "spotify:ad:commercial123")

    # ─────────────────────────────────────────────────────────────────────────
    # Dimension 4: Extrapolation Precision & Bounds
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_extrapolation_paused_state_stability(self, mock_smtc, mock_cli):
        """Verify position does not drift when playback is paused."""
        mock_smtc.return_value = {
            "title": "Paused Track",
            "artist": "Paused Artist",
            "album": "Paused Album",
            "is_playing": False,
            "shuffle": False,
            "repeat": 0,
            "position_s": 50,
            "length_s": 200,
            "position_ms": 50000,
            "length_ms": 200000,
        }
        mock_cli.return_value = json.dumps({"currently_playing": {"uri": "spotify:track:paused"}})

        # Initial call
        res1 = SpotifyControl.get_now_playing()
        self.assertFalse(res1["playing"])
        self.assertEqual(res1["position_ms"], 50000)

        # Subsequent fast-path call with same paused SMTC position
        res2 = SpotifyControl.get_now_playing()
        self.assertFalse(res2["playing"])
        self.assertEqual(res2["position_ms"], 50000)

    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_extrapolation_active_playback_progress(self, mock_smtc, mock_cli):
        """Verify position advances smoothly during active playback."""
        mock_smtc.return_value = {
            "title": "Active Track",
            "artist": "Active Artist",
            "album": "Active Album",
            "is_playing": True,
            "shuffle": False,
            "repeat": 0,
            "position_s": 10,
            "length_s": 200,
            "position_ms": 10000,
            "length_ms": 200000,
        }
        mock_cli.return_value = json.dumps({"currently_playing": {"uri": "spotify:track:active"}})

        res1 = SpotifyControl.get_now_playing()
        self.assertTrue(res1["playing"])
        self.assertEqual(res1["position_ms"], 10000)

        # Simulate 2 seconds later via SMTC update
        mock_smtc.return_value["position_s"] = 12
        mock_smtc.return_value["position_ms"] = 12000

        res2 = SpotifyControl.get_now_playing()
        self.assertTrue(res2["playing"])
        self.assertEqual(res2["position_ms"], 12000)
        self.assertEqual(res2["position_s"], 12)


if __name__ == "__main__":
    unittest.main()
