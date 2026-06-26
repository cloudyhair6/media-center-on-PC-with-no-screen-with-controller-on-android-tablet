@echo off
echo ===========================================================
echo Media Controller (it is called Minipc) Tablet APK Installer
echo ===========================================================
echo Ensure your tablet is connected via USB.
pause
echo Installing APK...
platform-tools\adb.exe install -r app.apk
echo.
echo Launching App...
platform-tools\adb.exe shell am start -n com.minipc.kiosk/.MainActivity
echo Done!
pause
