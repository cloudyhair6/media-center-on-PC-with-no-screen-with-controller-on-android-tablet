import os
import subprocess
import threading
from pathlib import Path
import sys

base_dir = Path(os.path.abspath(__file__)).parent.parent.parent
updater_script = base_dir / "installer_and_updater.py"
if not updater_script.exists():
    updater_script = base_dir / "updater" / "installer_and_updater.py"

UPDATE_STATE = {
    "status": "idle",
    "message": "",
    "error": ""
}

def get_update_status():
    return UPDATE_STATE

def check_for_update(current_version):
    try:
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
    global UPDATE_STATE
    UPDATE_STATE = {"status": "running", "message": "Downloading APK from GitHub...", "error": ""}
    
    try:
        print(f"[TabletUpdater] Downloading APK for version {latest_version}...")
        temp_apk = os.path.expandvars(r"%TEMP%\minipc_tablet_app.apk")
        
        result = subprocess.run(
            [sys.executable, str(updater_script), "--download-file", "tablet_apk/app.apk", "--tag", latest_version, "--out-file", temp_apk],
            capture_output=True, text=True
        )
        
        if "SUCCESS" not in result.stdout:
            error_msg = result.stderr if result.stderr else result.stdout
            UPDATE_STATE = {"status": "error", "message": "", "error": f"Failed to download APK from GitHub. Details: {error_msg}"}
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return
            
        UPDATE_STATE["message"] = "Connecting to tablet via ADB..."
        adb_path = base_dir / "platform-tools" / "adb.exe"
        if not adb_path.exists():
            import shutil
            if shutil.which("adb"):
                adb_path = "adb"
            else:
                UPDATE_STATE = {"status": "error", "message": "", "error": "ADB (Android Debug Bridge) is missing on this PC! Ensure the platform-tools folder is downloaded."}
                print(f"[TabletUpdater] {UPDATE_STATE['error']}")
                return
            
        print(f"[TabletUpdater] Connecting to tablet at {client_ip}...")
        subprocess.run([str(adb_path), "connect", client_ip], capture_output=True)
        
        UPDATE_STATE["message"] = "Transferring and installing APK onto tablet (this may take a minute)..."
        print(f"[TabletUpdater] Installing APK to tablet...")
        
        inst_res = subprocess.run([str(adb_path), "-s", f"{client_ip}:5555", "install", "-r", temp_apk], capture_output=True, text=True)
        
        if inst_res.returncode != 0:
            UPDATE_STATE = {"status": "error", "message": "", "error": f"ADB Install Failed: {inst_res.stderr or inst_res.stdout}"}
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return
            
        UPDATE_STATE = {"status": "success", "message": "Update installed successfully! The app should restart shortly.", "error": ""}
        print("[TabletUpdater] Tablet update installed successfully.")
        
    except Exception as e:
        UPDATE_STATE = {"status": "error", "message": "", "error": f"Python Script Error: {str(e)}"}
        print(f"[TabletUpdater] {UPDATE_STATE['error']}")

def trigger_install(client_ip, latest_version):
    if UPDATE_STATE["status"] == "running":
        return
    threading.Thread(target=do_install_update, args=(client_ip, latest_version), daemon=True).start()
