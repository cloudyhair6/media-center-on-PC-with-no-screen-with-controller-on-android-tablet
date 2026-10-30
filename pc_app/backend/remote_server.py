"""HTTP remote control server for Media Centre.

Runs a lightweight HTTP server that serves a mobile-friendly remote control
web page and accepts navigation commands via a simple REST API.
"""

from __future__ import annotations

import json
import socket
import threading
import logging
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

# Setup global logging
log_path = Path(__file__).resolve().parent.parent / "Media Centre.log"
logging.basicConfig(
    filename=str(log_path),
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

try:
    from PySide6.QtCore import QObject, Signal
except Exception:
    try:
        from PyQt6.QtCore import QObject, pyqtSignal as Signal
    except Exception:
        class QObject:
            pass
        class Signal:
            def __init__(self, *args): pass
            def emit(self, *args): pass
            def connect(self, *args): pass


REMOTE_DIR = Path(__file__).resolve().parent.parent / "remote"

VALID_COMMANDS = {
    "up", "down", "left", "right",
    "select", "back", "home",
    "volume_up", "volume_down",
    "play_pause", "mute",
    "spotify_play_pause", "spotify_next", "spotify_prev", "spotify_launch",
    "launch_indexer",
}


class RemoteCommandDispatcher(QObject):
    """Bridges HTTP remote commands to Qt signals."""
    command_received = Signal(str)

    def dispatch(self, command: str) -> bool:
        if command in VALID_COMMANDS:
            self.command_received.emit(command)
            return True
        return False


class _RemoteHandler(BaseHTTPRequestHandler):
    """HTTP request handler for the remote control server."""

    def log_message(self, format, *args):
        pass

    def do_GET(self) -> None:
        """
        Main HTTP GET request handler.
        Routes API calls starting with /api/ to their respective backend controllers
        (e.g., Spotify, System Stats, Configuration) and serves static HTML/JS/CSS 
        files for the remote control interface otherwise.
        """
        raw_path = self.path.rstrip("/")
        path = raw_path.split("?")[0]
        
        if path.startswith("/api/"):
            logging.info(f"API Request: {self.path}")

        # API: command
        if path.startswith("/api/command/"):
            cmd = path.split("/api/command/", 1)[1].split("?")[0]
            if cmd in ("spotify_play_pause", "spotify_next", "spotify_prev", "spotify_launch"):
                try:
                    from backend.local_music import LocalMusicManager
                    LocalMusicManager.get_instance().pause()
                except Exception:
                    pass
                try:
                    from backend.spotify_control import SpotifyControl
                    if cmd == "spotify_play_pause":
                        SpotifyControl.play_pause()
                    elif cmd == "spotify_next":
                        SpotifyControl.next_track()
                    elif cmd == "spotify_prev":
                        SpotifyControl.prev_track()
                    elif cmd == "spotify_launch":
                        SpotifyControl.launch()
                    self._json_response(200, {"ok": True, "command": cmd})
                except Exception as e:
                    self._json_response(500, {"ok": False, "error": str(e)})
                return
            dispatcher: RemoteCommandDispatcher = self.server.dispatcher
            if dispatcher.dispatch(cmd):
                # Include volume level in response for volume commands
                response = {"ok": True, "command": cmd}
                if cmd in ("volume_up", "volume_down", "mute"):
                    try:
                        from backend.system_control import SystemControl
                        # Small delay to let the volume change take effect
                        import time
                        time.sleep(0.05)
                        response["volume"] = SystemControl.get_volume()
                    except Exception:
                        pass
                self._json_response(200, response)
            else:
                self._json_response(400, {"ok": False, "error": "Unknown command"})
            return

        # API: status
        if path == "/api/status":
            version = "v1.8"
            try:
                # updater_config.json is in the root directory (one above pc_app)
                config_path = Path(__file__).resolve().parent.parent.parent / "updater_config.json"
                if config_path.exists():
                    cfg = json.loads(config_path.read_text(encoding="utf-8"))
                    version = cfg.get("current_version", version)
            except Exception:
                pass
            self._json_response(200, {"ok": True, "app": "Media Centre", "version": version})
            return

        # API: Spotify now playing
        if path == "/api/spotify/now_playing":
            try:
                from urllib.parse import urlparse, parse_qs
                qs = parse_qs(urlparse(self.path).query)
                force_fetch = qs.get("force", ["false"])[0].lower() in ("true", "1")
                from backend.spotify_control import SpotifyControl
                info = SpotifyControl.get_now_playing(force_fetch=force_fetch)
                self._json_response(200, info)
            except Exception as e:
                self._json_response(200, {"playing": False, "artist": "", "title": str(e), "album": "", "uri": "", "position_ms": 0, "length_ms": 0, "position_s": 0, "length_s": 0})
            return

        # API: Spotify lyrics
        if path == "/api/spotify/lyrics":
            try:
                from urllib.parse import urlparse, parse_qs
                import urllib.request
                import urllib.error
                import re
                qs = parse_qs(urlparse(self.path).query)
                track = qs.get("track", [""])[0].strip()
                artist = qs.get("artist", [""])[0].strip()
                source = qs.get("source", [""])[0].strip()

                def _clean_track_title(t: str) -> str:
                    if not t:
                        return ""
                    c = re.sub(
                        r'\s*[\(\[](?:feat|ft|featuring|with|remaster|remastered|\d{4}\s+remaster|version|deluxe|mono|stereo|live|edit|radio\s+edit|bonus|anniversary|expanded|\d{4}\s+mix|mix|remix|club\s+mix|from\s+.*?soundtrack|soundtrack|ost).*?[\)\]]',
                        '',
                        t,
                        flags=re.IGNORECASE
                    )
                    c = re.sub(
                        r'\s*-\s*(?:remaster.*|\d{4}\s+remaster.*|live.*|radio\s+edit.*|deluxe.*|bonus\s+track.*|version.*|original\s+mix.*|mono.*|stereo.*|single\s+version.*|from\s+.*?soundtrack.*|\d{4}\s+mix.*)$',
                        '',
                        c,
                        flags=re.IGNORECASE
                    )
                    c = re.sub(r'\s*[\(\[]\s*[\)\]]', '', c)
                    return c.strip()

                def _clean_artist(a: str) -> str:
                    if not a:
                        return ""
                    parts = re.split(r'\s*(?:,|;|/|feat\.?|ft\.?|featuring)\s*', a, flags=re.IGNORECASE)
                    return parts[0].strip() if parts else a.strip()

                def _query_lyrica(t: str, a: str, s: str):
                    base_url = "http://127.0.0.1:9999/lyrics/"
                    params = [
                        f"song={urllib.parse.quote(t)}",
                        f"artist={urllib.parse.quote(a)}",
                        "timestamps=true"
                    ]
                    if s:
                        params.extend(["pass=true", f"sequence={urllib.parse.quote(s)}"])
                    url = f"{base_url}?{'&'.join(params)}"
                    req = urllib.request.Request(url)
                    with urllib.request.urlopen(req, timeout=12) as response:
                        return json.loads(response.read().decode('utf-8'))

                data = None
                try:
                    data = _query_lyrica(track, artist, source)
                except urllib.error.HTTPError as he:
                    try:
                        data = json.loads(he.read().decode('utf-8'))
                    except Exception:
                        data = None
                except Exception:
                    data = None

                is_success = isinstance(data, dict) and data.get("status") == "success"

                if not is_success:
                    clean_t = _clean_track_title(track)
                    clean_a = _clean_artist(artist)
                    fallbacks = []
                    if clean_t and clean_t != track:
                        fallbacks.append((clean_t, artist))
                    if clean_a and clean_a != artist:
                        fallbacks.append((track, clean_a))
                    if clean_t and clean_a and (clean_t != track or clean_a != artist):
                        fallbacks.append((clean_t, clean_a))

                    for fb_t, fb_a in fallbacks:
                        try:
                            fb_data = _query_lyrica(fb_t, fb_a, source)
                            if isinstance(fb_data, dict) and fb_data.get("status") == "success":
                                data = fb_data
                                is_success = True
                                break
                        except Exception:
                            continue

                if is_success and data:
                    self._json_response(200, data)
                elif isinstance(data, dict):
                    self._json_response(200, data)
                else:
                    self._json_response(200, {"status": "error", "error": {"message": "Lyrics not found"}, "message": "Lyrics not found"})
            except Exception as e:
                self._json_response(200, {"status": "error", "error": {"message": str(e)}, "message": str(e)})
            return

        # API: Spotify search
        if path.startswith("/api/spotify/search"):
            try:
                from urllib.parse import urlparse, parse_qs
                parsed = parse_qs(urlparse(self.path).query)
                query = parsed.get("q", [""])[0]
                search_type = parsed.get("type", ["track"])[0]
                limit = int(parsed.get("limit", ["5"])[0])
                if query:
                    from backend.spotify_control import SpotifyControl
                    res = SpotifyControl.search(query, search_type, limit)
                    # Convert to unified items list for Android 2.3 client
                    items = []
                    for k in ["tracks", "albums", "artists", "playlists"]:
                        if k in res and res[k]:
                            for item in res[k]:
                                artists = item.get("artists", [])
                                artist_name = artists[0] if artists else ""
                                img = item.get("image", "").replace("spotify:image:", "https://i.scdn.co/image/").replace("https://", "http://")
                                items.append({
                                    "name": item.get("name", ""),
                                    "artist": artist_name,
                                    "image": img,
                                    "type": search_type,
                                    "uri": item.get("uri", "")
                                })
                    self._json_response(200, {"items": items})
                else:
                    self._json_response(400, {"error": "Missing query"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Spotify library
        if path.startswith("/api/spotify/library/"):
            try:
                from urllib.parse import urlparse, parse_qs
                parsed = parse_qs(urlparse(self.path).query)
                uri = parsed.get("uri", [""])[0]
                action = path.split("/api/spotify/library/", 1)[1].split("?")[0]
                
                from backend.spotify_control import SpotifyControl
                if action == "add" and uri:
                    res = SpotifyControl.library_add(uri)
                    self._json_response(200, {"ok": res})
                elif action == "remove" and uri:
                    res = SpotifyControl.library_remove(uri)
                    self._json_response(200, {"ok": res})
                elif action == "contains" and uri:
                    res = SpotifyControl.library_contains(uri)
                    self._json_response(200, {"contains": res})
                elif action == "hierarchy":
                    res = SpotifyControl.get_folder_hierarchy()
                    self._json_response(200, res)
                else:
                    self._json_response(400, {"error": "Invalid action or missing URI"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Spotify playlist
        if path.startswith("/api/spotify/playlist/"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                parsed = parse_qs(urlparse(self.path).query)
                action = path.split("/api/spotify/playlist/", 1)[1].split("?")[0]
                if action == "add":
                    playlist_uri = parsed.get("playlist_uri", [""])[0]
                    track_uri = parsed.get("track_uri", [""])[0]
                    res = SpotifyControl.playlist_add(playlist_uri, track_uri)
                    self._json_response(200, {"ok": res})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Spotify queue, shuffle, repeat
        if path.startswith("/api/spotify/queue"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                action = path.split("/api/spotify/queue", 1)[1].split("?")[0]
                if action == "/add":
                    uri = parse_qs(urlparse(self.path).query).get("uri", [""])[0]
                    res = SpotifyControl.queue_add(uri)
                    self._json_response(200, {"ok": res})
                else:
                    res = SpotifyControl.get_queue()
                    self._json_response(200, res)
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path.startswith("/api/spotify/shuffle"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                state = parse_qs(urlparse(self.path).query).get("state", ["true"])[0].lower() == "true"
                res = SpotifyControl.shuffle(state)
                if res:
                    SpotifyControl._last_metadata["shuffle"] = state
                self._json_response(200, {"ok": res})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path.startswith("/api/spotify/repeat"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                state = parse_qs(urlparse(self.path).query).get("state", ["off"])[0]
                res = SpotifyControl.repeat(state)
                if res:
                    SpotifyControl._last_metadata["repeat"] = 0 if state == "off" else (1 if state == "track" else 2)
                self._json_response(200, {"ok": res})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path.startswith("/api/spotify/seek"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                qs = parse_qs(urlparse(self.path).query)
                ms = qs.get("ms", ["0"])[0]
                relative = qs.get("type", ["relative"])[0] == "relative"
                SpotifyControl.seek(ms, relative=relative)
                self._json_response(200, {"ok": True})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Spotify play URI
        if path.startswith("/api/spotify/play"):
            try:
                from backend.local_music import LocalMusicManager
                LocalMusicManager.get_instance().pause()
            except Exception:
                pass
            try:
                from urllib.parse import urlparse, parse_qs
                uri = parse_qs(urlparse(self.path).query).get("uri", [""])[0]
                if uri:
                    from backend.spotify_control import SpotifyControl
                    SpotifyControl._last_context_uri = uri
                    SpotifyControl.play_uri(uri)
                    self._json_response(200, {"ok": True})
                else:
                    self._json_response(400, {"error": "Missing URI"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Audio config
        if path == "/api/audio/info":
            try:
                from backend.system_control import SystemControl
                info = SystemControl.get_audio_info()
                self._json_response(200, info)
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path == "/api/audio/open_config":
            try:
                from backend.system_control import SystemControl
                SystemControl.open_speaker_config()
                self._json_response(200, {"ok": True})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return
        if path == "/api/audio/speaker_fill_state":
            try:
                from backend.spotify_control import SpotifyControl
                state = SpotifyControl.get_speaker_fill_state()
                self._json_response(200, {"enabled": state})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path.startswith("/api/audio/speaker_fill"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                enabled = parse_qs(urlparse(self.path).query).get("enabled", ["true"])[0].lower() == "true"
                ok, msg = SpotifyControl.enable_speaker_fill(enabled)
                self._json_response(200, {"ok": ok, "message": msg})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: volume level
        if path == "/api/volume/current":
            try:
                from backend.system_control import SystemControl
                vol = SystemControl.get_volume()
                self._json_response(200, {"volume": vol})
            except Exception:
                self._json_response(200, {"volume": -1})
            return

        if path == "/api/volume/set":
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.system_control import SystemControl
                qs = parse_qs(urlparse(self.path).query)
                vol_str = qs.get("vol", [""])[0]
                if vol_str.isdigit():
                    vol = int(vol_str)
                    SystemControl.set_volume(vol)
                    
                    # Update the UI if needed
                    dispatcher: RemoteCommandDispatcher = self.server.dispatcher
                    dispatcher.command_received.emit("volume_update")
                    
                    self._json_response(200, {"ok": True, "volume": vol})
                else:
                    self._json_response(400, {"error": "Invalid volume"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path == "/api/volume/mixer":
            try:
                from backend.system_control import SystemControl
                self._json_response(200, SystemControl.get_mixer())
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path == "/api/volume/app/set":
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.system_control import SystemControl
                qs = parse_qs(urlparse(self.path).query)
                key = qs.get("key", [""])[0]
                vol_str = qs.get("vol", [""])[0]
                if key and vol_str.isdigit():
                    SystemControl.set_app_volume(key, int(vol_str))
                    self._json_response(200, {"ok": True})
                else:
                    self._json_response(400, {"error": "Invalid app volume params"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path == "/api/audio/devices":
            try:
                from backend.system_control import SystemControl
                self._json_response(200, {"devices": SystemControl.get_output_devices()})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path == "/api/audio/device/set":
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.system_control import SystemControl
                device_id = parse_qs(urlparse(self.path).query).get("id", [""])[0]
                if device_id:
                    ok = SystemControl.set_output_device(device_id)
                    self._json_response(200, {"ok": ok})
                else:
                    self._json_response(400, {"error": "Missing device id"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        if path.startswith("/api/system/"):
            action = path.split("/api/system/", 1)[1].split("?")[0]
            if action == "tablet_update_check":
                try:
                    from urllib.parse import urlparse, parse_qs
                    from backend.tablet_updater import check_for_update
                    qs = parse_qs(urlparse(self.path).query)
                    version = qs.get("version", [""])[0]
                    self._json_response(200, check_for_update(version))
                except Exception as e:
                    self._json_response(500, {"error": str(e)})
                return
            elif action == "tablet_update_install":
                try:
                    from urllib.parse import urlparse, parse_qs
                    from backend.tablet_updater import trigger_install
                    qs = parse_qs(urlparse(self.path).query)
                    version = qs.get("version", [""])[0]
                    client_ip = self.client_address[0]
                    trigger_install(client_ip, version)
                    self._json_response(200, {"ok": True})
                except Exception as e:
                    self._json_response(500, {"error": str(e)})
                return
            elif action == "tablet_update_status":
                try:
                    from backend.tablet_updater import get_update_status
                    self._json_response(200, get_update_status())
                except Exception as e:
                    self._json_response(500, {"error": str(e)})
                return
            elif action == "tablet_apk":
                import os
                temp_apk = os.path.expandvars(r"%TEMP%\mediacentre_tablet_app.apk")
                if os.path.exists(temp_apk):
                    self.send_response(200)
                    self.send_header("Content-Type", "application/vnd.android.package-archive")
                    self.send_header("Content-Length", str(os.path.getsize(temp_apk)))
                    self.end_headers()
                    with open(temp_apk, "rb") as f:
                        import shutil
                        shutil.copyfileobj(f, self.wfile)
                else:
                    self.send_error(404, "APK not found")
                return
            elif action == "files":
                try:
                    import os
                    from backend.local_music import LocalMusicManager
                    mgr = LocalMusicManager.get_instance()
                    songs = mgr.get_songs()
                    if not songs and not mgr._is_scanning:
                        mgr.start_scan_async()
                    items = []
                    for s in songs:
                        items.append({
                            "name": s.get("title") or os.path.basename(s.get("file_path", "")),
                            "artist": s.get("contributing_artists") or s.get("artist") or "",
                            "album": s.get("album") or "",
                            "genre": s.get("genre") or "",
                            "duration": s.get("duration_formatted") or "",
                            "format": s.get("format") or "",
                            "bitrate": s.get("bitrate_kbps") or "",
                            "art_url": s.get("album_art_web_url") or "",
                            "path": s.get("file_path", "")
                        })
                    self._json_response(200, {"items": items, "total": len(items), "is_scanning": mgr._is_scanning})
                except Exception as e:
                    self._json_response(500, {"error": str(e)})
                return
            elif action == "play_file":
                try:
                    from urllib.parse import urlparse, parse_qs
                    from backend.local_music import LocalMusicManager
                    qs = parse_qs(urlparse(self.path).query)
                    file_path = qs.get("path", [""])[0]
                    if file_path:
                        ok = LocalMusicManager.get_instance().play_file(file_path)
                        self._json_response(200, {"ok": ok, "path": file_path})
                    else:
                        self._json_response(400, {"error": "Missing path"})
                except Exception as e:
                    self._json_response(500, {"error": str(e)})
                return
            
            try:
                from backend.system_control import SystemControl
                if action == "shutdown":
                    self._json_response(200, {"ok": True})
                    SystemControl.shutdown()
                elif action == "restart":
                    self._json_response(200, {"ok": True})
                    SystemControl.restart()
                elif action == "close_app":
                    self._json_response(200, {"ok": True})
                    import os
                    os._exit(0)
                elif action == "spotify_reinstall":
                    self._json_response(200, {"ok": True})
                    import threading
                    threading.Thread(target=SystemControl.reinstall_spotify, daemon=True).start()
                elif action == "stats":
                    res = SystemControl.get_system_stats()
                    res['disk'] = SystemControl.get_disk_usage()
                    self._json_response(200, res)

                elif action == "scaling":
                    SystemControl.set_display_scaling_100()
                    self._json_response(200, {"ok": True})
                elif action == "wifi/list":
                    networks = SystemControl.get_available_networks()
                    self._json_response(200, {"networks": networks})
                elif action == "wifi/connect":
                    from urllib.parse import urlparse, parse_qs
                    qs = parse_qs(urlparse(self.path).query)
                    ssid = qs.get("ssid", [""])[0]
                    pwd = qs.get("pwd", [""])[0]
                    if ssid:
                        res = SystemControl.connect_wifi(ssid, pwd)
                        self._json_response(200, {"ok": res})
                    else:
                        self._json_response(400, {"error": "Missing ssid"})
                else:
                    self._json_response(400, {"error": "Unknown system command"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})

        # API: Local PyMusicIndexer
        if path.startswith("/api/indexer/"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.local_music import LocalMusicManager
                action = path.split("/api/indexer/", 1)[1].split("?")[0]
                mgr = LocalMusicManager.get_instance()
                parsed = parse_qs(urlparse(self.path).query)

                if action == "status":
                    self._json_response(200, mgr.get_catalog_stats())
                elif action == "browse":
                    p = parsed.get("path", [""])[0]
                    self._json_response(200, mgr.browse_path(p))
                elif action == "folders":
                    self._json_response(200, {"folders": mgr.get_configured_folders()})
                elif action == "folders/add":
                    f = parsed.get("folder", [""])[0]
                    if f:
                        updated = mgr.add_configured_folder(f)
                        self._json_response(200, {"ok": True, "folders": updated})
                    else:
                        self._json_response(400, {"error": "Missing folder"})
                elif action == "folders/remove":
                    f = parsed.get("folder", [""])[0]
                    if f:
                        updated = mgr.remove_configured_folder(f)
                        self._json_response(200, {"ok": True, "folders": updated})
                    else:
                        self._json_response(400, {"error": "Missing folder"})
                elif action == "songs":
                    songs = mgr.get_songs()
                    q = parsed.get("q", [""])[0].strip().lower()
                    artist = parsed.get("artist", [""])[0].strip().lower()
                    album = parsed.get("album", [""])[0].strip().lower()
                    if q or artist or album:
                        filtered = []
                        for s in songs:
                            s_title = (s.get("title") or "").lower()
                            s_artist = (s.get("contributing_artists") or s.get("artist") or "").lower()
                            s_album = (s.get("album") or "").lower()
                            s_genre = (s.get("genre") or "").lower()
                            if q and (q not in s_title and q not in s_artist and q not in s_album and q not in s_genre):
                                continue
                            if artist and artist != s_artist and artist not in s_artist:
                                continue
                            if album and album != s_album and album not in s_album:
                                continue
                            filtered.append(s)
                        songs = filtered

                    total_songs = len(songs)
                    limit_str = parsed.get("limit", ["100"])[0]
                    offset_str = parsed.get("offset", ["0"])[0]
                    try:
                        limit = max(1, min(int(limit_str), 300))
                    except Exception:
                        limit = 100
                    try:
                        offset = max(0, int(offset_str))
                    except Exception:
                        offset = 0

                    paged = songs[offset:offset + limit]
                    projected = []
                    for s in paged:
                        projected.append({
                            "file_path": s.get("file_path", ""),
                            "file_name": s.get("file_name", ""),
                            "title": s.get("title") or s.get("file_name", "Untitled"),
                            "artist": s.get("contributing_artists") or s.get("artist") or "Unknown Artist",
                            "album": s.get("album") or "",
                            "duration_formatted": s.get("duration_formatted") or "",
                            "format": s.get("format") or "",
                            "bitrate_kbps": s.get("bitrate_kbps") or "",
                        })
                    self._json_response(200, {"songs": projected, "total": total_songs, "limit": limit, "offset": offset})
                elif action == "artists":
                    self._json_response(200, {"artists": mgr.get_artists()})
                elif action == "albums":
                    self._json_response(200, {"albums": mgr.get_albums()})
                elif action in ("incomplete", "review"):
                    self._json_response(200, mgr.get_fingerprint_review())
                elif action == "unindexed":
                    un = mgr.get_unindexed_files()
                    self._json_response(200, {"unindexed": un[:150], "total": len(un)})
                elif action == "scan":
                    folders = parsed.get("folders", [])
                    if not folders:
                        f_param = parsed.get("folder", [""])[0]
                        if f_param:
                            folders = [f_param]
                    force = parsed.get("force", ["false"])[0].lower() in ("true", "1")
                    started = mgr.start_scan_async(folders=folders or None, force_full=force)
                    self._json_response(200, {"started": started, "is_scanning": mgr._is_scanning})
                elif action == "fingerprint":
                    started = mgr.start_fingerprint_async()
                    self._json_response(200, {"started": started, "is_fingerprinting": mgr._is_fingerprinting})
                elif action == "fetch_art":
                    started = mgr.start_fetch_art_async()
                    self._json_response(200, {"started": started, "is_fingerprinting": mgr._is_fingerprinting})
                elif action == "write_tags":
                    started = mgr.start_write_tags_async()
                    self._json_response(200, {"started": started, "is_fingerprinting": mgr._is_fingerprinting})
                elif action == "play":
                    p = parsed.get("path", [""])[0]
                    if p:
                        ok = mgr.play_file(p)
                        self._json_response(200, {"ok": ok, "path": p})
                    else:
                        self._json_response(400, {"error": "Missing path"})
                elif action == "pause":
                    mgr.pause()
                    self._json_response(200, {"ok": True})
                elif action == "resume":
                    mgr.resume()
                    self._json_response(200, {"ok": True})
                elif action == "stop":
                    mgr.stop()
                    self._json_response(200, {"ok": True})
                elif action == "launch_gui":
                    self._json_response(200, {"ok": True})
                else:
                    self._json_response(400, {"error": "Unknown indexer action"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Direct image bytes proxy fallback for Android 2.3
        if path.startswith("/api/image_download"):
            try:
                from urllib.parse import urlparse, parse_qs
                import urllib.request
                import ssl
                img_url = parse_qs(urlparse(self.path).query).get("url", [""])[0]
                if not img_url:
                    self.send_error(400, "Missing url")
                    return
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE
                req = urllib.request.Request(
                    img_url,
                    headers={
                        "User-Agent": "Mozilla/5.0",
                        "Accept": "image/jpeg,image/png,image/*,*/*;q=0.8",
                    },
                )
                with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
                    raw_bytes = resp.read()
                    content_type = resp.headers.get("Content-Type", "image/jpeg")
                # Ensure format is standard JPEG/PNG for Android 2.3 BitmapFactory
                try:
                    from PySide6.QtGui import QImage
                    from PySide6.QtCore import QByteArray, QBuffer, QIODevice
                    qimg = QImage.fromData(raw_bytes)
                    if not qimg.isNull():
                        ba = QByteArray()
                        buf = QBuffer(ba)
                        buf.open(QIODevice.WriteOnly)
                        qimg.save(buf, "JPG", 90)
                        raw_bytes = bytes(ba)
                        content_type = "image/jpeg"
                except Exception:
                    pass
                self.send_response(200)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(raw_bytes)))
                self.send_header("Cache-Control", "max-age=3600")
                self.end_headers()
                self.wfile.write(raw_bytes)
            except Exception as e:
                self.send_error(500, str(e))
            return

        # API: Proxy artwork for Android 2.3
        if path.startswith("/api/proxy_art"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                import json as json_mod
                uri = parse_qs(urlparse(self.path).query).get("uri", [""])[0]
                if uri:
                    res = SpotifyControl._run_cli(["lookup", uri, "--format", "json"])
                    if res:
                        try:
                            data = json_mod.loads(res)
                            entities = data.get("entities", [])
                            if entities:
                                img_url = (entities[0].get("image_url") or "").strip()
                                if img_url.startswith("spotify:image:"):
                                    img_url = "http://i.scdn.co/image/" + img_url.split("spotify:image:", 1)[1]
                                elif img_url.startswith("spotify:mosaic:"):
                                    parts = img_url.split(":")[2:]
                                    if parts:
                                        img_url = "http://mosaic.scdn.co/300/" + "".join(parts)
                                if img_url:
                                    if "i.scdn.co" in img_url:
                                        img_url = img_url.replace("https://", "http://")
                                        # 1e02 is 300x300 (Search size), b273 is 640x640 (High Quality), 4851 is 64x64 (Low Quality)
                                        low_res = img_url.replace("b273", "4851").replace("1e02", "4851")
                                        high_res = img_url.replace("4851", "b273").replace("1e02", "b273")
                                    else:
                                        low_res = img_url
                                        high_res = img_url
                                    self._json_response(200, {"thumbnail_url": low_res, "high_res_url": high_res})
                                    return
                                else:
                                    # Lookup succeeded, but Spotify provided no image URL for this item (e.g. custom playlist with no cover)
                                    self._json_response(200, {"thumbnail_url": "", "high_res_url": "", "no_url": True})
                                    return
                        except Exception:
                            pass
                    
                    # Fallback if lookup fails or returns no image
                    self._json_response(200, {"thumbnail_url": "", "high_res_url": "", "no_url": True})
                else:
                    self._json_response(400, {"error": "Missing URI"})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return



        # API: Get config
        if path == '/api/config':
            try:
                config_path = Path(__file__).resolve().parent.parent / 'config.json'
                if config_path.exists():
                    import json as json_mod
                    cfg = json_mod.loads(config_path.read_text())
                    self._json_response(200, cfg)
                else:
                    self._json_response(200, {})
            except Exception as e:
                self._json_response(500, {'error': str(e)})
            return

        # API: Update config
        if path == '/api/config/set':
            try:
                from urllib.parse import urlparse, parse_qs
                import json as json_mod
                qs = parse_qs(urlparse(self.path).query)
                key = qs.get('key', [''])[0]
                value = qs.get('value', [''])[0]
                if key:
                    config_path = Path(__file__).resolve().parent.parent / 'config.json'
                    cfg = {}
                    if config_path.exists():
                        cfg = json_mod.loads(config_path.read_text())
                    # Parse booleans
                    if value.lower() == 'true':
                        value = True
                    elif value.lower() == 'false':
                        value = False
                    cfg[key] = value
                    config_path.write_text(json_mod.dumps(cfg, indent=2))
                    self._json_response(200, {'ok': True})
                else:
                    self._json_response(400, {'error': 'Missing key'})
            except Exception as e:
                self._json_response(500, {'error': str(e)})
            return

        # API: Kiosk unlock trigger
        if path == "/api/unlock_trigger":
            try:
                RemoteServer.force_unlock = True
                self._json_response(200, {"ok": True})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # API: Kiosk unlock status
        if path == "/api/unlock_status":
            try:
                status = RemoteServer.force_unlock
                # Auto-reset the flag after it's read as True
                if status:
                    RemoteServer.force_unlock = False
                self._json_response(200, {"unlock": status})
            except Exception as e:
                self._json_response(500, {"error": str(e)})
            return

        # Serve static files
        if path == "" or path == "/":
            path = "/index.html"

        file_path = REMOTE_DIR / path.lstrip("/")
        try:
            file_path = file_path.resolve()
            if not str(file_path).startswith(str(REMOTE_DIR.resolve())):
                self._text_response(403, "Forbidden")
                return
        except (ValueError, OSError):
            self._text_response(403, "Forbidden")
            return

        if file_path.is_file():
            content_type = self._guess_type(file_path.suffix)
            try:
                data = file_path.read_bytes()
            except OSError:
                self._text_response(403, "Forbidden")
                return
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(data)
        else:
            self._text_response(404, "Not Found")

    def _json_response(self, code: int, data: dict) -> None:
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def _text_response(self, code: int, text: str) -> None:
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    @staticmethod
    def _guess_type(suffix: str) -> str:
        return {
            ".html": "text/html; charset=utf-8",
            ".css": "text/css",
            ".js": "application/javascript",
            ".json": "application/json",
            ".png": "image/png",
            ".svg": "image/svg+xml",
            ".ico": "image/x-icon",
        }.get(suffix.lower(), "application/octet-stream")


def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


class RemoteServer:
    """Manages the HTTP remote control server in a background thread."""
    
    force_unlock = False

    def __init__(self, dispatcher: RemoteCommandDispatcher, port: int = 8080) -> None:
        self.dispatcher = dispatcher
        self.port = port
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None

    def start(self) -> str:
        self._server = ThreadingHTTPServer(("0.0.0.0", self.port), _RemoteHandler)
        self._server.dispatcher = self.dispatcher  # type: ignore
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()
        
        # Start Lyrica subprocess
        import subprocess
        import sys
        lyrica_dir = Path(__file__).resolve().parent / "lyrica"
        if lyrica_dir.exists():
            try:
                self._lyrica_process = subprocess.Popen(
                    [sys.executable, str(lyrica_dir / "run.py")],
                    cwd=str(lyrica_dir),
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    creationflags=0x08000000  # CREATE_NO_WINDOW
                )
                logging.info("Started Lyrica server on port 9999")
            except Exception as e:
                logging.error(f"Failed to start Lyrica: {e}")
                self._lyrica_process = None
        else:
            self._lyrica_process = None

        ip = get_local_ip()
        url = f"http://{ip}:{self.port}"
        print(f"[Media Centre] Remote control available at: {url}")
        return url

    def stop(self) -> None:
        if self._server:
            self._server.shutdown()
            self._server = None
        if hasattr(self, "_lyrica_process") and self._lyrica_process:
            try:
                self._lyrica_process.terminate()
            except Exception:
                pass
            self._lyrica_process = None

    @property
    def url(self) -> str:
        return f"http://{get_local_ip()}:{self.port}"
