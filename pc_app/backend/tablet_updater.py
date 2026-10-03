import os
import subprocess
import threading
from pathlib import Path
import sys

base_dir = Path(os.path.abspath(__file__)).parent.parent.parent
updater_script = base_dir / "updater" / "installer_and_updater.py"

def check_for_update(current_version):
    try:
        # Use subprocess to call the updater script headlessly
        result = subprocess.run(
            [sys.executable, str(updater_script), "--get-latest-version"],
            capture_output=True, text=True
        )
        latest = result.stdout.strip()
        if not latest or latest == "FAILED":
            return {"update_available": False}
        
        if latest.lower() != current_version.lower():
            return {"update_available": True, "latest_version": latest}
        return {"update_available": False}
    except Exception as e:
        return {"error": str(e)}

def do_install_update(client_ip, latest_version):
    try:
        print(f"[TabletUpdater] Downloading APK for version {latest_version}...")
        temp_apk = os.path.expandvars(r"%TEMP%\minipc_tablet_app.apk")
        
        result = subprocess.run(
            [sys.executable, str(updater_script), "--download-file", "tablet_apk/app.apk", "--tag", latest_version, "--out-file", temp_apk],
            capture_output=True, text=True
        )
        
        if "SUCCESS" not in result.stdout:
            print(f"[TabletUpdater] Failed to download APK from GitHub. Output: {result.stdout}")
            return
            
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
