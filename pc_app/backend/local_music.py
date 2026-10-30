"""
Local Music Management & PyMusicIndexer Bridge for Media Centre
==============================================================
Integrates PyMusicIndexer's Smart Incremental HDD Engine,
metadata extraction, cover art discovery, fingerprinting, and
audio playback into the Media Center remote server and tablet app.
"""

from __future__ import annotations

import os
import sys
import json
import logging
import threading
import subprocess
import string
from pathlib import Path
from typing import List, Dict, Any, Optional

logger = logging.getLogger("local_music")

# Ensure PyMusicIndexer is on sys.path
_INDEXER_DIR = Path(__file__).resolve().parent.parent / "PyMusicIndexer"
if str(_INDEXER_DIR) not in sys.path:
    sys.path.insert(0, str(_INDEXER_DIR))

try:
    from media_scanner.storage import StorageManager, DEFAULT_SONGS_JSON, DEFAULT_UNINDEXED_JSON
    from media_scanner.scanner import AudioIndexer, ScanResult
    from media_scanner.metadata import AudioMetadata, VLCMetadataEnricher, format_seconds
    from media_scanner.player import BaseAudioPlayer, get_best_player
    from media_scanner.artwork import AlbumArtManager
    from media_scanner.writer import AudioMetadataWriter
    INDEXER_AVAILABLE = True
except Exception as e:
    logger.warning(f"PyMusicIndexer import error: {e}")
    INDEXER_AVAILABLE = False


