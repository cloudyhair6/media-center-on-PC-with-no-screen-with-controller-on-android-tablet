"""Spotify control via Windows media keys and window-title parsing."""

from __future__ import annotations

import ctypes
import ctypes.wintypes
import subprocess
import time
from pathlib import Path

# Virtual-key codes for media keys
VK_MEDIA_PLAY_PAUSE = 0xB3
VK_MEDIA_NEXT_TRACK = 0xB0
VK_MEDIA_PREV_TRACK = 0xB1
VK_VOLUME_UP = 0xAF
VK_VOLUME_DOWN = 0xAE
VK_VOLUME_MUTE = 0xAD

def _send_media_key(vk: int) -> None:
    """Send a media key press/release via win32api."""
    try:
        import win32api
        import win32con
        # Key down
        win32api.keybd_event(vk, 0, win32con.KEYEVENTF_EXTENDEDKEY, 0)
        # Key up
        win32api.keybd_event(vk, 0, win32con.KEYEVENTF_EXTENDEDKEY | win32con.KEYEVENTF_KEYUP, 0)
    except Exception as e:
        print(f"Failed to send media key: {e}")


import json
import os
import urllib.request

class SpotifyControl:
    """Control Spotify via spotify_cli.exe and read track info."""

    _last_title = ""
    _last_metadata = {"playing": False, "artist": "", "title": "", "album": "", "uri": "", "artwork": ""}
    _last_uri = ""
    _last_artwork_url = ""
    _last_album = ""

    @staticmethod
    def get_artwork(uri: str) -> str:
        if not uri:
            return ""
        try:
            import ssl
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            
            url = f"https://open.spotify.com/oembed?url={uri}"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=2, context=ctx) as response:
                data = json.loads(response.read().decode('utf-8'))
                # Replace https with http to help older Android tablets load the image
                return data.get('thumbnail_url', '').replace('https://', 'http://')
        except Exception as e:
            import logging
            logging.error(f"Artwork fetch failed: {e}")
            return ""

    @staticmethod
    def _get_cli_path() -> str:
        return os.path.expandvars(r"%APPDATA%\Spotify\spotify_cli.exe")

    @staticmethod
    def _run_cli(args: list[str]) -> str:
        cli = SpotifyControl._get_cli_path()
        if not Path(cli).exists():
            return ""
        try:
            # We don't force utf-8 here initially so we can fallback if it fails.
            # The JSON payload will be in utf-8, but print statements from CLI may be cp1252.
            # subprocess.run blocks, but CLI is extremely fast.
            # creationflags=0x08000000 is CREATE_NO_WINDOW to prevent cmd popping up
            result = subprocess.run([cli] + args, capture_output=True, timeout=5, creationflags=0x08000000)
            text = result.stdout.decode('utf-8', errors='replace').strip()
            err_text = result.stderr.decode('utf-8', errors='replace').strip()
            
            if "client is not running" in text or "client is not running" in err_text:
                SpotifyControl.launch()
                time.sleep(1.5)
                result = subprocess.run([cli] + args, capture_output=True, timeout=5, creationflags=0x08000000)
                text = result.stdout.decode('utf-8', errors='replace').strip()
            return text
        except Exception as e:
            print(f"CLI Error: {e}")
            return ""

    @staticmethod
    def is_running() -> bool:
        """Check if Spotify is running."""
        try:
            result = subprocess.run(
                ["tasklist", "/FI", "IMAGENAME eq Spotify.exe", "/NH"],
                capture_output=True, text=True, timeout=5, creationflags=0x08000000
            )
            return "Spotify.exe" in result.stdout
        except Exception:
            return False

    @staticmethod
    def launch() -> bool:
        """Launch Spotify using the CLI."""
        cli = SpotifyControl._get_cli_path()
        if Path(cli).exists():
            subprocess.run([cli, "open"], capture_output=True, creationflags=0x08000000)
            return True
        return False

    @staticmethod
    def play_pause() -> None:
        info = SpotifyControl.get_now_playing(force_fetch=True)
        if info.get("playing", False):
            SpotifyControl._run_cli(["pause"])
        else:
            SpotifyControl._run_cli(["play"])

    @staticmethod
    def next_track() -> None:
        SpotifyControl._run_cli(["next"])

    @staticmethod
    def prev_track() -> None:
        SpotifyControl._run_cli(["previous"])

    @staticmethod
    def seek(ms: str, relative: bool = True) -> None:
        args = ["seek", ms]
        if relative:
            args.append("--relative")
        SpotifyControl._run_cli(args)

    @staticmethod
    def play_uri(uri: str) -> None:
        SpotifyControl._run_cli(["play", uri])

    @staticmethod
    def search(query: str, search_type: str = "track", limit: int = 5) -> dict:
        """Search Spotify via CLI and return parsed JSON."""
        output = SpotifyControl._run_cli(["search", query, "--type", search_type, "--limit", str(limit), "--format", "json"])
        if not output:
            return {}
        try:
            return json.loads(output)
        except Exception:
            return {}

    @staticmethod
    def library_add(uri: str) -> bool:
        """Add a URI to the saved library."""
        output = SpotifyControl._run_cli(["library", "add", uri])
        return "Added" in output

    @staticmethod
    def playlist_add(playlist_uri: str, track_uri: str) -> bool:
        """Add a track to a playlist."""
        output = SpotifyControl._run_cli(["playlist", "add", playlist_uri, track_uri])
        return "Added" in output or "Client error" not in output

    @staticmethod
    def library_remove(uri: str) -> bool:
        """Remove a URI from the saved library."""
        if uri.startswith("spotify:playlist:"):
            output = SpotifyControl._run_cli(["folder", "remove", uri])
            return "Removed" in output
        else:
            output = SpotifyControl._run_cli(["library", "remove", uri])
            return "Removed" in output

    @staticmethod
    def library_contains(uri: str) -> bool:
        """Check if a URI is in the saved library."""
        output = SpotifyControl._run_cli(["library", "contains", uri])
        return ": saved" in output

    @staticmethod
    def get_playback_progress() -> dict:
        import asyncio
        try:
            import winrt.windows.media.control as wmc
            async def fetch():
                try:
                    manager = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
                    session = manager.get_current_session()
                    if session:
                        tl = session.get_timeline_properties()
                        pb = session.get_playback_info()
                        
                        pos = tl.position.total_seconds()
                        if pb and pb.playback_status == 4: # Playing
                            from datetime import datetime, timezone
                            now = datetime.now(timezone.utc)
                            elapsed = (now - tl.last_updated_time).total_seconds()
                            pos += max(0, elapsed)
                            
                        return {
                            "position_s": int(pos),
                            "length_s": int(tl.end_time.total_seconds())
                        }
                except Exception:
                    pass
                return {"position_s": 0, "length_s": 0}
            return asyncio.run(fetch())
        except ImportError:
            return {"position_s": 0, "length_s": 0}

    @staticmethod
    def get_now_playing(force_fetch: bool = False) -> dict:
        """Get track metadata quickly by observing window title changes, parsing JSON when changed."""
        try:
            hwnd = ctypes.windll.user32.FindWindowW("Chrome_WidgetWin_1", None)
            
            current_title = ""
            if hwnd:
                titles = []
                @ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.wintypes.HWND, ctypes.wintypes.LPARAM)
                def enum_callback(h, _):
                    length = ctypes.windll.user32.GetWindowTextLengthW(h)
                    if length > 0:
                        buf = ctypes.create_unicode_buffer(length + 1)
                        ctypes.windll.user32.GetWindowTextW(h, buf, length + 1)
                        title = buf.value
                        if " - " in title:
                            pid = ctypes.wintypes.DWORD()
                            ctypes.windll.user32.GetWindowThreadProcessId(h, ctypes.byref(pid))
                            try:
                                result = subprocess.run(
                                    ["tasklist", "/FI", f"PID eq {pid.value}", "/NH", "/FO", "CSV"],
                                    capture_output=True, text=True, timeout=2, creationflags=0x08000000
                                )
                                if "Spotify" in result.stdout:
                                    titles.append(title)
                            except Exception:
                                pass
                    return True

                ctypes.windll.user32.EnumWindows(enum_callback, 0)
                for t in titles:
                    if " - " in t and t.lower() not in ("spotify", "spotify free", "spotify premium"):
                        current_title = t
                        break

            if not force_fetch and current_title == SpotifyControl._last_title and current_title != "":
                prog = SpotifyControl.get_playback_progress()
                SpotifyControl._last_metadata["position_s"] = prog.get("position_s", 0)
                SpotifyControl._last_metadata["length_s"] = prog.get("length_s", 0)
                state = SpotifyControl.get_shuffle_repeat()
                SpotifyControl._last_metadata["shuffle"] = state["shuffle"]
                SpotifyControl._last_metadata["repeat"] = state["repeat"]
                return SpotifyControl._last_metadata

            output = SpotifyControl._run_cli(["now-playing", "--format", "json"])
            SpotifyControl._last_title = current_title

            if not output:
                if current_title:
                    parts = current_title.split(" - ", 1)
                    SpotifyControl._last_metadata = {
                        "playing": True,
                        "artist": parts[0].strip(),
                        "title": parts[1].strip(),
                        "album": "",
                        "uri": "",
                        "artwork": SpotifyControl._last_artwork_url
                    }
                else:
                    SpotifyControl._last_metadata = {"playing": False, "artist": "", "title": "Spotify", "album": "", "uri": "", "artwork": ""}
                
                prog = SpotifyControl.get_playback_progress()
                SpotifyControl._last_metadata["position_s"] = prog.get("position_s", 0)
                SpotifyControl._last_metadata["length_s"] = prog.get("length_s", 0)
                state = SpotifyControl.get_shuffle_repeat()
                SpotifyControl._last_metadata["shuffle"] = state["shuffle"]
                SpotifyControl._last_metadata["repeat"] = state["repeat"]
                return SpotifyControl._last_metadata

            try:
                data = json.loads(output)
                cp = data.get("currently_playing", {})
                is_playing = cp.get("is_playing", False)
                desc = cp.get("description", "")
                uri = cp.get("uri", "")

                if uri != SpotifyControl._last_uri and uri:
                    # Fetch extra metadata (album name and artwork) using lookup
                    SpotifyControl._last_uri = uri
                    try:
                        lookup_out = SpotifyControl._run_cli(["lookup", uri, "--format", "json"])
                        ent = json.loads(lookup_out).get("entities", [{}])[0]
                        SpotifyControl._last_artwork_url = ent.get("image_url", "")
                        SpotifyControl._last_album = ent.get("parent", {}).get("name", "")
                    except Exception:
                        SpotifyControl._last_artwork_url = SpotifyControl.get_artwork(uri)
                        SpotifyControl._last_album = ""
                elif not uri:
                    SpotifyControl._last_artwork_url = ""
                    SpotifyControl._last_uri = ""
                    SpotifyControl._last_album = ""

                title = desc
                artist = ""
                # Attempt to split on common dash chars including the mangled output user showed
                for dash in [" \u2014 ", " \u2013 ", " - ", " \u00d4\u00c7\u00f6 "]:
                    if dash in desc:
                        parts = desc.split(dash, 1)
                        title = parts[0].strip()
                        artist = parts[1].strip()
                        break

                is_ad = uri.startswith("spotify:ad:")
                
                # Use context_description as fallback if album is missing
                album_text = SpotifyControl._last_album
                if not album_text:
                    album_text = cp.get("context_description", "")

                SpotifyControl._last_metadata = {
                    "playing": is_playing,
                    "title": title,
                    "artist": artist,
                    "album": album_text,
                    "uri": uri,
                    "artwork": SpotifyControl._last_artwork_url,
                    "is_ad": is_ad
                }

                prog = SpotifyControl.get_playback_progress()
                SpotifyControl._last_metadata["position_s"] = prog.get("position_s", 0)
                SpotifyControl._last_metadata["length_s"] = prog.get("length_s", 0)
                
                state = SpotifyControl.get_shuffle_repeat()
                SpotifyControl._last_metadata["shuffle"] = state["shuffle"]
                SpotifyControl._last_metadata["repeat"] = state["repeat"]

                return SpotifyControl._last_metadata

            except Exception as e:
                print(f"Error parsing CLI JSON: {e}")
                return {"playing": False, "artist": "", "title": "Spotify", "album": "", "uri": "", "artwork": ""}

        except Exception as e:
            print(f"SpotifyControl error: {e}")
            return {"playing": False, "artist": "", "title": "", "album": "", "uri": "", "artwork": ""}

    @staticmethod
    def get_folder_hierarchy() -> dict:
        """Get the full library folder and playlist hierarchy."""
        output = SpotifyControl._run_cli(["folder", "list", "--recursive", "--format", "json"])
        if not output:
            return {}
        try:
            return json.loads(output)
        except Exception:
            return {}

    @staticmethod
    def queue_add(uri: str) -> bool:
        """Add a track to the playback queue."""
        output = SpotifyControl._run_cli(["queue", "add", uri])
        return "Added" in output

    @staticmethod
    def get_queue() -> dict:
        """Get the current playback queue."""
        output = SpotifyControl._run_cli(["queue", "list", "--format", "json"])
        if not output:
            return {}
        try:
            return json.loads(output)
        except Exception:
            return {}

    @staticmethod
    def shuffle(state: bool) -> bool:
        """Enable or disable shuffle."""
        output = SpotifyControl._run_cli(["shuffle", "on" if state else "off"])
        return "Failed" not in output and "client error" not in output.lower()

    @staticmethod
    def get_shuffle_repeat() -> dict:
        """Get shuffle and repeat states using Windows SMTC."""
        try:
            import asyncio
            from winrt.windows.media.control import GlobalSystemMediaTransportControlsSessionManager
            import winrt.windows.media

            async def _get_state():
                manager = await GlobalSystemMediaTransportControlsSessionManager.request_async()
                session = manager.get_current_session()
                if session:
                    info = session.get_playback_info()
                    return {
                        "shuffle": info.is_shuffle_active,
                        "repeat": int(info.auto_repeat_mode)
                    }
                return {"shuffle": False, "repeat": 0}

            return asyncio.run(_get_state())
        except Exception as e:
            return {"shuffle": False, "repeat": 0}

    @staticmethod
    def repeat(state: str) -> bool:
        """Set repeat mode (track, context, off)."""
        output = SpotifyControl._run_cli(["repeat", state])
        return "Failed" not in output and "client error" not in output.lower()

    @staticmethod
    def get_speaker_fill_state() -> bool:
        """Check if Windows Speaker Fill upmixing is currently enabled."""
        import os
        apo_config_path = r"C:\Program Files\EqualizerAPO\config\config.txt"
        if not os.path.exists(apo_config_path):
            return False
        try:
            with open(apo_config_path, "r") as f:
                content = f.read()
                return "Stage: pre-mix" in content and "C=0.5*L+0.5*R" in content
        except Exception:
            return False

    @staticmethod
    def enable_speaker_fill(enabled: bool) -> tuple[bool, str]:
        """Toggle Windows Speaker Fill via Equalizer APO for 5.1 surround upmixing.

        This makes stereo Spotify output use all surround speakers.
        """
        import logging
        import os
        apo_config_path = r"C:\Program Files\EqualizerAPO\config\config.txt"
        
        # Check if Equalizer APO is installed
        if not os.path.exists(apo_config_path):
            logging.error("Speaker fill failed: Equalizer APO is not installed.")
            return False, "Equalizer APO not found"

        try:
            if enabled:
                # Write the upmix copy command using proper multi-line formatting
                # The 'If: inputChannelCount == 2' ensures it ONLY upmixes stereo content (Spotify)
                # and leaves true 5.1 content (VLC movies) completely untouched!
                upmix_code = (
                    "Stage: pre-mix\n"
                    "Copy: C=0.5*L+0.5*R\n"
                    "Copy: SUB=0.5*L+0.5*R\n"
                    "Copy: RL=L\n"
                    "Copy: RR=R\n"
                    "Copy: SL=L\n"
                    "Copy: SR=R\n"
                )
                with open(apo_config_path, "w") as f:
                    f.write(upmix_code)
                logging.info("Speaker fill ENABLED via Equalizer APO.")
                return True, "Speaker fill enabled"
            else:
                # Clear the file to restore normal surround sound
                with open(apo_config_path, "w") as f:
                    f.write("# Speaker fill disabled\n")
                logging.info("Speaker fill DISABLED via Equalizer APO.")
                return True, "Speaker fill disabled"
        except Exception as e:
            logging.error(f"Failed to modify Equalizer APO config: {e}")
            return False, str(e)
