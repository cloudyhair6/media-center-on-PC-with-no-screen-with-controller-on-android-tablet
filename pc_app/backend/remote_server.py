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

from PySide6.QtCore import QObject, Signal


REMOTE_DIR = Path(__file__).resolve().parent.parent / "remote"

VALID_COMMANDS = {
    "up", "down", "left", "right",
    "select", "back", "home",
    "volume_up", "volume_down",
    "play_pause", "mute",
    "spotify_play_pause", "spotify_next", "spotify_prev", "spotify_launch",
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
            cmd = path.split("/api/command/", 1)[1]
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
            self._json_response(200, {"ok": True, "app": "Media Centre"})
            return

        # API: Spotify now playing
        if path == "/api/spotify/now_playing":
            try:
                from backend.spotify_control import SpotifyControl
                info = SpotifyControl.get_now_playing()
                self._json_response(200, info)
            except Exception as e:
                self._json_response(200, {"playing": False, "artist": "", "title": str(e), "album": "", "uri": ""})
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
                        if k in res:
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
                from urllib.parse import urlparse, parse_qs
                uri = parse_qs(urlparse(self.path).query).get("uri", [""])[0]
                if uri:
                    from backend.spotify_control import SpotifyControl
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

        if path.startswith("/api/volume/set"):
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

        # API: system actions
        if path.startswith("/api/system/"):
            action = path.split("/api/system/", 1)[1].split("?")[0]
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
        # API: Proxy artwork for Android 2.3
        if path.startswith("/api/proxy_art"):
            try:
                from urllib.parse import urlparse, parse_qs
                from backend.spotify_control import SpotifyControl
                import json
                uri = parse_qs(urlparse(self.path).query).get("uri", [""])[0]
                if uri:
                    res = SpotifyControl._run_cli(["lookup", uri, "--format", "json"])
                    if res:
                        data = json.loads(res)
                        entities = data.get("entities", [])
                        if entities:
                            img_url = entities[0].get("image_url", "")
                            if img_url:
                                img_url = img_url.replace("https://", "http://")
                                # 1e02 is 300x300 (Search size), b273 is 640x640 (High Quality), 4851 is 64x64 (Low Quality)
                                low_res = img_url.replace("b273", "4851").replace("1e02", "4851")
                                high_res = img_url.replace("4851", "b273").replace("1e02", "b273")
                                self._json_response(200, {"thumbnail_url": low_res, "high_res_url": high_res})
                                return
                    # Fallback if lookup fails
                    self._json_response(400, {"error": "Could not resolve artwork"})
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
            data = file_path.read_bytes()
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
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


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
        ip = get_local_ip()
        url = f"http://{ip}:{self.port}"
        print(f"[Media Centre] Remote control available at: {url}")
        return url

    def stop(self) -> None:
        if self._server:
            self._server.shutdown()
            self._server = None

    @property
    def url(self) -> str:
        return f"http://{get_local_ip()}:{self.port}"
