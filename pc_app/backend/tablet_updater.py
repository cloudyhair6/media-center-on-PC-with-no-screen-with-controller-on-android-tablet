import os
import shutil
import subprocess
import sys
import threading
from pathlib import Path

from backend.updater import (
    download_file_content,
    get_commits_between,
    get_latest_release_version,
    get_local_version,
    get_releases_behind,
    is_update_available,
)

base_dir = Path(os.path.abspath(__file__)).parent.parent.parent
updater_script = base_dir / "installer_and_updater.py"
if not updater_script.exists():
    updater_script = base_dir / "updater" / "installer_and_updater.py"

UPDATE_STATE = {
    "status": "idle",
    "message": "",
    "error": "",
}


def get_update_status():
    return UPDATE_STATE


def _format_commits_changelog(commits: list[str]) -> str:
    if not commits:
        return ""
    blocks = []
    for idx, msg in enumerate(commits, 1):
        lines = [ln.strip() for ln in str(msg).strip().splitlines() if ln.strip()]
        if not lines:
            continue
        if len(lines) == 1:
            blocks.append(f"- {lines[0]}")
        else:
            subject = lines[0]
            body = "\n  ".join(lines[1:])
            blocks.append(f"- {subject}\n  {body}")
    return "\n\n".join(blocks)


def check_for_update(current_version: str):
    try:
        current_version = (current_version or "").strip()
        latest = get_latest_release_version()

        # Fallback to subprocess call if direct check returned None
        if not latest and updater_script.exists():
            try:
                result = subprocess.run(
                    [sys.executable, str(updater_script), "--get-latest-version"],
                    capture_output=True,
                    text=True,
                    timeout=20,
                )
                lines = [ln.strip() for ln in (result.stdout or "").strip().splitlines() if ln.strip()]
                if lines:
                    candidate = lines[-1]
                    if candidate and candidate != "FAILED" and " " not in candidate:
                        latest = candidate
            except Exception:
                pass

        # Offline fallback to local updater_config.json version
        if not latest:
            latest = get_local_version()

        if not latest or latest == "FAILED":
            return {
                "update_available": False,
                "current_version": current_version,
                "latest_version": current_version,
                "releases_behind": 0,
                "commits": [],
                "changelog": "",
            }

        has_update = is_update_available(current_version, latest) if current_version else False
        commits: list[str] = []
        changelog = ""
        releases_behind = 0
        if has_update:
            commits = get_commits_between(current_version, latest)
            changelog = _format_commits_changelog(commits)
            releases_behind = get_releases_behind(current_version, latest)

        return {
            "update_available": has_update,
            "current_version": current_version,
            "latest_version": latest,
            "releases_behind": releases_behind,
            "commits": commits,
            "changelog": changelog,
        }
    except Exception as e:
        return {
            "update_available": False,
            "current_version": current_version,
            "latest_version": current_version,
            "releases_behind": 0,
            "commits": [],
            "changelog": "",
            "error": str(e),
        }


def _find_adb_executable() -> str | None:
    candidates = [
        base_dir / "platform-tools" / "adb.exe",
        base_dir / "tablet_apk" / "platform-tools" / "adb.exe",
        base_dir / "tablet_unlock" / "platform-tools" / "adb.exe",
    ]
    for c in candidates:
        if c.exists():
            return str(c)
    if shutil.which("adb"):
        return "adb"
    return None


