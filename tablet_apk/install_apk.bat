@echo off
echo ===========================================================
echo Media Centre Tablet APK Installer
echo ===========================================================
echo Ensure your tablet is connected via USB.
pause
echo Installing APK...
..\development\platform-tools\adb.exe uninstall com.mediacentre.kiosk
..\development\platform-tools\adb.exe install -r app.apk
echo.
echo Launching App...
..\development\platform-tools\adb.exe shell am start -n com.mediacentre.kiosk/.MainActivity
echo Done!
pause
