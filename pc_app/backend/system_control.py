"""System control utilities for Windows."""
import subprocess


def _get_volume_interface():
    """Get the IAudioEndpointVolume interface."""
    import pythoncom
    pythoncom.CoInitialize()
    from pycaw.pycaw import AudioUtilities
    speakers = AudioUtilities.GetSpeakers()
    return speakers.EndpointVolume


class SystemControl:
    """Provides static methods to control Windows system settings."""

    @staticmethod
    def get_volume() -> int:
        """Get current system volume (0-100)."""
        try:
            volume = _get_volume_interface()
            return int(volume.GetMasterVolumeLevelScalar() * 100)
        except Exception as e:
            print(f"Failed to get volume: {e}")
            return 50

    @staticmethod
    def set_volume(value: int) -> None:
        """Set system volume and individual application volumes (HDMI bypass)."""
        clamped = max(0, min(100, value))
        vol_float = clamped / 100.0
        try:
            # 1. Try to set Windows Master Volume
            volume = _get_volume_interface()
            volume.SetMasterVolumeLevelScalar(vol_float, None)
        except Exception as e:
            print(f"Failed to set master volume: {e}")
            
        try:
            # 2. Set individual application volumes (bypasses HDMI master volume lock)
            import pythoncom
            pythoncom.CoInitialize()
            from pycaw.pycaw import AudioUtilities
            sessions = AudioUtilities.GetAllSessions()
            for session in sessions:
                vol = session.SimpleAudioVolume
                if vol:
                    vol.SetMasterVolume(vol_float, None)
        except Exception as e:
            print(f"Failed to set application volumes: {e}")

    @staticmethod
    def get_brightness() -> int:
        """Get current screen brightness (0-100)."""
        try:
            import screen_brightness_control as sbc
            brightness = sbc.get_brightness()
            if isinstance(brightness, list):
                return brightness[0] if brightness else 50
            return brightness
        except Exception:
            return 50

    @staticmethod
    def set_brightness(value: int) -> None:
        """Set screen brightness (0-100)."""
        try:
            import screen_brightness_control as sbc
            sbc.set_brightness(max(0, min(100, value)))
        except Exception as e:
            print(f"Failed to set brightness: {e}")

    @staticmethod
    def get_wifi_status() -> dict:
        """Get current Wi-Fi connection status.

        Returns:
            dict with keys: "connected" (bool), "network" (str), "signal" (str)
        """
        try:
            result = subprocess.run(
                ["netsh", "wlan", "show", "interfaces"],
                capture_output=True, text=True, timeout=5
            )
            output = result.stdout
            connected = False
            network = ""
            signal = ""
            for line in output.split("\n"):
                line = line.strip()
                if "State" in line and "connected" in line.lower():
                    connected = "disconnected" not in line.lower()
                elif "SSID" in line and "BSSID" not in line:
                    network = line.split(":", 1)[1].strip() if ":" in line else ""
                elif "Signal" in line:
                    signal = line.split(":", 1)[1].strip() if ":" in line else ""
            return {"connected": connected, "network": network, "signal": signal}
        except Exception:
            return {"connected": False, "network": "", "signal": ""}

    @staticmethod
    def get_available_networks() -> list:
        """Get list of available Wi-Fi networks.

        Returns:
            list of dicts with keys: "name" (str), "signal" (str), "security" (str)
        """
        try:
            result = subprocess.run(
                ["netsh", "wlan", "show", "networks"],
                capture_output=True, text=True, timeout=10
            )
            networks = []
            current = {}
            for line in result.stdout.split("\n"):
                line = line.strip()
                if "SSID" in line and "BSSID" not in line:
                    if current.get("name"):
                        networks.append(current)
                    name = line.split(":", 1)[1].strip() if ":" in line else ""
                    current = {"name": name, "signal": "", "security": ""}
                elif "Signal" in line:
                    current["signal"] = line.split(":", 1)[1].strip() if ":" in line else ""
                elif "Authentication" in line:
                    current["security"] = line.split(":", 1)[1].strip() if ":" in line else ""
            if current.get("name"):
                networks.append(current)
            return networks
        except Exception:
            return []

    @staticmethod
    def connect_wifi(network_name: str, password: str = "") -> bool:
        """Connect to a Wi-Fi network (creates profile if password provided)."""
        try:
            import tempfile, os
            if password:
                xml = f"""<?xml version="1.0"?>
<WLANProfile xmlns="http://www.microsoft.com/networking/WLAN/profile/v1">
    <name>{network_name}</name>
    <SSIDConfig>
        <SSID>
            <name>{network_name}</name>
        </SSID>
    </SSIDConfig>
    <connectionType>ESS</connectionType>
    <connectionMode>auto</connectionMode>
    <MSM>
        <security>
            <authEncryption>
                <authentication>WPA2PSK</authentication>
                <encryption>AES</encryption>
                <useOneX>false</useOneX>
            </authEncryption>
            <sharedKey>
                <keyType>passPhrase</keyType>
                <protected>false</protected>
                <keyMaterial>{password}</keyMaterial>
            </sharedKey>
        </security>
    </MSM>
</WLANProfile>"""
                fd, path = tempfile.mkstemp(suffix=".xml")
                with os.fdopen(fd, "w") as f:
                    f.write(xml)
                subprocess.run(["netsh", "wlan", "add", "profile", f"filename={path}"], capture_output=True)
                os.unlink(path)
                
            result = subprocess.run(
                ["netsh", "wlan", "connect", f"name={network_name}"],
                capture_output=True, text=True, timeout=10
            )
            return result.returncode == 0
        except Exception:
            return False

    @staticmethod
    def _find_wifi_adapter_name() -> str:
        """Find the Wi-Fi adapter name from netsh output."""
        try:
            result = subprocess.run(
                ["netsh", "interface", "show", "interface"],
                capture_output=True, text=True, timeout=5
            )
            for line in result.stdout.split("\n"):
                low = line.lower()
                if "wi-fi" in low or "wifi" in low or "wireless" in low or "wlan" in low:
                    # Extract the interface name (last column)
                    parts = line.split()
                    if len(parts) >= 4:
                        return " ".join(parts[3:])
            return ""
        except Exception:
            return ""

    @staticmethod
    def is_wifi_enabled() -> bool:
        """Check if the Wi-Fi adapter is enabled."""
        try:
            result = subprocess.run(
                ["netsh", "interface", "show", "interface"],
                capture_output=True, text=True, timeout=5
            )
            for line in result.stdout.split("\n"):
                low = line.lower()
                if "wi-fi" in low or "wifi" in low or "wireless" in low or "wlan" in low:
                    # Admin State "Enabled" means adapter is on
                    return "enabled" in low
            return False
        except Exception:
            return False

    @staticmethod
    def set_wifi_enabled(enabled: bool) -> tuple[bool, str]:
        """Enable or disable the Wi-Fi adapter.
        
        Returns: (success: bool, message: str)
        Note: May require admin privileges.
        """
        action = "enable" if enabled else "disable"
        # Try the actual adapter name first
        actual_name = SystemControl._find_wifi_adapter_name()
        adapter_names = []
        if actual_name:
            adapter_names.append(actual_name)
        adapter_names.extend(["Wi-Fi", "WiFi", "Wireless Network Connection", "WLAN"])
        
        for name in adapter_names:
            try:
                result = subprocess.run(
                    ["netsh", "interface", "set", "interface", name, f"admin={action}"],
                    capture_output=True, text=True, timeout=10
                )
                if result.returncode == 0:
                    return True, f"Wi-Fi {action}d successfully"
            except Exception:
                continue
        
        # Fallback: try PowerShell
        try:
            ps_action = "Enable" if enabled else "Disable"
            result = subprocess.run(
                ["powershell", "-Command",
                 f"Get-NetAdapter -Name '*Wi*','*Wireless*','*WLAN*','*WiFi*' | "
                 f"{ps_action}-NetAdapter -Confirm:$false"],
                capture_output=True, text=True, timeout=10
            )
            if result.returncode == 0:
                return True, f"Wi-Fi {action}d successfully"
            else:
                return False, f"Failed: may need admin rights. {result.stderr.strip()}"
        except Exception as e:
            return False, f"Could not {action} Wi-Fi: {e}"

    # ------------------------------------------------------------------
    # Bluetooth
    # ------------------------------------------------------------------

    @staticmethod
    def _find_bt_adapter_name() -> str:
        """Find the Bluetooth adapter's FriendlyName."""
        try:
            import tempfile, os
            # Write PS script to temp file to avoid $_ escaping issues
            script = (
                "Get-PnpDevice -Class Bluetooth "
                "| Where-Object { $_.FriendlyName -notmatch 'Enumerator|RFCOMM|Avrcp' "
                "-and $_.FriendlyName -match 'Bluetooth' "
                "-and $_.FriendlyName -match 'Radio|Adapter|Built.in|Broadcom|Intel|Realtek|Apple' } "
                "| Select-Object -First 1 -ExpandProperty FriendlyName"
            )
            fd, path = tempfile.mkstemp(suffix=".ps1")
            try:
                with os.fdopen(fd, "w") as f:
                    f.write(script)
                result = subprocess.run(
                    ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", path],
                    capture_output=True, text=True, timeout=10,
                )
                return result.stdout.strip()
            finally:
                os.unlink(path)
        except Exception:
            return ""

    @staticmethod
    def is_bluetooth_enabled() -> bool:
        """Check if Bluetooth is enabled."""
        try:
            name = SystemControl._find_bt_adapter_name()
            if not name:
                return False
            ps = (
                f"(Get-PnpDevice -FriendlyName '{name}' -Class Bluetooth "
                f"| Select-Object -First 1).Status"
            )
            result = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps],
                capture_output=True, text=True, timeout=10,
            )
            return result.stdout.strip().lower() == "ok"
        except Exception:
            return False

    @staticmethod
    def set_bluetooth_enabled(enabled: bool) -> tuple[bool, str]:
        """Enable or disable Bluetooth.
        
        Returns: (success: bool, message: str)
        Note: May require admin privileges.
        """
        action = "Enable" if enabled else "Disable"
        try:
            name = SystemControl._find_bt_adapter_name()
            if not name:
                return False, "Bluetooth adapter not found"
            ps = (
                f"Get-PnpDevice -FriendlyName '{name}' -Class Bluetooth "
                f"| {action}-PnpDevice -Confirm:$false"
            )
            result = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps],
                capture_output=True, text=True, timeout=15,
            )
            if result.returncode == 0:
                return True, f"Bluetooth {action.lower()}d"
            else:
                return False, f"Failed: may need admin rights. {result.stderr.strip()}"
        except Exception as e:
            return False, f"Could not toggle Bluetooth: {e}"

    @staticmethod
    def get_bluetooth_devices() -> list[dict]:
        """Get list of paired/connected Bluetooth devices.
        
        Returns: list of {"name": str, "status": str, "type": str}
        """
        try:
            import tempfile, os, json
            script = (
                "Get-PnpDevice -Class Bluetooth "
                "| Where-Object { $_.FriendlyName -notmatch "
                "'Radio|Adapter|Enumerator|Microsoft|Built.in|Broadcom|Intel|Realtek|Apple' "
                "-and $_.FriendlyName -ne 'Bluetooth' } "
                "| Select-Object FriendlyName, Status "
                "| ConvertTo-Json"
            )
            fd, path = tempfile.mkstemp(suffix=".ps1")
            try:
                with os.fdopen(fd, "w") as f:
                    f.write(script)
                result = subprocess.run(
                    ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", path],
                    capture_output=True, text=True, timeout=10,
                )
            finally:
                os.unlink(path)

            if result.returncode != 0 or not result.stdout.strip():
                return []

            data = json.loads(result.stdout)
            if isinstance(data, dict):
                data = [data]
            
            # Deduplicate by name, keeping "Connected" status if any entry is connected
            seen = {}
            for item in data:
                name = item.get("FriendlyName", "Unknown")
                status = item.get("Status", "Unknown")
                is_connected = status.lower() == "ok"
                if name not in seen or is_connected:
                    seen[name] = {
                        "name": name,
                        "status": "Connected" if is_connected else "Paired",
                        "type": "bluetooth",
                    }
            return list(seen.values())
        except Exception:
            return []

    # ------------------------------------------------------------------
    # Power
    # ------------------------------------------------------------------

    @staticmethod
    def shutdown() -> None:
        """Shutdown the computer."""
        subprocess.run(["shutdown", "/s", "/t", "0"])

    @staticmethod
    def restart() -> None:
        """Restart the computer."""
        subprocess.run(["shutdown", "/r", "/t", "0"])

    @staticmethod
    def sleep() -> None:
        """Put the computer to sleep."""
        subprocess.run(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"])

    @staticmethod
    def lock_screen() -> None:
        """Lock the screen."""
        import ctypes
        ctypes.windll.user32.LockWorkStation()

    @staticmethod
    def get_system_stats() -> dict:
        """Get CPU, RAM, Disk, and GPU usage via native Windows tools (PowerShell fallback)."""
        import time
        from datetime import datetime
        try:
            # Get CPU load percentage
            cpu_cmd = subprocess.run(["powershell", "-Command", "Get-CimInstance Win32_Processor | Select-Object -ExpandProperty LoadPercentage"], capture_output=True, text=True, timeout=3, creationflags=0x08000000)
            cpu = 0
            for line in cpu_cmd.stdout.split():
                if line.strip().isdigit():
                    cpu = int(line.strip())
                    break
            
            # Get RAM usage
            mem_cmd = subprocess.run(["powershell", "-Command", "Get-CimInstance Win32_OperatingSystem | Select-Object -Property FreePhysicalMemory,TotalVisibleMemorySize | ConvertTo-Json"], capture_output=True, text=True, timeout=3, creationflags=0x08000000)
            mem = 0
            import json
            data = json.loads(mem_cmd.stdout)
            free_mem = data.get("FreePhysicalMemory", 0)
            total_mem = data.get("TotalVisibleMemorySize", 1)
            mem = int(((total_mem - free_mem) / total_mem) * 100) if total_mem > 0 else 0
            
            # Get Disk RW Usage
            disk_cmd = subprocess.run(["powershell", "-Command", r"((Get-Counter '\PhysicalDisk(_Total)\% Disk Time').CounterSamples | Measure-Object -Property CookedValue -Average).Average"], capture_output=True, text=True, timeout=3, creationflags=0x08000000)
            disk = 0
            try:
                if disk_cmd.stdout.strip():
                    disk = int(float(disk_cmd.stdout.strip().replace(',', '.')))
            except Exception:
                pass
                
            # Get GPU Usage
            gpu_cmd = subprocess.run(["powershell", "-Command", r"((Get-Counter '\GPU Engine(*engtype_3D)\Utilization Percentage' -ErrorAction SilentlyContinue).CounterSamples | Measure-Object -Property CookedValue -Sum).Sum"], capture_output=True, text=True, timeout=3, creationflags=0x08000000)
            gpu = 0
            try:
                if gpu_cmd.stdout.strip():
                    gpu = int(float(gpu_cmd.stdout.strip().replace(',', '.')))
            except Exception:
                pass

            last_updated = datetime.now().strftime("%H:%M:%S")
            return {"cpu": cpu, "ram": mem, "disk": disk, "gpu": gpu, "last_updated": last_updated}
        except Exception as e:
            print(f"Stats Error: {e}")
            return {"cpu": 0, "ram": 0}

    @staticmethod
    def get_local_videos() -> list:
        """Find local videos in the Videos folder."""
        try:
            from pathlib import Path
            import os
            videos = []
            home = Path.home()
            for v_dir in [home / "Videos", home / "Downloads"]:
                if v_dir.exists():
                    for f in v_dir.rglob("*"):
                        if f.is_file() and f.suffix.lower() in [".mp4", ".mkv", ".avi", ".mov"]:
                            videos.append({
                                "name": f.stem,
                                "path": str(f)
                            })
            return videos
        except Exception:
            return []

    @staticmethod
    def play_local_video(path: str) -> None:
        """Launch video in default player."""
        import os
        os.startfile(path)

    @staticmethod
    def set_display_scaling_100() -> None:
        """Force Windows display scaling to 100% and restart explorer."""
        script = (
            'Set-ItemProperty -Path "HKCU:\\Control Panel\\Desktop" -Name "LogPixels" -Value 96;'
            'Set-ItemProperty -Path "HKCU:\\Control Panel\\Desktop" -Name "Win8DpiScaling" -Value 1;'
            'Stop-Process -Name explorer -Force;'
            'Start-Process explorer.exe'
        )
        subprocess.run(["powershell", "-WindowStyle", "Hidden", "-Command", script], creationflags=0x08000000)

    # ------------------------------------------------------------------
    # Disk & VLC
    # ------------------------------------------------------------------

    @staticmethod
    def get_disk_usage() -> int:
        """Get disk usage percentage of the system drive."""
        import shutil
        total, used, free = shutil.disk_usage('C:\\')
        return int((used / total) * 100)

    @staticmethod
    def launch_vlc(file_path: str) -> bool:
        """Launch VLC in fullscreen with HTTP interface enabled."""
        import os
        vlc_paths = [
            r'C:\Program Files\VideoLAN\VLC\vlc.exe',
            r'C:\Program Files (x86)\VideoLAN\VLC\vlc.exe',
        ]
        vlc = None
        for p in vlc_paths:
            if os.path.exists(p):
                vlc = p
                break
        if not vlc:
            return False
        # Launch VLC with HTTP interface on port 9090, password 'minipc'
        subprocess.Popen([
            vlc, file_path,
            '--fullscreen',
            '--extraintf', 'http',
            '--http-host', '127.0.0.1',
            '--http-port', '9090',
            '--http-password', 'minipc',
        ], creationflags=0x08000000)
        return True

    @staticmethod
    def vlc_command(command: str, val: str = '') -> dict:
        """Send command to VLC HTTP interface."""
        import urllib.request
        import base64
        try:
            url = f'http://127.0.0.1:9090/requests/status.json?command={command}'
            if val:
                url += f'&val={val}'
            auth = base64.b64encode(b':minipc').decode()
            req = urllib.request.Request(url, headers={'Authorization': f'Basic {auth}'})
            with urllib.request.urlopen(req, timeout=2) as resp:
                import json
                return json.loads(resp.read().decode('utf-8'))
        except Exception as e:
            return {'error': str(e)}

    @staticmethod
    def vlc_get_status() -> dict:
        """Get VLC current playback status."""
        import urllib.request
        import base64
        try:
            url = 'http://127.0.0.1:9090/requests/status.json'
            auth = base64.b64encode(b':minipc').decode()
            req = urllib.request.Request(url, headers={'Authorization': f'Basic {auth}'})
            with urllib.request.urlopen(req, timeout=2) as resp:
                import json
                data = json.loads(resp.read().decode('utf-8'))
                # Extract useful info
                info = data.get('information', {}).get('category', {}).get('meta', {})
                stream0 = data.get('information', {}).get('category', {}).get('Stream 0', {})
                return {
                    'state': data.get('state', 'stopped'),
                    'time': data.get('time', 0),
                    'length': data.get('length', 0),
                    'position': data.get('position', 0),
                    'volume': data.get('volume', 256),
                    'filename': info.get('filename', ''),
                    'resolution': stream0.get('Resolution', ''),
                    'codec': stream0.get('Codec', ''),
                }
        except Exception:
            return {'state': 'stopped', 'time': 0, 'length': 0}

    @staticmethod
    def vlc_stop() -> bool:
        """Stop VLC and close it."""
        try:
            subprocess.run(['taskkill', '/F', '/IM', 'vlc.exe'], capture_output=True, creationflags=0x08000000)
            return True
        except Exception:
            return False

    @staticmethod
    def get_audio_info() -> dict:
        """Get audio device channel info."""
        info = {'channels': 2, 'config': 'Stereo', 'device': 'Unknown'}
        try:
            from ctypes import cast, POINTER
            from comtypes import CLSCTX_ALL
            import pythoncom
            pythoncom.CoInitialize()
            from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
            devices = AudioUtilities.GetSpeakers()
            interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
            volume = cast(interface, POINTER(IAudioEndpointVolume))
            ch = volume.GetChannelCount()
            info['channels'] = ch
            if ch >= 8: info['config'] = '7.1 Surround'
            elif ch >= 6: info['config'] = '5.1 Surround'
            else: info['config'] = 'Stereo'
        except Exception as e:
            info['error'] = str(e)
        return info

    @staticmethod
    def open_speaker_config():
        """Open the Windows Sound speaker configuration dialog."""
        subprocess.Popen(['control', 'mmsys.cpl', ',0'], creationflags=0x08000000)
