@echo off
echo =========================================
echo MiniPC Tablet APK Installer
echo =========================================
echo Ensure your tablet is connected via USB.
pause
echo Installing APK...
..\development\platform-tools\adb.exe install -r app.apk
echo.
echo Launching App...
..\development\platform-tools\adb.exe shell am start -n com.minipc.kiosk/.MainActivity
echo Done!
pause
