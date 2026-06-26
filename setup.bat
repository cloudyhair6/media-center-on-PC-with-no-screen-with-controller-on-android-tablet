@echo off
echo ===================================================
echo Setting up MiniPC Media Center Environment
echo ===================================================

echo [1/4] Clearing Cache...
pip cache purge

echo.
echo [2/4] Creating Python Virtual Environment (venv)...
python -m venv venv
if errorlevel 1 (
    echo [ERROR] Failed to create virtual environment. Ensure Python is installed and in PATH.
    pause
    exit /b 1
)

echo.
echo [3/4] Upgrading PIP...
call venv\Scripts\activate.bat
python -m pip install --upgrade pip

echo.
echo [4/4] Installing Required Packages...
pip install -r pc_app\requirements.txt
if errorlevel 1 (
    echo [ERROR] Failed to install packages. Check requirements.txt.
    pause
    exit /b 1
)

echo.
echo ===================================================
echo SETUP COMPLETE!
echo You can now use "start.bat" to run the application.
echo ===================================================
pause
