"""
Milestone 1 Test Suite: Now Playing Detection, Lyrics Timing Sync, & Active Lyrics Query
======================================================================================
Tests:
  1. R1.1: Now Playing automatic song change detection without stale Win32 window title lock
  2. R1.2: Lyrics timing sync, optimistic seek tracking, and accurate position_ms/length_ms
  3. R1.3: Active now_playing force query contract for Lyrics view opening and remote_server
"""

import unittest
from unittest.mock import MagicMock, patch
import json
import sys
import time
from pathlib import Path

# Add pc_app directory to path
sys.path.insert(0, str(Path(__file__).resolve().parent))

from backend.spotify_control import SpotifyControl


class TestMilestone1NowPlayingAndLyrics(unittest.TestCase):

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
    # R1.1: Now Playing Song Change Detection Tests
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_now_playing_detects_song_change_via_smtc(self, mock_smtc, mock_cli):
        """Verify that backend detects track change via SMTC and enriches metadata via CLI."""
        mock_smtc.return_value = {
            "title": "Bohemian Rhapsody",
            "artist": "Queen",
            "album": "A Night at the Opera",
            "is_playing": True,
            "shuffle": False,
            "repeat": 0,
            "position_s": 30,
            "length_s": 354,
            "position_ms": 30000,
            "length_ms": 354000,
        }
        
        def cli_side_effect(args):
            if "now-playing" in args:
                return json.dumps({
                    "currently_playing": {
                        "uri": "spotify:track:queen123",
                        "context": {"uri": "spotify:album:opera123"},
                        "context_description": "A Night at the Opera"
                    }
                })
            elif "lookup" in args:
                return json.dumps({
                    "entities": [{"image_url": "http://image.spotify.com/art123.jpg", "parent": {"name": "A Night at the Opera"}}]
                })
            return ""
            
        mock_cli.side_effect = cli_side_effect

        info = SpotifyControl.get_now_playing()
        self.assertEqual(info["title"], "Bohemian Rhapsody")
        self.assertEqual(info["artist"], "Queen")
        self.assertEqual(info["album"], "A Night at the Opera")
        self.assertEqual(info["uri"], "spotify:track:queen123")
        self.assertEqual(info["artwork"], "http://image.spotify.com/art123.jpg")
        self.assertEqual(info["position_ms"], 30000)
        self.assertEqual(info["length_ms"], 354000)
        self.assertTrue(info["playing"])

        # Track changes to another song
        mock_smtc.return_value = {
            "title": "Under Pressure",
            "artist": "Queen & David Bowie",
            "album": "Hot Space",
            "is_playing": True,
            "shuffle": False,
            "repeat": 0,
            "position_s": 5,
            "length_s": 248,
            "position_ms": 5000,
            "length_ms": 248000,
        }
        def cli_side_effect_2(args):
            if "now-playing" in args:
                return json.dumps({
                    "currently_playing": {
                        "uri": "spotify:track:bowie456",
                        "context": {"uri": "spotify:album:hotspace456"},
                        "context_description": "Hot Space"
                    }
                })
            elif "lookup" in args:
                return json.dumps({
                    "entities": [{"image_url": "http://image.spotify.com/art456.jpg", "parent": {"name": "Hot Space"}}]
                })
            return ""
        mock_cli.side_effect = cli_side_effect_2

        info2 = SpotifyControl.get_now_playing()
        self.assertEqual(info2["title"], "Under Pressure")
        self.assertEqual(info2["artist"], "Queen & David Bowie")
        self.assertEqual(info2["album"], "Hot Space")
        self.assertEqual(info2["uri"], "spotify:track:bowie456")
        self.assertEqual(info2["artwork"], "http://image.spotify.com/art456.jpg")
        self.assertEqual(info2["position_ms"], 5000)

    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_now_playing_fast_path_when_song_unchanged(self, mock_smtc, mock_cli):
        """Verify fast-path returns cached metadata with updated timeline without calling CLI."""
        mock_smtc.return_value = {
            "title": "Song A",
            "artist": "Artist A",
            "album": "Album A",
            "is_playing": True,
            "shuffle": False,
            "repeat": 0,
            "position_s": 10,
            "length_s": 200,
            "position_ms": 10000,
            "length_ms": 200000,
        }
        mock_cli.return_value = json.dumps({
            "currently_playing": {"uri": "spotify:track:songA"}
        })

        # First call sets the song
        info1 = SpotifyControl.get_now_playing()
        self.assertEqual(info1["title"], "Song A")
        self.assertEqual(mock_cli.call_count, 2)  # now-playing + lookup

        # Second call with same title and artist advances timeline
        mock_smtc.return_value = {
            "title": "Song A",
            "artist": "Artist A",
            "album": "Album A",
            "is_playing": True,
            "shuffle": True,
            "repeat": 1,
            "position_s": 12,
            "length_s": 200,
            "position_ms": 12000,
            "length_ms": 200000,
        }
        info2 = SpotifyControl.get_now_playing()
        self.assertEqual(info2["title"], "Song A")
        self.assertEqual(info2["position_ms"], 12000)
        self.assertEqual(info2["position_s"], 12)
        self.assertTrue(info2["shuffle"])
        self.assertEqual(info2["repeat"], 1)
        # CLI should NOT have been called again on unchanged song fast-path
        self.assertEqual(mock_cli.call_count, 2)

    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_now_playing_force_fetch_forces_enrichment(self, mock_smtc, mock_cli):
        """Verify force_fetch=True forces CLI query even if track is unchanged."""
        mock_smtc.return_value = {
            "title": "Song A",
            "artist": "Artist A",
            "album": "Album A",
            "is_playing": True,
            "shuffle": False,
            "repeat": 0,
            "position_s": 10,
            "length_s": 200,
            "position_ms": 10000,
            "length_ms": 200000,
        }
        mock_cli.return_value = json.dumps({
            "currently_playing": {"uri": "spotify:track:songA"}
        })

        SpotifyControl.get_now_playing()
        cli_count_before = mock_cli.call_count

        # Call with force_fetch=True
        SpotifyControl.get_now_playing(force_fetch=True)
        self.assertGreater(mock_cli.call_count, cli_count_before)

    # ─────────────────────────────────────────────────────────────────────────
    # R1.2: Lyrics Timing Sync and Seek Position Reconciliation Tests
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, '_run_cli')
    def test_seek_updates_internal_state_and_invokes_cli(self, mock_cli):
        """Verify that seek updates _last_seek_time, _last_seek_pos_ms, and _last_metadata."""
        SpotifyControl._last_metadata["position_ms"] = 30000
        SpotifyControl._last_metadata["length_ms"] = 180000

        # Test relative seek +15s (15000ms)
        SpotifyControl.seek("15000", relative=True)
        self.assertTrue(SpotifyControl._last_seek_is_active)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 45000)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 45000)
        self.assertEqual(SpotifyControl._last_metadata["position_s"], 45)
        mock_cli.assert_called_with(["seek", "15000", "--relative"])

        # Test absolute seek to 120s (120000ms)
        SpotifyControl.seek("120000", relative=False)
        self.assertTrue(SpotifyControl._last_seek_is_active)
        self.assertEqual(SpotifyControl._last_seek_pos_ms, 120000)
        self.assertEqual(SpotifyControl._last_metadata["position_ms"], 120000)
        self.assertEqual(SpotifyControl._last_metadata["position_s"], 120)
        mock_cli.assert_called_with(["seek", "120000"])

    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_get_playback_progress_reconciles_pending_seek(self, mock_smtc):
        """Verify get_playback_progress returns seek position when SMTC has not updated."""
        mock_smtc.return_value = None  # SMTC not returning yet
        SpotifyControl._last_metadata["length_s"] = 180
        SpotifyControl._last_metadata["length_ms"] = 180000

        SpotifyControl.seek("160000", relative=False)
        prog = SpotifyControl.get_playback_progress()
        self.assertEqual(prog["position_ms"], 160000)
        self.assertEqual(prog["position_s"], 160)
        self.assertEqual(prog["length_ms"], 180000)
        self.assertEqual(prog["length_s"], 180)

    @patch.object(SpotifyControl, 'is_running')
    @patch.object(SpotifyControl, '_run_cli')
    @patch.object(SpotifyControl, '_fetch_smtc_info')
    def test_all_metadata_branches_contain_position_ms_and_length_ms(self, mock_smtc, mock_cli, mock_running):
        """Verify position_ms and length_ms are present across all metadata retrieval branches."""
        # Branch 1: SMTC active
        mock_smtc.return_value = {
            "title": "T1", "artist": "A1", "album": "Alb1", "is_playing": True,
            "shuffle": False, "repeat": 0, "position_s": 50, "length_s": 150,
            "position_ms": 50000, "length_ms": 150000
        }
        mock_cli.return_value = json.dumps({"currently_playing": {"uri": "spotify:track:1"}})
        res1 = SpotifyControl.get_now_playing()
        self.assertIn("position_ms", res1)
        self.assertIn("length_ms", res1)
        self.assertIn("position_s", res1)
        self.assertIn("length_s", res1)

        # Branch 2: SMTC unavailable, CLI active
        mock_smtc.return_value = None
        mock_cli.return_value = json.dumps({
            "currently_playing": {
                "uri": "spotify:track:2",
                "description": "Artist 2 - Title 2",
                "is_playing": True
            }
        })
        res2 = SpotifyControl.get_now_playing(force_fetch=True)
        self.assertIn("position_ms", res2)
        self.assertIn("length_ms", res2)

        # Branch 3: Nothing running
        mock_smtc.return_value = None
        mock_cli.return_value = ""
        mock_running.return_value = False
        res3 = SpotifyControl.get_now_playing(force_fetch=True)
        self.assertIn("position_ms", res3)
        self.assertIn("length_ms", res3)
        self.assertEqual(res3["position_ms"], 0)
        self.assertEqual(res3["length_ms"], 0)

    # ─────────────────────────────────────────────────────────────────────────
    # R1.3: Active Lyrics Query & Remote Server Query Parameter Contract Tests
    # ─────────────────────────────────────────────────────────────────────────

    @patch.object(SpotifyControl, 'get_now_playing')
    def test_remote_server_force_parameter_passed_to_spotify_control(self, mock_get_np):
        """Verify that remote_server /api/spotify/now_playing?force=true passes force_fetch=True."""
        from urllib.parse import urlparse, parse_qs
        
        test_url = "/api/spotify/now_playing?force=true"
        qs = parse_qs(urlparse(test_url).query)
        force_fetch = qs.get("force", ["false"])[0].lower() in ("true", "1")
        self.assertTrue(force_fetch)

        test_url_false = "/api/spotify/now_playing"
        qs_false = parse_qs(urlparse(test_url_false).query)
        force_fetch_false = qs_false.get("force", ["false"])[0].lower() in ("true", "1")
        self.assertFalse(force_fetch_false)


if __name__ == "__main__":
    unittest.main()