def do_install_update(client_ip: str, latest_version: str):
    global UPDATE_STATE
    UPDATE_STATE = {"status": "running", "message": "Downloading APK from GitHub...", "error": ""}

    try:
        print(f"[TabletUpdater] Downloading APK for version {latest_version}...")
        temp_apk = os.path.expandvars(r"%TEMP%\mediacentre_tablet_app.apk")

        # Primary: use multi-strategy download_file_content directly
        apk_bytes = download_file_content("tablet_apk/app.apk", latest_version)
        if apk_bytes:
            with open(temp_apk, "wb") as f:
                f.write(apk_bytes)
        elif updater_script.exists():
            # Fallback to subprocess invocation of installer_and_updater.py
            result = subprocess.run(
                [
                    sys.executable,
                    str(updater_script),
                    "--download-file",
                    "tablet_apk/app.apk",
                    "--tag",
                    latest_version,
                    "--out-file",
                    temp_apk,
                ],
                capture_output=True,
                text=True,
                timeout=60,
            )
            if "SUCCESS" not in (result.stdout or ""):
                error_msg = result.stderr if result.stderr else result.stdout
                UPDATE_STATE = {
                    "status": "error",
                    "message": "",
                    "error": f"Failed to download APK from GitHub. Details: {error_msg}",
                }
                print(f"[TabletUpdater] {UPDATE_STATE['error']}")
                return
        else:
            UPDATE_STATE = {
                "status": "error",
                "message": "",
                "error": "Failed to download APK from GitHub.",
            }
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return

        if not os.path.exists(temp_apk) or os.path.getsize(temp_apk) < 1024:
            UPDATE_STATE = {
                "status": "error",
                "message": "",
                "error": "Downloaded APK file is empty or corrupted.",
            }
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return

        UPDATE_STATE["message"] = "Connecting to tablet via ADB..."
        adb_path = _find_adb_executable()
        if not adb_path:
            UPDATE_STATE = {
                "status": "manual_install",
                "message": "",
                "error": "ADB is missing on this PC. Downloading APK directly to tablet...",
            }
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return

        target_device = None
        # Check if a USB ADB device is already attached
        try:
            dev_res = subprocess.run([adb_path, "devices"], capture_output=True, text=True, timeout=10)
            for line in (dev_res.stdout or "").splitlines():
                line = line.strip()
                if line and not line.startswith("List of devices") and "\tdevice" in line:
                    serial = line.split("\t")[0].strip()
                    if serial:
                        target_device = serial
                        break
        except Exception:
            pass

        if not target_device and client_ip:
            print(f"[TabletUpdater] Connecting to tablet at {client_ip}...")
            conn_res = subprocess.run([adb_path, "connect", client_ip], capture_output=True, text=True, timeout=15)
            out_low = ((conn_res.stdout or "") + " " + (conn_res.stderr or "")).lower()
            if "cannot connect" not in out_low and "unable to connect" not in out_low and "failed" not in out_low:
                target_device = f"{client_ip}:5555"

        if not target_device:
            UPDATE_STATE = {
                "status": "manual_install",
                "message": "",
                "error": "Wireless ADB failed to connect. Downloading update directly to tablet...",
            }
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return

        UPDATE_STATE["message"] = "Transferring and installing APK onto tablet (this may take a minute)..."
        print(f"[TabletUpdater] Installing APK to tablet ({target_device})...")

        inst_res = subprocess.run(
            [adb_path, "-s", target_device, "install", "-r", temp_apk],
            capture_output=True,
            text=True,
            timeout=120,
        )

        inst_out = ((inst_res.stdout or "") + " " + (inst_res.stderr or "")).lower()
        if inst_res.returncode != 0 or "failure" in inst_out or "failed" in inst_out:
            UPDATE_STATE = {
                "status": "manual_install",
                "message": "",
                "error": "Silent ADB Install Failed. Downloading update directly to tablet...",
            }
            print(f"[TabletUpdater] {UPDATE_STATE['error']}")
            return

        UPDATE_STATE = {
            "status": "success",
            "message": "Update installed successfully! The app should restart shortly.",
            "error": "",
        }
        print("[TabletUpdater] Tablet update installed successfully.")

    except Exception as e:
        UPDATE_STATE = {"status": "error", "message": "", "error": f"Python Script Error: {str(e)}"}
        print(f"[TabletUpdater] {UPDATE_STATE['error']}")


def trigger_install(client_ip: str, latest_version: str):
    if UPDATE_STATE["status"] == "running":
        return
    threading.Thread(target=do_install_update, args=(client_ip, latest_version), daemon=True).start()
