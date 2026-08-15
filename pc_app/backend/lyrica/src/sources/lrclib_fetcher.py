import re
import time
from datetime import datetime, timezone

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from src.config import LRCLIB_API_URL
from src.logger import get_logger
from .base_fetcher import BaseFetcher, get_http_client, parse_lrc, build_result

logger = get_logger("lrclib_fetcher")

class LRCLIBFetcher(BaseFetcher):

    async def fetch(self, artist: str, song: str, timestamps: bool = True):
        client = get_http_client()
        try:
            logger.info(f"LRCLIB: fetching '{artist} – {song}' (timestamps={timestamps})")

            # ── Step 1: search ───────────────────────────────────────────────
            search_resp = await client.get(
                "https://lrclib.net/api/search",
                params={"track_name": song, "artist_name": artist},
                timeout=15.0,
                headers={"User-Agent": "Lyrica/1.0 (music lyrics API)"},
            )
            if search_resp.status_code != 200:
                logger.warning(f"LRCLIB search returned {search_resp.status_code}")
                return None

            results = search_resp.json()
            if not results:
                logger.info("LRCLIB: no results found")
                return None

            track = results[0]

            # ── Step 2: fetch full track data ────────────────────────────────
            get_resp = await client.get(
                LRCLIB_API_URL,
                params={
                    "track_name":  track.get("trackName"),
                    "artist_name": track.get("artistName"),
                    "album_name":  track.get("albumName"),
                    "duration":    track.get("duration"),
                },
                timeout=15.0,
                headers={"User-Agent": "Lyrica/1.0 (music lyrics API)"},
            )
            if get_resp.status_code != 200:
                logger.warning(f"LRCLIB get returned {get_resp.status_code}")
                return None

            data = get_resp.json()
            lyrics = (
                data.get("syncedLyrics") if timestamps
                else data.get("plainLyrics")
            )
            if not lyrics:
                # If synced lyrics not available, fall back to plain
                if timestamps:
                    lyrics = data.get("plainLyrics")
                if not lyrics:
                    logger.info("LRCLIB: no lyrics content in response")
                    return None

            # ── Step 3: parse synced lyrics ──────────────────────────────────
            timed = None
            if timestamps and data.get("syncedLyrics"):
                timed = parse_lrc(data["syncedLyrics"], data.get("duration"))
            
            return build_result(
                source="lrclib",
                artist=data.get("artistName"),
                title=data.get("trackName"),
                album=data.get("albumName"),
                duration=data.get("duration"),
                instrumental=data.get("instrumental", False),
                lyrics=lyrics,
                timed_lyrics=timed,
                has_timestamps=bool(timed)
            )

        except Exception as e:
            logger.error(f"LRCLIB error: {e}")
            return None
