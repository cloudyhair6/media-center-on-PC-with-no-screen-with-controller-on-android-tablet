@echo off
if defined VIRTUAL_ENV (
    echo A virtual environment is active. Deactivating now...
    call deactivate
) else (
    echo No virtual environment is currently active.
)
echo Checking for updates...
python installer_and_updater.py --update-check --relaunch "start.bat"

cd pc_app

echo [Media Centre] Starting...

REM Kill explorer to remove taskbar and free up memory
taskkill /f /im explorer.exe >nul 2>&1

REM Pre-compile Python for faster loading from HDD
python -m compileall -q "%~dp0" >nul 2>&1

REM Create and activate virtual environment to prevent package collisions
if not exist "%~dp0venv\Scripts\activate.bat" (
    echo Creating virtual environment...
    python -m venv "%~dp0venv"
)
call "%~dp0venv\Scripts\activate.bat"

python -m pip install --upgrade pip --no-cache-dir
python -m pip install -r requirements.txt --no-cache-dir

REM Start the app
cd /d "%~dp0pc_app"
python main.py

REM Restore explorer when app exits
start explorer.exe
cd ..
if defined VIRTUAL_ENV (
    echo A virtual environment is active. Deactivating now...
    call deactivate
) else (
    echo No virtual environment is currently active.
)
