import os
import subprocess
import threading
from pathlib import Path
import sys

# Ensure updater is in path
base_dir = Path(__file__).resolve().parent.parent.parent
sys.path.append(str(base_dir))

from updater.installer_and_updater import get_latest_release_version, download_file_content

def check_for_update(current_version):
    try:
        latest = get_latest_release_version()
        if not latest:
            return {"update_available": False}
        
        if latest.lower() != current_version.lower():
            return {"update_available": True, "latest_version": latest}
        return {"update_available": False}
    except Exception as e:
        return {"error": str(e)}

def do_install_update(client_ip, latest_version):
    try:
        print(f"[TabletUpdater] Downloading APK for version {latest_version}...")
        content = download_file_content("tablet_apk/app.apk", latest_version)
        if not content:
            print("[TabletUpdater] Failed to download APK from GitHub.")
            return

        temp_apk = os.path.expandvars(r"%TEMP%\minipc_tablet_app.apk")
        with open(temp_apk, "wb") as f:
            f.write(content)
            
        adb_path = base_dir / "platform-tools" / "adb.exe"
        if not adb_path.exists():
            adb_path = "adb" 
            
        print(f"[TabletUpdater] Connecting to tablet at {client_ip}...")
        subprocess.run([str(adb_path), "connect", client_ip], capture_output=True)
        
        print(f"[TabletUpdater] Installing APK to tablet...")
        subprocess.run([str(adb_path), "-s", f"{client_ip}:5555", "install", "-r", temp_apk], capture_output=True)
        print("[TabletUpdater] Tablet update installed successfully.")
        
    except Exception as e:
        print(f"[TabletUpdater] Failed to install tablet update: {e}")

def trigger_install(client_ip, latest_version):
    threading.Thread(target=do_install_update, args=(client_ip, latest_version), daemon=True).start()
