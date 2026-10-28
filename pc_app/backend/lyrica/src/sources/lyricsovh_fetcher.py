import httpx
import urllib.parse
from src.logger import get_logger
from .base_fetcher import BaseFetcher, get_http_client, build_result

logger = get_logger("lyricsovh_fetcher")


class LyricsOvhFetcher(BaseFetcher):
    source_name = "lyrics.ovh"

    async def fetch(self, artist: str, song: str, timestamps: bool = False):
        client = get_http_client()
        try:
            logger.info(f"Attempting Lyrics.ovh for {artist} - {song}")
            safe_artist = urllib.parse.quote(artist.strip())
            safe_song = urllib.parse.quote(song.strip())
            resp = await client.get(f"https://api.lyrics.ovh/v1/{safe_artist}/{safe_song}")
            if resp.status_code != 200:
                return None
            data = resp.json()
            lyrics = data.get("lyrics", "").strip()
            if not lyrics:
                return None
            return build_result(
                source="lyrics.ovh",
                artist=artist,
                title=song,
                lyrics=lyrics,
                has_timestamps=False,
            )
        except httpx.TimeoutException:
            logger.warning(f"Lyrics.ovh timeout for {artist} - {song}")
            return None
        except Exception as e:
            logger.error(f"Lyrics.ovh error: {e}")
            return None
