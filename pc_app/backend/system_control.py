"""System control utilities for Windows."""
import subprocess


def _get_volume_interface():
    """Get the IAudioEndpointVolume interface."""
    import pythoncom
    pythoncom.CoInitialize()
    from pycaw.pycaw import AudioUtilities
    speakers = AudioUtilities.GetSpeakers()
    return speakers.EndpointVolume


def _get_policy_config():
    """Get the IPolicyConfig COM interface to set the default audio endpoint."""
    from ctypes import cast, POINTER, HRESULT, c_wchar_p
    from comtypes import GUID, IUnknown, COMMETHOD, CoCreateInstance
    import pythoncom
    
    # IPolicyConfig has several versions. This one (IPolicyConfigVista) works well for setting defaults.
    # IPolicyConfig interface ID: 87CE5498-68D6-44E5-9215-6DA47EF883D8
    class IPolicyConfig(IUnknown):
        _iid_ = GUID("{87CE5498-68D6-44E5-9215-6DA47EF883D8}")
        _methods_ = [
            COMMETHOD([], HRESULT, 'GetMixFormat', (['in'], c_wchar_p, 'pwstrId'), (['out'], POINTER(c_wchar_p), 'ppFormat')),
            COMMETHOD([], HRESULT, 'GetDeviceFormat', (['in'], c_wchar_p, 'pwstrId'), (['in'], c_wchar_p, 'bDefault'), (['out'], POINTER(c_wchar_p), 'ppFormat')),
            COMMETHOD([], HRESULT, 'ResetDeviceFormat', (['in'], c_wchar_p, 'pwstrId')),
            COMMETHOD([], HRESULT, 'SetDeviceFormat', (['in'], c_wchar_p, 'pwstrId'), (['in'], POINTER(c_wchar_p), 'pEndpointFormat'), (['in'], POINTER(c_wchar_p), 'pMixFormat')),
            COMMETHOD([], HRESULT, 'GetProcessingPeriod', (['in'], c_wchar_p, 'pwstrId'), (['in'], c_wchar_p, 'bDefault'), (['out'], POINTER(c_wchar_p), 'pmftDefaultPeriod'), (['out'], POINTER(c_wchar_p), 'pmftMinimumPeriod')),
            COMMETHOD([], HRESULT, 'SetProcessingPeriod', (['in'], c_wchar_p, 'pwstrId'), (['in'], POINTER(c_wchar_p), 'pmftPeriod')),
            COMMETHOD([], HRESULT, 'GetShareMode', (['in'], c_wchar_p, 'pwstrId'), (['out'], POINTER(c_wchar_p), 'pMode')),
            COMMETHOD([], HRESULT, 'SetShareMode', (['in'], c_wchar_p, 'pwstrId'), (['in'], POINTER(c_wchar_p), 'mode')),
            COMMETHOD([], HRESULT, 'GetPropertyValue', (['in'], c_wchar_p, 'pwstrId'), (['in'], c_wchar_p, 'bFxStore'), (['in'], POINTER(c_wchar_p), 'key'), (['out'], POINTER(c_wchar_p), 'pv')),
            COMMETHOD([], HRESULT, 'SetPropertyValue', (['in'], c_wchar_p, 'pwstrId'), (['in'], c_wchar_p, 'bFxStore'), (['in'], POINTER(c_wchar_p), 'key'), (['in'], POINTER(c_wchar_p), 'pv')),
            COMMETHOD([], HRESULT, 'SetDefaultEndpoint', (['in'], c_wchar_p, 'pwstrId'), (['in'], c_wchar_p, 'role')),
            COMMETHOD([], HRESULT, 'SetEndpointVisibility', (['in'], c_wchar_p, 'pwstrId'), (['in'], c_wchar_p, 'bVisible'))
        ]
    
    pythoncom.CoInitialize()
    # CLSID_PolicyConfig: 870AF99C-171D-4F9E-AF0D-E63DF40C2BC9
    return CoCreateInstance(GUID("{870AF99C-171D-4F9E-AF0D-E63DF40C2BC9}"), IPolicyConfig, pythoncom.CLSCTX_ALL)


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
        """Set the Windows master (main) volume only.

        Per-app levels in the volume mixer are left untouched; use
        set_app_volume() to change those individually.
        """
        clamped = max(0, min(100, value))
        vol_float = clamped / 100.0
        try:
            volume = _get_volume_interface()
            volume.SetMasterVolumeLevelScalar(vol_float, None)
        except Exception as e:
            print(f"Failed to set master volume: {e}")

    # ------------------------------------------------------------------
    # Volume mixer (per-app volume)
    # ------------------------------------------------------------------

    @staticmethod
    def _session_key(session) -> tuple[str, str]:
        """Return (key, display_name) for an audio session.

        Apps can own several sessions (e.g. browsers), so sessions are grouped
        by executable name and changed together.
        """
        proc = getattr(session, "Process", None)
        if proc is None:
            return "__system__", "System Sounds"
        try:
            exe = proc.name()
        except Exception:
            exe = f"pid{getattr(session, 'ProcessId', 0)}"
        name = exe[:-4] if exe.lower().endswith(".exe") else exe
        return exe.lower(), (name[:1].upper() + name[1:]) if name else exe

    @staticmethod
    def get_mixer() -> dict:
        """List per-app volumes for the current output device."""
        apps: dict[str, dict] = {}
        try:
            import pythoncom
            pythoncom.CoInitialize()
            from pycaw.pycaw import AudioUtilities
            for session in AudioUtilities.GetAllSessions():
                vol = session.SimpleAudioVolume
                if not vol:
                    continue
                key, display = SystemControl._session_key(session)
                if key in apps:
                    continue
                apps[key] = {
                    "key": key,
                    "name": display,
                    "volume": int(round(vol.GetMasterVolume() * 100)),
                    "muted": bool(vol.GetMute()),
                }
        except Exception as e:
            print(f"Failed to read volume mixer: {e}")
        ordered = sorted(apps.values(), key=lambda a: (a["key"] != "__system__", a["name"].lower()))
        return {"master": SystemControl.get_volume(), "apps": ordered}

    @staticmethod
    def set_app_volume(key: str, value: int) -> bool:
        """Set the volume of every session belonging to one app (0-100)."""
        vol_float = max(0, min(100, value)) / 100.0
        changed = False
        try:
            import pythoncom
            pythoncom.CoInitialize()
            from pycaw.pycaw import AudioUtilities
            for session in AudioUtilities.GetAllSessions():
                vol = session.SimpleAudioVolume
                if vol and SystemControl._session_key(session)[0] == key:
                    vol.SetMasterVolume(vol_float, None)
                    changed = True
        except Exception as e:
            print(f"Failed to set app volume: {e}")
        return changed

    @staticmethod
    def reset_app_volumes() -> int:
        """Put every app in the mixer back to 100%. Returns sessions changed."""
        count = 0
        try:
            import pythoncom
            pythoncom.CoInitialize()
            from pycaw.pycaw import AudioUtilities
            for session in AudioUtilities.GetAllSessions():
                vol = session.SimpleAudioVolume
                if vol:
                    vol.SetMasterVolume(1.0, None)
                    count += 1
        except Exception as e:
            print(f"Failed to reset app volumes: {e}")
        return count

    # ------------------------------------------------------------------
    # Sound output device
    # ------------------------------------------------------------------

    @staticmethod
    def get_output_devices() -> list[dict]:
        """List active playback devices and which one is the default."""
        devices = []
        try:
            import pythoncom
            pythoncom.CoInitialize()
            from pycaw.pycaw import AudioUtilities
            enumerator = AudioUtilities.GetDeviceEnumerator()
            default_id = ""
            try:
                # eRender=0, eMultimedia=1
                default_id = enumerator.GetDefaultAudioEndpoint(0, 1).GetId()
            except Exception:
                pass
            # eRender=0, DEVICE_STATE_ACTIVE=1
            collection = enumerator.EnumAudioEndpoints(0, 1)
            for i in range(collection.GetCount()):
                dev = AudioUtilities.CreateDevice(collection.Item(i))
                devices.append({
                    "id": dev.id,
                    "name": dev.FriendlyName or "Unknown device",
                    "default": dev.id == default_id,
                })
        except Exception as e:
            print(f"Failed to list output devices: {e}")
        return devices

    @staticmethod
    def set_output_device(device_id: str) -> bool:
        """Make a playback device the Windows default for all roles."""
        try:
            import pythoncom
            pythoncom.CoInitialize()
            policy = _get_policy_config()
            for role in (0, 1, 2):  # eConsole, eMultimedia, eCommunications
                policy.SetDefaultEndpoint(device_id, role)
            return True
        except Exception as e:
            print(f"Failed to set output device: {e}")
            return False

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
    def reinstall_spotify() -> None:
        """Automatically uninstall and reinstall Spotify."""
        import os
        import urllib.request
        
        try:
            # Kill Spotify if running
            subprocess.run(["taskkill", "/F", "/IM", "Spotify.exe"], capture_output=True)
            subprocess.run(["taskkill", "/F", "/IM", "spotify_cli.exe"], capture_output=True)
            
            # Uninstall silently
            uninstaller = os.path.expandvars(r"%APPDATA%\Spotify\uninstall.exe")
            if os.path.exists(uninstaller):
                subprocess.run([uninstaller, "/S"], capture_output=True)
                
            # Wait for uninstall to complete
            import time
            time.sleep(3)
            
            # Download new setup
            setup_path = os.path.expandvars(r"%TEMP%\SpotifySetup.exe")
            urllib.request.urlretrieve("https://download.scdn.co/SpotifySetup.exe", setup_path)
            
            # Run installer (it will auto-launch Spotify when done)
            subprocess.run([setup_path], creationflags=0x08000000)
            
        except Exception as e:
            print(f"Spotify reinstall failed: {e}")

    @staticmethod
    def lock_screen() -> None:
        """Lock the screen."""
        import ctypes
        ctypes.windll.user32.LockWorkStation()

    @staticmethod
    def get_system_stats() -> dict:
        """Get CPU, RAM, Disk, and GPU usage via psutil."""
        from datetime import datetime
        try:
            import psutil
            cpu = int(psutil.cpu_percent())
            mem = int(psutil.virtual_memory().percent)
            disk = int(psutil.disk_usage('C:\\').percent)
            gpu = 0
            last_updated = datetime.now().strftime("%H:%M:%S")
            return {"cpu": cpu, "ram": mem, "disk": disk, "gpu": gpu, "last_updated": last_updated}
        except Exception as e:
            print(f"Stats Error: {e}")
            return {"cpu": 0, "ram": 0, "disk": 0, "gpu": 0}



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

    @staticmethod
    def get_disk_usage() -> int:
        """Get disk usage percentage of the system drive."""
        import shutil
        total, used, free = shutil.disk_usage('C:\\')
        return int((used / total) * 100)

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
