@echo off
set SDKROOT=C:\Users\Will\OneDrive\github\copilot cli\minipc\android-sdk
set SDKMANAGER=%SDKROOT%\cmdline-tools\latest\bin\sdkmanager.bat
echo y| "%SDKMANAGER%" --sdk_root="%SDKROOT%" "platforms;android-10" "build-tools;29.0.3"
