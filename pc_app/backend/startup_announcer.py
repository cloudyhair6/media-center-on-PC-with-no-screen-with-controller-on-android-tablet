import os
import threading
import winsound
import time
from pathlib import Path

# Paths
BASE_DIR = Path(__file__).resolve().parent.parent
MEDIA_DIR = BASE_DIR / "media"

def play_sound_sync(filename):
    """Play a wav file synchronously."""
    filepath = MEDIA_DIR / filename
    if filepath.exists():
        winsound.PlaySound(str(filepath), winsound.SND_FILENAME)

def play_sound_async(filename):
    """Play a wav file asynchronously."""
    filepath = MEDIA_DIR / filename
    if filepath.exists():
        winsound.PlaySound(str(filepath), winsound.SND_FILENAME | winsound.SND_ASYNC)

def play_starting_sound():
    """Play the starting sound asynchronously so it doesn't block startup."""
    play_sound_async("Media_Center_is_starting_please_wait.wav")

def _play_ip_sequence(ip_address: str):
    """Thread target to play the IP sequence synchronously."""
    # Give a short delay to ensure UI has fully painted before talking
    time.sleep(1.0)
    
    # Play the "This computer's IP is" sound
    play_sound_sync("This_Computers_IP_is.wav")
    time.sleep(0.2)
    
    # Play each character
    for char in ip_address:
        if char == '.':
            play_sound_sync("dot.wav")
        elif char.isdigit():
            play_sound_sync(f"{char}.wav")

def play_ready_sequence(ip_address: str):
    """Start a background thread to announce readiness and the IP address."""
    thread = threading.Thread(target=_play_ip_sequence, args=(ip_address,), daemon=True)
    thread.start()
