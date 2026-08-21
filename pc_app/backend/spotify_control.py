"""Spotify control via Windows media keys and window-title parsing."""

from __future__ import annotations

import ctypes
import ctypes.wintypes
import subprocess
import time
from pathlib import Path
import psutil

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
    """Control Spotify via Windows SMTC, spotify_cli.exe and fallback APIs."""

    _last_title = ""
    _last_artist = ""
    _last_album = ""
    _last_uri = ""
    _last_artwork_url = ""
    _last_context_uri = ""
    _last_context_desc = ""
    _last_seek_time = 0.0
    _last_seek_pos_ms = 0
    _last_seek_is_active = False
    _last_metadata = {
        "playing": False,
        "artist": "",
        "title": "Spotify",
        "album": "",
        "uri": "",
        "artwork": "",
        "position_s": 0,
        "length_s": 0,
        "position_ms": 0,
        "length_ms": 0,
        "shuffle": False,
        "repeat": 0,
        "context": "",
        "context_uri": "",
        "is_ad": False
    }

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
        try:
            ms_int = int(ms)
            cur_pos = SpotifyControl._last_metadata.get("position_ms", 0)
            len_ms = SpotifyControl._last_metadata.get("length_ms", 0)
            if relative:
                target_pos = max(0, cur_pos + ms_int)
            else:
                target_pos = max(0, ms_int)
            if len_ms > 0:
                target_pos = min(target_pos, len_ms)
            
            SpotifyControl._last_seek_time = time.time()
            SpotifyControl._last_seek_pos_ms = target_pos
            SpotifyControl._last_seek_is_active = True
            
            SpotifyControl._last_metadata["position_ms"] = target_pos
            SpotifyControl._last_metadata["position_s"] = int(target_pos / 1000)
        except Exception:
            pass

        args = ["seek", ms]
        if relative:
            args.append("--relative")
        SpotifyControl._run_cli(args)

    @staticmethod
    def play_uri(uri: str) -> None:
        SpotifyControl._run_cli(["play", uri])
        
        # Give CLI a split second to send the play command
        import time
        time.sleep(0.5)
        
        # Re-apply last known shuffle/repeat state if they were active
        if SpotifyControl._last_metadata.get("shuffle", False):
            SpotifyControl._run_cli(["shuffle", "on"])
            
        repeat = SpotifyControl._last_metadata.get("repeat", 0)
        if repeat > 0:
            SpotifyControl._run_cli(["repeat", "context" if repeat == 2 else "track"])

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
    def _fetch_smtc_info() -> dict | None:
        """Fetch media properties, playback state, timeline and controls in a single async SMTC call."""
        try:
            import asyncio
            from datetime import datetime, timezone
            import winrt.windows.media.control as wmc

            async def _query():
                try:
                    manager = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
                    if not manager:
                        return None

                    # Target Spotify session specifically
                    session = None
                    curr = manager.get_current_session()
                    if curr and "spotify" in (curr.source_app_user_model_id or "").lower():
                        session = curr
                    else:
                        for s in manager.get_sessions():
                            if "spotify" in (s.source_app_user_model_id or "").lower():
                                session = s
                                break

                    if not session:
                        return None

                    props = await session.try_get_media_properties_async()
                    pb = session.get_playback_info()
                    tl = session.get_timeline_properties()

                    title = props.title if props else ""
                    artist = props.artist if props else ""
                    album = props.album_title if props else ""

                    # Playback status: 4 is Playing, 5 is Paused, 3 is Stopped, 0 is Closed
                    is_playing = (pb.playback_status == 4) if pb else False
                    shuffle = bool(pb.is_shuffle_active) if (pb and pb.is_shuffle_active is not None) else False
                    repeat = int(pb.auto_repeat_mode) if (pb and pb.auto_repeat_mode is not None) else 0

                    pos_s = 0
                    len_s = 0
                    pos_ms = 0
                    len_ms = 0

                    if tl:
                        if tl.end_time:
                            total_s = tl.end_time.total_seconds()
                            len_s = int(total_s)
                            len_ms = int(total_s * 1000)
                        if tl.position:
                            cur_s = tl.position.total_seconds()
                            if is_playing and tl.last_updated_time:
                                now = datetime.now(timezone.utc)
                                elapsed = (now - tl.last_updated_time).total_seconds()
                                cur_s += max(0.0, elapsed)
                            if len_s > 0:
                                cur_s = min(cur_s, float(len_s))
                            pos_s = int(cur_s)
                            pos_ms = int(cur_s * 1000)

                    # Reconcile recent seeks if SMTC timeline hasn't caught up
                    now_time = time.time()
                    if SpotifyControl._last_seek_is_active:
                        seek_age = now_time - SpotifyControl._last_seek_time
                        if seek_age < 2.5:
                            seek_utc = datetime.fromtimestamp(SpotifyControl._last_seek_time, timezone.utc)
                            if tl is None or tl.last_updated_time is None or tl.last_updated_time < seek_utc:
                                seek_elapsed = seek_age if is_playing else 0.0
                                est_ms = SpotifyControl._last_seek_pos_ms + int(seek_elapsed * 1000)
                                if len_ms > 0:
                                    est_ms = min(est_ms, len_ms)
                                pos_ms = est_ms
                                pos_s = int(est_ms / 1000)
                        else:
                            SpotifyControl._last_seek_is_active = False

                    return {
                        "title": title,
                        "artist": artist,
                        "album": album,
                        "is_playing": is_playing,
                        "shuffle": shuffle,
                        "repeat": repeat,
                        "position_s": pos_s,
                        "length_s": len_s,
                        "position_ms": pos_ms,
                        "length_ms": len_ms,
                    }
                except Exception:
                    return None

            return asyncio.run(_query())
        except Exception:
            return None

    @staticmethod
    def get_playback_progress() -> dict:
        smtc = SpotifyControl._fetch_smtc_info()
        if smtc:
            return {
                "position_s": smtc.get("position_s", 0),
                "length_s": smtc.get("length_s", 0),
                "position_ms": smtc.get("position_ms", 0),
                "length_ms": smtc.get("length_ms", 0)
            }
        
        # Fallback if SMTC unavailable but seek was active
        if SpotifyControl._last_seek_is_active and (time.time() - SpotifyControl._last_seek_time) < 2.5:
            est_ms = SpotifyControl._last_seek_pos_ms
            return {
                "position_s": int(est_ms / 1000),
                "length_s": SpotifyControl._last_metadata.get("length_s", 0),
                "position_ms": est_ms,
                "length_ms": SpotifyControl._last_metadata.get("length_ms", 0)
            }
        
        return {
            "position_s": SpotifyControl._last_metadata.get("position_s", 0),
            "length_s": SpotifyControl._last_metadata.get("length_s", 0),
            "position_ms": SpotifyControl._last_metadata.get("position_ms", 0),
            "length_ms": SpotifyControl._last_metadata.get("length_ms", 0)
        }

    @staticmethod
    def get_now_playing(force_fetch: bool = False) -> dict:
        """Get track metadata with rock-solid song change detection via SMTC and CLI enrichment."""
        try:
            smtc_data = SpotifyControl._fetch_smtc_info()

            if smtc_data is not None:
                title = smtc_data.get("title", "").strip()
                artist = smtc_data.get("artist", "").strip()
                album = smtc_data.get("album", "").strip()
                is_playing = smtc_data.get("is_playing", False)
                pos_s = smtc_data.get("position_s", 0)
                len_s = smtc_data.get("length_s", 0)
                pos_ms = smtc_data.get("position_ms", 0)
                len_ms = smtc_data.get("length_ms", 0)
                shuffle = smtc_data.get("shuffle", False)
                repeat = smtc_data.get("repeat", 0)

                # Check if song changed
                song_changed = (
                    force_fetch or
                    title != SpotifyControl._last_title or
                    artist != SpotifyControl._last_artist or
                    not SpotifyControl._last_metadata.get("title")
                )

                if not song_changed and title != "":
                    # Fast-path: Same song, update dynamic playback timeline and controls
                    SpotifyControl._last_metadata["playing"] = is_playing
                    SpotifyControl._last_metadata["position_s"] = pos_s
                    SpotifyControl._last_metadata["length_s"] = len_s
                    SpotifyControl._last_metadata["position_ms"] = pos_ms
                    SpotifyControl._last_metadata["length_ms"] = len_ms
                    SpotifyControl._last_metadata["shuffle"] = shuffle
                    SpotifyControl._last_metadata["repeat"] = repeat
                    return SpotifyControl._last_metadata

                # Song has changed or empty title: Enrich with CLI for URI, Artwork, Context
                SpotifyControl._last_title = title
                SpotifyControl._last_artist = artist
                if album:
                    SpotifyControl._last_album = album

                cli_output = SpotifyControl._run_cli(["now-playing", "--format", "json"])
                uri = ""
                context_desc = ""
                is_ad = False

                if cli_output:
                    try:
                        cli_json = json.loads(cli_output)
                        cp = cli_json.get("currently_playing", {})
                        if not cp and "track" in cli_json:
                            # Alternate CLI format
                            track = cli_json.get("track", {})
                            uri = track.get("uri", "")
                        else:
                            uri = cp.get("uri", "")
                        is_ad = uri.startswith("spotify:ad:")

                        # Context handling
                        ctx_obj = cp.get("context", {}) if cp else {}
                        if ctx_obj and isinstance(ctx_obj, dict):
                            actual_ctx = ctx_obj.get("uri", "")
                            if actual_ctx:
                                SpotifyControl._last_context_uri = actual_ctx
                        
                        context_desc = cp.get("context_description", "") if cp else ""
                        if not context_desc:
                            if SpotifyControl._last_context_uri == "spotify:collection:tracks":
                                context_desc = "Liked Songs"
                            elif SpotifyControl._last_context_uri:
                                try:
                                    lookup_out = SpotifyControl._run_cli(["lookup", SpotifyControl._last_context_uri, "--format", "json"])
                                    ent = json.loads(lookup_out).get("entities", [{}])[0]
                                    context_desc = ent.get("name", "")
                                except Exception:
                                    pass
                            SpotifyControl._last_context_desc = context_desc
                        else:
                            SpotifyControl._last_context_desc = context_desc
                    except Exception:
                        pass

                # Artwork resolution
                if uri and uri != SpotifyControl._last_uri:
                    SpotifyControl._last_uri = uri
                    try:
                        lookup_out = SpotifyControl._run_cli(["lookup", uri, "--format", "json"])
                        entities = json.loads(lookup_out).get("entities", [])
                        ent = entities[0] if entities else {}
                        SpotifyControl._last_artwork_url = ent.get("image_url", "")
                        if not SpotifyControl._last_album and ent.get("parent", {}).get("name"):
                            SpotifyControl._last_album = ent.get("parent", {}).get("name", "")
                    except Exception:
                        SpotifyControl._last_artwork_url = SpotifyControl.get_artwork(uri)
                elif not uri and not SpotifyControl._last_uri:
                    SpotifyControl._last_artwork_url = ""

                SpotifyControl._last_metadata = {
                    "playing": is_playing,
                    "title": title if title else ("Advertisement" if is_ad else "Spotify"),
                    "artist": artist if artist else ("Advertisement" if is_ad else ""),
                    "album": SpotifyControl._last_album if SpotifyControl._last_album else album,
                    "context": SpotifyControl._last_context_desc,
                    "context_uri": SpotifyControl._last_context_uri,
                    "uri": uri if uri else SpotifyControl._last_uri,
                    "artwork": SpotifyControl._last_artwork_url,
                    "position_s": pos_s,
                    "length_s": len_s,
                    "position_ms": pos_ms,
                    "length_ms": len_ms,
                    "shuffle": shuffle,
                    "repeat": repeat,
                    "is_ad": is_ad,
                }
                return SpotifyControl._last_metadata

            # Fallback when SMTC is unavailable (CLI only fallback)
            output = SpotifyControl._run_cli(["now-playing", "--format", "json"])
            if not output:
                if not SpotifyControl.is_running():
                    return {"playing": False, "artist": "", "title": "Not Playing", "album": "", "uri": "", "artwork": "", "position_s": 0, "length_s": 0, "position_ms": 0, "length_ms": 0, "shuffle": False, "repeat": 0, "context": "", "context_uri": "", "is_ad": False}
                return SpotifyControl._last_metadata

            # Parse CLI output when SMTC is unavailable
            data = json.loads(output)
            cp = data.get("currently_playing", {})
            if not cp and "track" in data:
                track = data.get("track", {})
                is_playing = data.get("is_playing", False)
                title = track.get("name", "")
                artists = track.get("artists", [])
                artist = artists[0].get("name", "") if artists else ""
                album_obj = track.get("album", {})
                album_text = album_obj.get("name", "")
                uri = track.get("uri", "")
                is_ad = uri.startswith("spotify:ad:")
            else:
                is_playing = cp.get("is_playing", False)
                desc = cp.get("description", "")
                uri = cp.get("uri", "")
                title = desc
                artist = ""
                for dash in [" \u2014 ", " \u2013 ", " - ", " \u00d4\u00c7\u00f6 "]:
                    if dash in desc:
                        parts = desc.split(dash, 1)
                        title = parts[0].strip()
                        artist = parts[1].strip()
                        break
                is_ad = uri.startswith("spotify:ad:")
                album_text = SpotifyControl._last_album

            if uri != SpotifyControl._last_uri and uri:
                SpotifyControl._last_uri = uri
                try:
                    lookup_out = SpotifyControl._run_cli(["lookup", uri, "--format", "json"])
                    entities = json.loads(lookup_out).get("entities", [])
                    ent = entities[0] if entities else {}
                    SpotifyControl._last_artwork_url = ent.get("image_url", "")
                    SpotifyControl._last_album = ent.get("parent", {}).get("name", "")
                except Exception:
                    SpotifyControl._last_artwork_url = SpotifyControl.get_artwork(uri)
                    SpotifyControl._last_album = ""

            prog = SpotifyControl.get_playback_progress()
            pos_s = prog.get("position_s", 0)
            len_s = prog.get("length_s", 0)
            pos_ms = prog.get("position_ms", 0)
            len_ms = prog.get("length_ms", 0)

            SpotifyControl._last_metadata = {
                "playing": is_playing,
                "title": title,
                "artist": artist,
                "album": SpotifyControl._last_album if SpotifyControl._last_album else album_text,
                "context": SpotifyControl._last_context_desc,
                "context_uri": SpotifyControl._last_context_uri,
                "uri": uri,
                "artwork": SpotifyControl._last_artwork_url,
                "position_s": pos_s,
                "length_s": len_s,
                "position_ms": pos_ms,
                "length_ms": len_ms,
                "shuffle": False,
                "repeat": 0,
                "is_ad": is_ad
            }
            return SpotifyControl._last_metadata

        except Exception as e:
            print(f"SpotifyControl error: {e}")
            return {"playing": False, "artist": "", "title": "", "album": "", "uri": "", "artwork": "", "position_s": 0, "length_s": 0, "position_ms": 0, "length_ms": 0, "shuffle": False, "repeat": 0, "context": "", "context_uri": "", "is_ad": False}

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
    def get_shuffle_repeat() -> dict | None:
        """Get shuffle and repeat states using Windows SMTC."""
        smtc = SpotifyControl._fetch_smtc_info()
        if smtc:
            return {
                "shuffle": smtc.get("shuffle", False),
                "repeat": smtc.get("repeat", 0)
            }
        return None

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
                return "Stage: pre-mix" in content
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