class LocalMusicManager:
    """Singleton manager for local indexed music catalog and playback."""

    _instance: Optional[LocalMusicManager] = None
    _lock = threading.Lock()

    def __init__(self):
        self.indexer_dir = _INDEXER_DIR
        self.songs_json_path = self.indexer_dir / "indexed_songs.json"
        self.unindexed_json_path = self.indexer_dir / "unindexed_files.json"

        self._player: Optional[BaseAudioPlayer] = None
        self._current_track: Optional[Dict[str, Any]] = None
        self._is_scanning = False
        self._scan_progress: Dict[str, Any] = {
            "status": "idle",
            "progress_percent": 0,
            "current_file": "",
            "current_file_index": 0,
            "total_files": 0,
            "songs_indexed": 0,
            "total_songs": 0,
            "last_scan_time": 0,
        }

        self._is_fingerprinting = False
        self._fingerprint_progress: Dict[str, Any] = {
            "status": "idle",
            "progress_percent": 0,
            "current_file": "",
            "current_file_index": 0,
            "total_files": 0,
            "enriched_count": 0,
        }

        self._gui_process: Optional[subprocess.Popen] = None

    @classmethod
    def get_instance(cls) -> LocalMusicManager:
        with cls._lock:
            if cls._instance is None:
                cls._instance = LocalMusicManager()
            return cls._instance

    # ── Folder Configuration & Directory Browsing ─────────────────────────────

    def get_configured_folders(self) -> List[str]:
        """Return explicitly saved music directories from config.json."""
        config_path = Path(__file__).resolve().parent.parent / "config.json"
        if config_path.exists():
            try:
                cfg = json.loads(config_path.read_text(encoding="utf-8"))
                folders = cfg.get("music_folders") or cfg.get("local_music_folders")
                if isinstance(folders, list):
                    return [os.path.normpath(f) for f in folders if isinstance(f, str) and os.path.isdir(f)]
            except Exception:
                pass
        return []

    def set_configured_folders(self, folders: List[str]) -> bool:
        """Persist user selected music folders to config.json."""
        config_path = Path(__file__).resolve().parent.parent / "config.json"
        cfg = {}
        if config_path.exists():
            try:
                cfg = json.loads(config_path.read_text(encoding="utf-8"))
            except Exception:
                cfg = {}
        clean_folders = []
        for f in folders:
            if f and isinstance(f, str):
                norm = os.path.normpath(f)
                if norm not in clean_folders:
                    clean_folders.append(norm)
        cfg["music_folders"] = clean_folders
        try:
            config_path.write_text(json.dumps(cfg, indent=2), encoding="utf-8")
            return True
        except Exception as e:
            logger.error(f"Failed to save music folders: {e}")
            return False

    def add_configured_folder(self, folder: str) -> List[str]:
        current = self.get_configured_folders()
        norm = os.path.normpath(folder)
        if norm not in current:
            current.append(norm)
            self.set_configured_folders(current)
        return current

    def remove_configured_folder(self, folder: str) -> List[str]:
        current = self.get_configured_folders()
        norm = os.path.normpath(folder)
        updated = [f for f in current if f != norm]
        self.set_configured_folders(updated)
        return updated

    def get_default_music_folders(self) -> List[str]:
        """Return configured folders or system auto-detected default paths."""
        configured = self.get_configured_folders()
        if configured:
            return configured

        folders = []
        user_music = Path.home() / "Music"
        if user_music.exists():
            folders.append(str(user_music))

        onedrive_music = Path.home() / "OneDrive" / "Music"
        if onedrive_music.exists():
            folders.append(str(onedrive_music))

        project_media = Path(__file__).resolve().parent.parent / "media"
        if project_media.exists() and str(project_media) not in folders:
            folders.append(str(project_media))

        return folders

    def browse_path(self, path: Optional[str] = None) -> Dict[str, Any]:
        """Enumerate root drives or subdirectories on PC for the tablet folder chooser."""
        if not path or path.strip() == "":
            drives = []
            for letter in string.ascii_uppercase:
                drive_str = f"{letter}:\\"
                if os.path.exists(drive_str):
                    drives.append(drive_str)
            return {
                "current_path": "",
                "parent_path": None,
                "folders": drives,
                "is_drives": True
            }

        abs_path = os.path.abspath(path)
        if not os.path.isdir(abs_path):
            return {
                "current_path": abs_path,
                "parent_path": "",
                "folders": [],
                "error": "Directory does not exist",
                "is_drives": False
            }

        subfolders = []
        try:
            with os.scandir(abs_path) as it:
                for entry in it:
                    try:
                        if entry.is_dir(follow_symlinks=False):
                            name = entry.name
                            if not name.startswith("$") and not name.startswith("."):
                                subfolders.append(name)
                    except Exception:
                        continue
        except Exception as e:
            logger.error(f"Error reading directory {abs_path}: {e}")

        subfolders.sort(key=str.lower)
        parent = os.path.dirname(abs_path)
        if parent == abs_path or not parent:
            parent = ""  # Return to drive list
        return {
            "current_path": abs_path,
            "parent_path": parent,
            "folders": subfolders,
            "is_drives": False
        }

    # ── Songs Catalog & Queries ───────────────────────────────────────────────

    def get_songs(self) -> List[Dict[str, Any]]:
        """Return all indexed songs as serializable dictionaries."""
        if not self.songs_json_path.exists():
            return []

        try:
            with open(self.songs_json_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, list):
                    return data
                elif isinstance(data, dict) and "songs" in data:
                    return data.get("songs", [])
        except Exception as e:
            logger.error(f"Error loading indexed songs: {e}")

        return []

    def get_artists(self) -> List[Dict[str, Any]]:
        """Return grouped list of artists with track counts."""
        songs = self.get_songs()
        artist_map: Dict[str, Dict[str, Any]] = {}
        for s in songs:
            art = (s.get("contributing_artists") or s.get("artist") or "Unknown Artist").strip()
            if not art:
                art = "Unknown Artist"
            if art not in artist_map:
                artist_map[art] = {
                    "artist": art,
                    "song_count": 0,
                    "albums": set(),
                    "art_url": s.get("album_art_web_url") or "",
                }
            artist_map[art]["song_count"] += 1
            if s.get("album"):
                artist_map[art]["albums"].add(s.get("album"))

        result = []
        for art, data in sorted(artist_map.items(), key=lambda x: x[0].lower()):
            result.append({
                "artist": data["artist"],
                "song_count": data["song_count"],
                "album_count": len(data["albums"]),
                "art_url": data["art_url"],
            })
        return result

    def get_albums(self) -> List[Dict[str, Any]]:
        """Return grouped list of albums with artist and cover art."""
        songs = self.get_songs()
        album_map: Dict[str, Dict[str, Any]] = {}
        for s in songs:
            alb = (s.get("album") or "Unknown Album").strip()
            art = (s.get("contributing_artists") or s.get("artist") or "Unknown Artist").strip()
            key = f"{alb}___{art}".lower()
            if key not in album_map:
                album_map[key] = {
                    "album": alb,
                    "artist": art,
                    "track_count": 0,
                    "year": s.get("year") or "",
                    "art_url": s.get("album_art_web_url") or "",
                }
            album_map[key]["track_count"] += 1
            if not album_map[key]["art_url"] and s.get("album_art_web_url"):
                album_map[key]["art_url"] = s.get("album_art_web_url")

        result = []
        for _, data in sorted(album_map.items(), key=lambda x: x[1]["album"].lower()):
            result.append(data)
        return result

    def get_incomplete_songs(self) -> List[Dict[str, Any]]:
        """Return songs with incomplete metadata or missing artwork."""
        review = self.get_fingerprint_review()
        return review.get("failed", [])

    def get_fingerprint_review(self) -> Dict[str, Any]:
        """Return categorized review data split into worked vs didn't work, plus detailed numbers."""
        all_songs = self.get_songs()
        worked_list = []
        failed_list = []
        missing_art_count = 0
        missing_tags_count = 0

        for s in all_songs:
            title = (s.get("title") or "").strip()
            artist = (s.get("artist") or s.get("contributing_artists") or "").strip()
            album = (s.get("album") or "").strip()
            has_art = bool(s.get("has_album_art") or s.get("album_art_web_url") or s.get("album_art_path"))
            
            missing_fields = []
            stem = os.path.splitext(s.get("file_name", ""))[0].lower()
            if not title or title.lower() in ("unknown", "untitled", stem):
                missing_fields.append("Title")
            if not artist or artist.lower() in ("unknown", "unknown artist", "various"):
                missing_fields.append("Artist")
            if not album or album.lower() in ("unknown", "unknown album"):
                missing_fields.append("Album")
            if not has_art:
                missing_fields.append("Artwork")

            if not has_art:
                missing_art_count += 1
            if any(f in ("Title", "Artist", "Album") for f in missing_fields):
                missing_tags_count += 1

            vlc_status = (s.get("vlc_status") or "").strip()
            vlc_fp = bool(s.get("vlc_fingerprinted", False))
            
            # Worked if VLC enriched it, or art/tags were resolved and complete
            is_worked = (vlc_status == "VLC Enriched") or (vlc_fp and len(missing_fields) == 0) or bool(s.get("tags_saved", False))

            entry = {
                "file_path": s.get("file_path", ""),
                "file_name": s.get("file_name", ""),
                "title": title or s.get("file_name", "Untitled"),
                "artist": artist or "Unknown Artist",
                "album": album or "",
                "status": vlc_status if vlc_status else ("Complete" if len(missing_fields) == 0 else "Needs Fingerprinting"),
                "worked": is_worked,
                "missing_fields_list": missing_fields,
            }

            if is_worked:
                worked_list.append(entry)
            elif len(missing_fields) > 0 or vlc_fp:
                failed_list.append(entry)

        unindexed = self.get_unindexed_files()

        return {
            "total_songs": len(all_songs),
            "total_needing_fp": len(failed_list),
            "total_worked": len(worked_list),
            "total_failed": len(failed_list),
            "total_missing_art": missing_art_count,
            "total_missing_tags": missing_tags_count,
            "total_unindexed": len(unindexed),
            "worked": worked_list[:150],
            "failed": failed_list[:150],
            "incomplete": failed_list[:150],
        }

    def get_unindexed_files(self) -> List[Dict[str, Any]]:
        """Return non-audio or failed/corrupt files from unindexed_files.json."""
        if not self.unindexed_json_path.exists():
            return []
        try:
            with open(self.unindexed_json_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, list):
                    return data
                elif isinstance(data, dict) and "files" in data:
                    return data.get("files", [])
        except Exception as e:
            logger.error(f"Error loading unindexed files: {e}")
        return []

    def get_indexing_ui_state(self) -> Dict[str, Any]:
        """Instantaneous in-memory snapshot for high-speed UI updates (0 disk I/O)."""
        return {
            "is_scanning": self._is_scanning,
            "scan_progress": dict(self._scan_progress),
            "is_fingerprinting": self._is_fingerprinting,
            "fingerprint_progress": dict(self._fingerprint_progress),
        }

    def get_catalog_stats(self) -> Dict[str, Any]:
        """Return library summary statistics and status."""
        if self._is_scanning:
            live_indexed = self._scan_progress.get("songs_indexed", 0)
            return {
                "total_songs": live_indexed,
                "total_needing_fp": 0,
                "total_worked": 0,
                "total_failed": 0,
                "total_missing_art": 0,
                "unindexed_count": 0,
                "incomplete_count": 0,
                "configured_folders": self.get_configured_folders(),
                "has_configured_folders": len(self.get_configured_folders()) > 0,
                "is_scanning": True,
                "scan_progress": dict(self._scan_progress),
                "is_fingerprinting": self._is_fingerprinting,
                "fingerprint_progress": dict(self._fingerprint_progress),
                "player_status": self.get_player_status(),
            }

        review = self.get_fingerprint_review()
        configured_folders = self.get_configured_folders()

        return {
            "total_songs": review.get("total_songs", 0),
            "total_needing_fp": review.get("total_needing_fp", 0),
            "total_worked": review.get("total_worked", 0),
            "total_failed": review.get("total_failed", 0),
            "total_missing_art": review.get("total_missing_art", 0),
            "unindexed_count": review.get("total_unindexed", 0),
            "incomplete_count": review.get("total_needing_fp", 0),
            "configured_folders": configured_folders,
            "has_configured_folders": len(configured_folders) > 0,
            "is_scanning": self._is_scanning,
            "scan_progress": dict(self._scan_progress),
            "is_fingerprinting": self._is_fingerprinting,
            "fingerprint_progress": dict(self._fingerprint_progress),
            "player_status": self.get_player_status(),
        }

    # ── Scanning ──────────────────────────────────────────────────────────────

    def start_scan_async(self, folders: Optional[List[str]] = None, force_full: bool = False) -> bool:
        """Trigger an incremental background scan without blocking."""
        if self._is_scanning:
            return False

        target_folders = folders or self.get_default_music_folders()
        if not target_folders:
            logger.warning("No music folders found to scan.")
            return False

        thread = threading.Thread(
            target=self._run_scan_worker,
            args=(target_folders, force_full),
            daemon=True,
            name="MusicIndexerScanThread"
        )
        thread.start()
        return True

    def _run_scan_worker(self, folders: List[str], force_full: bool):
        self._is_scanning = True
        self._scan_progress = {
            "status": "scanning",
            "progress_percent": 0,
            "current_file": "",
            "current_file_index": 0,
            "total_files": 0,
            "songs_indexed": 0,
            "total_songs": 0,
            "last_scan_time": 0,
        }

        try:
            cached_songs = {}
            cached_unindexed = {}
            if not force_full:
                try:
                    cached_songs, cached_unindexed, _ = StorageManager.load_cached_library(
                        songs_filepath=str(self.songs_json_path),
                        unindexed_filepath=str(self.unindexed_json_path),
                    )
                except Exception as ex:
                    logger.warning(f"Could not load cache: {ex}")

            # Count valid cached songs that belong to target folders
            valid_cached_count = 0
            if cached_songs and not force_full:
                for s_path in cached_songs.keys():
                    if os.path.exists(s_path):
                        for f in folders:
                            try:
                                if os.path.commonpath([os.path.abspath(s_path), os.path.abspath(f)]) == os.path.abspath(f):
                                    valid_cached_count += 1
                                    break
                            except Exception:
                                pass

            indexed_count = valid_cached_count
            self._scan_progress["songs_indexed"] = indexed_count
            self._scan_progress["total_songs"] = indexed_count

            indexer = AudioIndexer(enable_vlc_enrichment=True)

            def on_song(meta):
                nonlocal indexed_count
                indexed_count += 1
                self._scan_progress["songs_indexed"] = indexed_count
                self._scan_progress["total_songs"] = indexed_count

            def on_progress(current: int, total: int, file_path: str):
                pct = int((current / max(1, total)) * 100)
                fname = os.path.basename(file_path)
                self._scan_progress["progress_percent"] = pct
                self._scan_progress["current_file"] = fname
                self._scan_progress["current_file_index"] = current
                self._scan_progress["total_files"] = total
                self._scan_progress["songs_indexed"] = indexed_count
                self._scan_progress["total_songs"] = indexed_count
                # Live terminal update
                print(f"\r[Media Centre Indexer] [{current}/{total}] ({pct}%) Indexing: {fname[:40]:<40}", end="", flush=True)

            result = indexer.scan_folders(
                folder_paths=folders,
                recursive=True,
                cached_songs=cached_songs if not force_full else {},
                cached_unindexed=cached_unindexed if not force_full else {},
                progress_callback=on_progress,
                on_song_indexed=on_song,
            )

            StorageManager.save_scan_result(
                result,
                songs_filepath=str(self.songs_json_path),
                unindexed_filepath=str(self.unindexed_json_path),
            )

            self._scan_progress["status"] = "completed"
            self._scan_progress["songs_indexed"] = len(result.songs)
            self._scan_progress["total_songs"] = len(result.songs)
            self._scan_progress["last_scan_time"] = result.elapsed_time_seconds
            print(f"\n[Media Centre Indexer] Scan complete: {len(result.songs)} songs indexed in {result.elapsed_time_seconds:.2f}s")
            logger.info(f"Scan complete: {len(result.songs)} songs indexed in {result.elapsed_time_seconds:.2f}s")
        except Exception as e:
            logger.error(f"Scan worker failed: {e}")
            self._scan_progress["status"] = f"error: {e}"
        finally:
            self._is_scanning = False

    # ── Fingerprinting & Metadata Enrichment ──────────────────────────────────

    def start_fingerprint_async(self) -> bool:
        """Run LibVLC fingerprinting on incomplete songs in the background."""
        if self._is_scanning or self._is_fingerprinting:
            return False
        thread = threading.Thread(
            target=self._run_fingerprint_worker,
            daemon=True,
            name="VLCFingerprintThread"
        )
        thread.start()
        return True

    def _run_fingerprint_worker(self):
        self._is_fingerprinting = True
        self._fingerprint_progress = {
            "status": "fingerprinting",
            "progress_percent": 0,
            "current_file": "",
            "current_file_index": 0,
            "total_files": 0,
            "enriched_count": 0,
        }
        try:
            cached_songs, _, _ = StorageManager.load_cached_library(
                songs_filepath=str(self.songs_json_path),
                unindexed_filepath=str(self.unindexed_json_path),
            )
            all_list = list(cached_songs.values())
            incomplete_list = [s for s in all_list if s.has_incomplete_metadata or not s.has_album_art]
            total = len(incomplete_list)
            enriched = 0
            self._fingerprint_progress["total_files"] = total

            for idx, song in enumerate(incomplete_list, start=1):
                fname = song.file_name
                self._fingerprint_progress["current_file"] = fname
                self._fingerprint_progress["current_file_index"] = idx
                self._fingerprint_progress["progress_percent"] = int((idx / max(1, total)) * 100)
                try:
                    changed = VLCMetadataEnricher.enrich(song)
                    if changed:
                        enriched += 1
                        self._fingerprint_progress["enriched_count"] = enriched
                except Exception:
                    pass
                print(f"\r[Media Centre Fingerprinter] [{idx}/{total}] Fingerprinting: {fname[:40]:<40}", end="", flush=True)

            StorageManager.update_songs_catalog(all_list, str(self.songs_json_path))
            self._fingerprint_progress["status"] = "completed"
            self._fingerprint_progress["progress_percent"] = 100
            print(f"\n[Media Centre Fingerprinter] Complete: {enriched} songs enriched.")
        except Exception as e:
            logger.error(f"Fingerprinting failed: {e}")
            self._fingerprint_progress["status"] = f"error: {e}"
        finally:
            self._is_fingerprinting = False

    def start_fetch_art_async(self) -> bool:
        """Search and download album artwork for tracks missing covers."""
        if self._is_scanning or self._is_fingerprinting:
            return False
        thread = threading.Thread(
            target=self._run_fetch_art_worker,
            daemon=True,
            name="FetchArtThread"
        )
        thread.start()
        return True

    def _run_fetch_art_worker(self):
        self._is_fingerprinting = True
        self._fingerprint_progress = {
            "status": "fetching_art",
            "progress_percent": 0,
            "current_file": "",
            "enriched_count": 0,
        }
        try:
            cached_songs, _, _ = StorageManager.load_cached_library(
                songs_filepath=str(self.songs_json_path),
                unindexed_filepath=str(self.unindexed_json_path),
            )
            all_list = list(cached_songs.values())
            missing_art = [s for s in all_list if not s.has_album_art]
            total = len(missing_art)
            fetched = 0

            for idx, song in enumerate(missing_art, start=1):
                self._fingerprint_progress["current_file"] = song.file_name
                self._fingerprint_progress["progress_percent"] = int((idx / max(1, total)) * 100)
                try:
                    art_mgr = AlbumArtManager()
                    resolved = art_mgr.resolve_artwork(song, enable_online_download=True)
                    if resolved:
                        fetched += 1
                        self._fingerprint_progress["enriched_count"] = fetched
                except Exception:
                    pass

            StorageManager.update_songs_catalog(all_list, str(self.songs_json_path))
            self._fingerprint_progress["status"] = "completed"
            self._fingerprint_progress["progress_percent"] = 100
        except Exception as e:
            logger.error(f"Art fetching failed: {e}")
            self._fingerprint_progress["status"] = f"error: {e}"
        finally:
            self._is_fingerprinting = False

    def start_write_tags_async(self) -> bool:
        """Write metadata directly to MP3/WAV/FLAC tags on disk."""
        if self._is_scanning or self._is_fingerprinting:
            return False
        thread = threading.Thread(
            target=self._run_write_tags_worker,
            daemon=True,
            name="WriteTagsThread"
        )
        thread.start()
        return True

    def _run_write_tags_worker(self):
        self._is_fingerprinting = True
        self._fingerprint_progress = {
            "status": "writing_tags",
            "progress_percent": 0,
            "current_file": "",
            "enriched_count": 0,
        }
        try:
            cached_songs, _, _ = StorageManager.load_cached_library(
                songs_filepath=str(self.songs_json_path),
                unindexed_filepath=str(self.unindexed_json_path),
            )
            all_list = list(cached_songs.values())
            total = len(all_list)
            written = 0

            for idx, song in enumerate(all_list, start=1):
                self._fingerprint_progress["current_file"] = song.file_name
                self._fingerprint_progress["progress_percent"] = int((idx / max(1, total)) * 100)
                try:
                    res = AudioMetadataWriter.write_metadata(song)
                    ok = res[0] if isinstance(res, tuple) else bool(res)
                    if ok:
                        written += 1
                        self._fingerprint_progress["enriched_count"] = written
                except Exception:
                    pass

                try:
                    from media_scanner.metadata import MetadataExtractor
                    MetadataExtractor.evaluate_completeness(song)
                except Exception:
                    pass

            StorageManager.update_songs_catalog(all_list, str(self.songs_json_path))
            self._fingerprint_progress["status"] = "completed"
            self._fingerprint_progress["progress_percent"] = 100
        except Exception as e:
            logger.error(f"Writing tags failed: {e}")
            self._fingerprint_progress["status"] = f"error: {e}"
        finally:
            self._is_fingerprinting = False

    # ── Playback ──────────────────────────────────────────────────────────────

    def _ensure_player(self) -> BaseAudioPlayer:
        if self._player is None:
            self._player = get_best_player(preferred_backend="vlc")
        return self._player

    def play_file(self, file_path: str) -> bool:
        """Play a local audio track on the Media Center and pause Spotify."""
        if not os.path.isfile(file_path):
            logger.error(f"Cannot play missing file: {file_path}")
            return False

        # Mutual Pause: Pause Spotify so playback doesn't conflict
        try:
            from backend.spotify_control import SpotifyControl
            SpotifyControl.pause()
        except Exception:
            pass

        try:
            player = self._ensure_player()
            if player.load(file_path):
                player.play()

                # Find metadata if available
                title = os.path.splitext(os.path.basename(file_path))[0]
                artist = ""
                album = ""
                art_url = ""
                for s in self.get_songs():
                    if s.get("file_path") == file_path:
                        title = s.get("title") or title
                        artist = s.get("contributing_artists") or s.get("artist") or ""
                        album = s.get("album") or ""
                        art_url = s.get("album_art_web_url") or ""
                        break

                self._current_track = {
                    "file_path": file_path,
                    "title": title,
                    "artist": artist,
                    "album": album,
                    "art_url": art_url,
                }
                return True
        except Exception as e:
            logger.error(f"Playback failed via engine: {e}")
            # Fallback to system default audio player on Windows
            try:
                os.startfile(file_path)
                self._current_track = {
                    "file_path": file_path,
                    "title": os.path.basename(file_path),
                    "artist": "Local Audio",
                    "album": "",
                    "art_url": "",
                }
                return True
            except Exception as e2:
                logger.error(f"os.startfile fallback also failed: {e2}")

        return False

    def pause(self):
        if self._player:
            self._player.pause()

    def resume(self):
        # Mutual Pause: Pause Spotify when local resumes
        try:
            from backend.spotify_control import SpotifyControl
            SpotifyControl.pause()
        except Exception:
            pass

        if self._player:
            self._player.play()

    def stop(self):
        if self._player:
            self._player.stop()
            self._current_track = None

    def set_volume(self, vol: int):
        if self._player:
            self._player.set_volume(vol)

    def get_player_status(self) -> Dict[str, Any]:
        """Return current local playback status."""
        is_playing = False
        pos = 0.0
        dur = 0.0
        vol = 80

        if self._player:
            try:
                is_playing = self._player.is_playing()
                pos = self._player.get_position()
                dur = self._player.get_duration()
                vol = self._player.get_volume()
            except Exception:
                pass

        return {
            "playing": is_playing,
            "position": round(pos, 1),
            "duration": round(dur, 1),
            "volume": vol,
            "current_track": self._current_track,
        }

    # ── GUI Launcher (Background/Headless compatibility) ──────────────────────

    def launch_gui(self) -> bool:
        """Launch PyMusicIndexer's desktop player in a subprocess."""
        main_script = self.indexer_dir / "main.py"
        if not main_script.exists():
            return False

        try:
            if self._gui_process is not None and self._gui_process.poll() is None:
                return True

            self._gui_process = subprocess.Popen(
                [sys.executable, str(main_script)],
                cwd=str(self.indexer_dir),
            )
            return True
        except Exception as e:
            logger.error(f"Failed to launch PyMusicIndexer GUI: {e}")
            return False
