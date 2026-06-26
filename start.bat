@echo off
echo Checking for updates...
python installer_and_updater.py --update-check --relaunch "start.bat"

cd pc_app
echo [MiniPC] Starting...

REM Kill explorer to remove taskbar and free up memory
taskkill /f /im explorer.exe >nul 2>&1

REM Pre-compile Python for faster loading from HDD
python -m compileall -q "%~dp0" >nul 2>&1

REM Activate virtual environment if it exists
if exist "%~dp0venv\Scripts\activate.bat" call "%~dp0venv\Scripts\activate.bat"

REM Start the app
cd /d "%~dp0pc_app"
python main.py

REM Restore explorer when app exits
start explorer.exe
cd ..