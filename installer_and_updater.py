import sys
import subprocess
import json
import urllib.request
import urllib.error
import urllib.parse
import os
import argparse
import threading
import shutil
import concurrent.futures
import ssl
try:
    import certifi
    # Force urllib/ssl to use certifi's trusted certificate bundle
    ssl_context = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    ssl_context = None

# --- Dynamic GUI Installation ---
def install_pyqt5_if_missing():
    try:
        import PyQt5
    except ImportError:
        print("PyQt5 is missing. Installing it now...")
        import tkinter as tk
        from tkinter import ttk
        
        root = tk.Tk()
        root.title("Installing UI Components")
        root.geometry("400x150")
        root.resizable(False, False)
        
        root.eval('tk::PlaceWindow . center')
        
        lbl = tk.Label(root, text="Installing modern UI components (PyQt5)...\nPlease wait.", font=("Arial", 10))
        lbl.pack(pady=20)
        
        progress = ttk.Progressbar(root, mode='indeterminate')
        progress.pack(fill=tk.X, padx=20, pady=10)
        progress.start(10)
        
        def run_install():
            subprocess.run([sys.executable, "-m", "pip", "install", "PyQt5"], 
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            root.after(0, root.destroy)
            
        threading.Thread(target=run_install, daemon=True).start()
        root.mainloop()

# Run the installer check before doing anything else
install_pyqt5_if_missing()

from PyQt5.QtWidgets import (QApplication, QWidget, QVBoxLayout, QProgressBar, 
                             QLabel, QCheckBox, QPushButton, QMessageBox, QScrollArea, QDesktopWidget)
from PyQt5.QtCore import Qt, QThread, pyqtSignal

# --- Config and Constants ---
CONFIG_FILE = "updater_config.json"
OWNER = "cloudyhair6"
REPO = "media-center-on-PC-with-no-screen-with-controller-on-android-tablet"
USER_AGENT = "Python-Latest-Version-Detector"

def load_config(target_dir=None):
    cfg_file = os.path.join(target_dir, CONFIG_FILE) if target_dir else CONFIG_FILE
    if os.path.exists(cfg_file):
        try:
            with open(cfg_file, "r") as f:
                return json.load(f)
        except json.JSONDecodeError:
            return {}
    return {}

def save_config(config, target_dir=None):
    cfg_file = os.path.join(target_dir, CONFIG_FILE) if target_dir else CONFIG_FILE
    with open(cfg_file, "w") as f:
        json.dump(config, f, indent=4)

# --- GitHub API Helpers ---
def get_auth_headers():
    headers = {'User-Agent': USER_AGENT}
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        headers['Authorization'] = f"token {token}"
    return headers

def get_latest_release_version():
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/releases/latest"
    req = urllib.request.Request(url, headers=get_auth_headers())
    try:
        with urllib.request.urlopen(req, context=ssl_context) as response:
            if response.status == 200:
                data = json.loads(response.read().decode('utf-8'))
                return data.get('tag_name', None)
    except urllib.error.HTTPError as e:
        if e.code == 403:
            raise Exception("GitHub API Rate Limit Exceeded (403).\nYou have made too many requests. Please wait a while before trying again, or set the GITHUB_TOKEN environment variable.")
        print(f"Error fetching latest release: {e}")
    except Exception as e:
        print(f"Error fetching latest release: {e}")
    return None

def get_repo_tree(tag):
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/git/trees/{tag}?recursive=1"
    req = urllib.request.Request(url, headers=get_auth_headers())
    try:
        with urllib.request.urlopen(req, context=ssl_context) as response:
            if response.status == 200:
                data = json.loads(response.read().decode('utf-8'))
                return data.get('tree', [])
    except Exception as e:
        print(f"Error fetching tree: {e}")
    return []

def download_file_content(path, tag):
    url = f"https://raw.githubusercontent.com/{OWNER}/{REPO}/{tag}/{urllib.parse.quote(path)}"
    req = urllib.request.Request(url, headers=get_auth_headers())
    try:
        with urllib.request.urlopen(req, context=ssl_context) as response:
            if response.status == 200:
                return response.read()
    except urllib.error.HTTPError as e:
        if e.code == 403:
            print(f"GitHub API Rate Limit Exceeded when downloading {path}.")
        else:
            print(f"Error downloading {path}: {e}")
    except Exception as e:
        print(f"Error downloading {path}: {e}")
    return None

def fetch_download_options(tag):
    content = download_file_content("download_options.json", tag)
    if content:
        try:
            return json.loads(content.decode('utf-8'))
        except json.JSONDecodeError:
            pass
    return None

def extract_files_from_option(option_data):
    if isinstance(option_data, dict):
        return option_data.get("Files", [])
    elif isinstance(option_data, list):
        return option_data
    return []
    
def extract_message_from_option(option_data):
    if isinstance(option_data, dict):
        return option_data.get("Message", None)
    return None

# --- Download Logic ---
def resolve_files_to_download(paths, tree_objects):
    files = []
    for path in paths:
        is_dir = path.endswith('/')
        path_clean = path.rstrip('/')
        
        for obj in tree_objects:
            if obj['type'] == 'blob':
                if is_dir and obj['path'].startswith(path_clean + '/'):
                    files.append(obj['path'])
                elif not is_dir and obj['path'] == path_clean:
                    files.append(obj['path'])
    return list(set(files))

def perform_download_concurrently(files, tag, install_dir, progress_callback=None):
    os.makedirs(install_dir, exist_ok=True)
    total = len(files)
    completed = 0
    lock = threading.Lock()

    def download_task(filepath):
        local_path = os.path.join(install_dir, filepath.replace('/', os.sep))
        os.makedirs(os.path.dirname(local_path), exist_ok=True)
        
        content = download_file_content(filepath, tag)
        if content:
            with open(local_path, "wb") as f:
                f.write(content)
        else:
            print(f"Failed to download: {filepath}")
            
        nonlocal completed
        with lock:
            completed += 1
            if progress_callback:
                progress_callback(completed, total, filepath)

    # Use ThreadPoolExecutor to speed up downloads
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        futures = [executor.submit(download_task, f) for f in files]
        concurrent.futures.wait(futures)

    if progress_callback:
        progress_callback(total, total, "Done!")

# --- Process & Deletion Logic ---
def kill_other_python_processes():
    current_pid = os.getpid()
    print(f"Current PID is {current_pid}. Checking for running Media Centre python processes...")
    try:
        import psutil
        base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        target_scripts = ("main.py", "remote_server.py", "run.py", "installer_and_updater.py")
        for proc in psutil.process_iter(['pid', 'name', 'cmdline', 'cwd']):
            try:
                if proc.info['pid'] == current_pid:
                    continue
                pname = (proc.info['name'] or '').lower()
                if pname in ("python.exe", "pythonw.exe"):
                    cmdline_str = " ".join(proc.info['cmdline'] or []).lower()
                    cwd_str = (proc.info.get('cwd') or '').lower()
                    if base_dir.lower() in cwd_str or any(ts in cmdline_str for ts in target_scripts):
                        print(f"Killing Media Centre process with PID {proc.info['pid']}...")
                        proc.kill()
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
    except Exception as e:
        print(f"psutil check unavailable or failed ({e}). Using tasklist fallback...")
        try:
            output = subprocess.check_output('tasklist /FI "IMAGENAME eq python.exe" /FO CSV /NH', shell=True).decode(errors='ignore')
            for line in output.strip().splitlines():
                parts = [p.strip(' "') for p in line.split('","')]
                if len(parts) >= 2 and parts[1].isdigit():
                    pid = int(parts[1])
                    if pid != current_pid:
                        try:
                            subprocess.run(["taskkill", "/F", "/PID", str(pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                            print(f"Killed other python process PID {pid}")
                        except Exception:
                            pass
        except Exception:
            pass

def kill_parent_batch_script():
    """Finds all shell/batch ancestors (cmd.exe, powershell.exe, conhost.exe, py.exe)
    and terminates them and their console window so start.bat cannot continue.
    """
    import ctypes
    from ctypes import wintypes

    # 1. Close the console window immediately if one exists
    try:
        kernel32 = ctypes.windll.kernel32
        hwnd = kernel32.GetConsoleWindow()
        if hwnd:
            user32 = ctypes.windll.user32
            user32.PostMessageW(hwnd, 0x0010, 0, 0)  # WM_CLOSE
    except Exception:
        pass

    # 2. Traverse ancestors up to explorer.exe using Win32 Toolhelp snapshot
    try:
        class PROCESSENTRY32(ctypes.Structure):
            _fields_ = [
                ('dwSize', wintypes.DWORD),
                ('cntUsage', wintypes.DWORD),
                ('th32ProcessID', wintypes.DWORD),
                ('th32DefaultHeapID', ctypes.c_size_t),
                ('th32ModuleID', wintypes.DWORD),
                ('cntThreads', wintypes.DWORD),
                ('th32ParentProcessID', wintypes.DWORD),
                ('pcPriClassBase', wintypes.LONG),
                ('dwFlags', wintypes.DWORD),
                ('szExeFile', ctypes.c_char * 260)
            ]

        kernel32 = ctypes.windll.kernel32
        hSnapshot = kernel32.CreateToolhelp32Snapshot(0x00000002, 0)  # TH32CS_SNAPPROCESS
        entry = PROCESSENTRY32()
        entry.dwSize = ctypes.sizeof(PROCESSENTRY32)

        parents = {}
        names = {}
        if kernel32.Process32First(hSnapshot, ctypes.byref(entry)):
            while True:
                parents[entry.th32ProcessID] = entry.th32ParentProcessID
                names[entry.th32ProcessID] = entry.szExeFile.decode(errors='ignore').lower()
                if not kernel32.Process32Next(hSnapshot, ctypes.byref(entry)):
                    break
        kernel32.CloseHandle(hSnapshot)

        pids_to_kill = []
        cur = os.getpid()
        while cur in parents and parents[cur] != 0:
            p_pid = parents[cur]
            p_name = names.get(p_pid, '')
            if p_name in ('explorer.exe', 'services.exe', 'wininit.exe', ''):
                break
            if any(sh in p_name for sh in ('cmd.exe', 'powershell.exe', 'pwsh.exe', 'py.exe', 'conhost.exe')):
                pids_to_kill.append(p_pid)
            cur = p_pid

        for pid in pids_to_kill:
            try:
                subprocess.run(["taskkill", "/F", "/PID", str(pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except Exception:
                pass
    except Exception:
        try:
            parent_pid = os.getppid()
            subprocess.run(["taskkill", "/F", "/PID", str(parent_pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass

def robust_rmtree(path):
    for root, dirs, files in os.walk(path, topdown=False):
        for name in files:
            file_path = os.path.join(root, name)
            try:
                os.remove(file_path)
                print(f"  -> Deleted: {file_path}")
            except Exception:
                print(f"  -> Skipped locked/in-use item: {file_path}")
        for name in dirs:
            dir_path = os.path.join(root, name)
            try:
                os.rmdir(dir_path)
                print(f"  -> Deleted directory: {dir_path}")
            except Exception:
                pass
    try:
        os.rmdir(path)
        print(f"  -> Deleted directory: {path}")
    except Exception:
        pass

def clear_install_directory(install_dir, files_to_download=None, progress_callback=None):
    script_name = os.path.basename(os.path.abspath(__file__)).lower()
    config_name = CONFIG_FILE.lower()
    
    if not os.path.exists(install_dir):
        return
        
    items_to_delete = []
    # Protect these core files and folders from being deleted
    protected_items = [config_name, ".git"]
    
    # If this script is running directly from install_dir (e.g. GUI mode), protect it
    if os.path.abspath(os.path.dirname(__file__)).lower() == os.path.abspath(install_dir).lower():
        protected_items.append(script_name)
    elif files_to_download:
        # Running from temp: only protect installer_and_updater.py if it is NOT in files_to_download
        download_basenames = [os.path.basename(f).lower() for f in files_to_download]
        if "installer_and_updater.py" not in download_basenames:
            protected_items.append("installer_and_updater.py")
    
    for item in os.listdir(install_dir):
        if item.lower() in protected_items:
            continue
        items_to_delete.append(item)
        
    total = len(items_to_delete)
    if total == 0:
        return
        
    print(f"Found {total} items to delete.")
    for idx, item in enumerate(items_to_delete):
        item_path = os.path.join(install_dir, item)
        print(f"Deleting top-level item: {item_path}")
        
        try:
            if os.path.isdir(item_path):
                robust_rmtree(item_path)
            else:
                os.remove(item_path)
                print(f"  -> Deleted: {item_path}")
        except Exception as e:
            print(f"  -> Skipped locked/in-use item: {item_path}")
            
        if progress_callback:
            progress_callback(idx + 1, total, f"Deleted {item}")

# --- Auto-Updater Routine ---
def run_update_check(relaunch_script=None, test_mode=False, install_dir=None, is_temp_runner=False):
    # Determine the actual target installation directory
    if not install_dir:
        if os.path.exists(os.path.join(os.getcwd(), CONFIG_FILE)):
            install_dir = os.getcwd()
        else:
            install_dir = os.path.dirname(os.path.abspath(__file__))
    install_dir = os.path.abspath(install_dir)
    
    config = load_config(install_dir)
    current_version = config.get('current_version')
    selected_options = config.get('selected_options', [])
    
    if 'selected_option' in config and not selected_options:
        selected_options = [config['selected_option']]

    if not current_version or not selected_options:
        print("No previous installation found. Cannot check for updates.")
        return
        
    print("Checking for updates...")
    try:
        latest_version = get_latest_release_version()
    except Exception as e:
        print(f"Failed to check for updates: {e}")
        return
        
    if not latest_version:
        print("Failed to get latest version from GitHub.")
        return
        
    if latest_version == current_version:
        print(f"Already on the latest version ({latest_version}).")
        return
        
    print(f"New version found: {latest_version}. Updating...")
    
    # --- DETACHMENT LOGIC: RUN FROM RAM / TEMP ---
    # Running from temp guarantees:
    # 1. The updater survives the termination of start.bat and all python processes.
    # 2. installer_and_updater.py in install_dir is NOT in use/locked and can be updated!
    if not test_mode and not is_temp_runner:
        print("Launching detached updater from RAM/temp in a new window...")
        import tempfile
        import time
        
        temp_dir = tempfile.gettempdir()
        temp_script_path = os.path.join(temp_dir, f"updater_ram_{os.getpid()}_{int(time.time())}.py")
        try:
            with open(os.path.abspath(__file__), "r", encoding="utf-8") as f_src:
                src_code = f_src.read()
            with open(temp_script_path, "w", encoding="utf-8") as f_dst:
                f_dst.write(src_code)
        except Exception as e:
            print(f"Warning: Could not create temp script ({e}). Falling back to current script.")
            temp_script_path = os.path.abspath(__file__)
            
        python_exe = sys.executable
        if hasattr(sys, 'real_prefix') or (hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix):
            possible_global = os.path.join(sys.base_prefix, 'python.exe')
            if os.path.exists(possible_global):
                print(f"Detected Virtual Environment. Relaunching globally via: {possible_global}")
                python_exe = possible_global
                
        cmd = [
            python_exe,
            temp_script_path,
            "--update-check",
            "--install-dir", install_dir,
            "--is-temp-runner"
        ]
        if relaunch_script:
            cmd.extend(["--relaunch", relaunch_script])
            
        subprocess.Popen(cmd, creationflags=subprocess.CREATE_NEW_CONSOLE)
        
        print("Stopping the parent batch script and closing window...")
        kill_parent_batch_script()
        sys.exit(0)
    # -------------------------------------------
    
    download_options = fetch_download_options(latest_version)
    if not download_options:
        print("Could not fetch download_options.json from the latest release. Update failed.")
        return
        
    missing_options = [opt for opt in selected_options if opt not in download_options]
    if missing_options:
        print(f"Options changed! Missing: {missing_options}")
        
        QMessageBox.warning(None, "Update Options Changed", 
            "Sorry, your previously selected download options are no longer available in this update.\n\n"
            "Please select your new options from the menu, or cancel to avoid updating.")
        
        main_app = UpdaterApp(latest_version, download_options)
        main_app.show()
        return
        
    paths = []
    for opt in selected_options:
        paths.extend(extract_files_from_option(download_options[opt]))
        
    tree = get_repo_tree(latest_version)
    files_to_download = resolve_files_to_download(paths, tree)
    
    class UpdateProgressWin(QWidget):
        def __init__(self):
            super().__init__()
            self.setWindowTitle('Updating Application...')
            self.setFixedSize(450, 150)
            
            layout = QVBoxLayout()
            self.setLayout(layout)
            
            self.lbl = QLabel("Starting update...", self)
            layout.addWidget(self.lbl)
            
            self.progress_bar = QProgressBar(self)
            self.progress_bar.setRange(0, 100)
            self.progress_bar.setValue(0)
            layout.addWidget(self.progress_bar)
            
            # Center window
            qtRectangle = self.frameGeometry()
            centerPoint = QDesktopWidget().availableGeometry().center()
            qtRectangle.moveCenter(centerPoint)
            self.move(qtRectangle.topLeft())
            
        def update_progress(self, current, total, text):
            val = int((current / total) * 100) if total > 0 else 0
            self.progress_bar.setValue(val)
            self.lbl.setText(text)
            
    update_win = UpdateProgressWin()
    update_win.show()
    
    class SilentUpdateWorker(QThread):
        progress_update = pyqtSignal(int, int, str)
        finished_update = pyqtSignal()
        
        def __init__(self, test_mode=False):
            super().__init__()
            self.test_mode = test_mode
            
        def run(self):
            kill_other_python_processes()
            print("Clearing old files...")
            def del_progress(idx, total, fname):
                self.progress_update.emit(idx, total, fname)
                
            clear_install_directory(install_dir, files_to_download, del_progress)
            
            if self.test_mode:
                input("\n[TEST MODE] Deletion complete. Verify the folder contents, then press Enter to continue downloading...\n")
            
            print("Downloading new files...")
            def down_progress(idx, total, fname):
                print(f"Downloading ({idx}/{total}): {fname}")
                self.progress_update.emit(idx, total, f"Downloading: {fname}")
                
            perform_download_concurrently(files_to_download, latest_version, install_dir, down_progress)
            
            config['current_version'] = latest_version
            config['selected_options'] = selected_options
            config['install_dir'] = install_dir
            save_config(config, install_dir)
            print("Update complete!")
            self.finished_update.emit()
            
    worker = SilentUpdateWorker(test_mode)
    worker.progress_update.connect(update_win.update_progress)
    worker.finished_update.connect(update_win.close)
    worker.finished_update.connect(QApplication.instance().quit)
    worker.start()
    
    # Block until background thread finishes
    QApplication.instance().exec_()
    
    if relaunch_script:
        print(f"Relaunching {relaunch_script} in a new process...")
        candidate_dirs = [install_dir, os.path.dirname(install_dir), os.getcwd()]
        found = False
        for cdir in candidate_dirs:
            script_path = os.path.join(cdir, relaunch_script)
            if os.path.exists(script_path):
                subprocess.Popen(["cmd.exe", "/c", relaunch_script], cwd=cdir, creationflags=subprocess.CREATE_NEW_CONSOLE)
                found = True
                break
        if not found:
            print(f"Warning: {relaunch_script} not found in {install_dir}. Cannot relaunch.")
            
    if is_temp_runner:
        try:
            cur_script = os.path.abspath(__file__)
            subprocess.Popen(
                f'cmd.exe /c timeout /t 1 >nul & del /f /q "{cur_script}"',
                shell=True,
                creationflags=subprocess.CREATE_NO_WINDOW
            )
        except Exception:
            pass
            
    sys.exit(0)

# --- UI Application Workers ---
class DownloadWorker(QThread):
    progress_update = pyqtSignal(int, int, str)
    finished_update = pyqtSignal()
    no_files_found = pyqtSignal()
    
    def __init__(self, latest_version, paths, install_dir, test_mode=False):
        super().__init__()
        self.latest_version = latest_version
        self.paths = paths
        self.install_dir = install_dir
        self.test_mode = test_mode
        
    def run(self):
        self.progress_update.emit(0, 1, "Fetching tree...")
        tree = get_repo_tree(self.latest_version)
        files = resolve_files_to_download(self.paths, tree)
        
        if not files:
            self.no_files_found.emit()
            return
            
        kill_other_python_processes()
            
        def del_progress(idx, total, fname):
            self.progress_update.emit(idx, total, fname)
            
        clear_install_directory(self.install_dir, files, del_progress)
            
        if self.test_mode:
            input("\n[TEST MODE] Deletion complete. Verify the folder contents, then press Enter to continue downloading...\n")
            
        def down_progress(idx, total, fname):
            self.progress_update.emit(idx, total, f"Downloading: {fname}")
            
        perform_download_concurrently(files, self.latest_version, self.install_dir, down_progress)
        self.finished_update.emit()

class UpdaterApp(QWidget):
    def __init__(self, latest_version, download_options, test_mode=False):
        super().__init__()
        self.latest_version = latest_version
        self.download_options = download_options
        self.install_dir = os.path.dirname(os.path.abspath(__file__))
        self.checkboxes = {}
        self.test_mode = test_mode
        
        self.initUI()
        
    def initUI(self):
        self.setWindowTitle("Application Updater & Installer")
        self.resize(600, 500)
        
        layout = QVBoxLayout()
        self.setLayout(layout)
        
        header = QLabel(f"Latest Version: {self.latest_version}", self)
        header.setStyleSheet("font-size: 20px; font-weight: bold;")
        header.setAlignment(Qt.AlignCenter)
        layout.addWidget(header)
        
        option_label = QLabel("Select what you want to download (Multiple allowed):", self)
        layout.addWidget(option_label)
        
        # Scroll area for checkboxes
        scroll = QScrollArea(self)
        scroll.setWidgetResizable(True)
        scroll_content = QWidget()
        scroll_layout = QVBoxLayout(scroll_content)
        
        config = load_config()
        saved_options = config.get('selected_options', [])
        
        for option in self.download_options.keys():
            cb = QCheckBox(option, self)
            if option in saved_options:
                cb.setChecked(True)
            scroll_layout.addWidget(cb)
            self.checkboxes[option] = cb
            
        scroll_layout.addStretch()
        scroll.setWidget(scroll_content)
        layout.addWidget(scroll)
        
        self.download_btn = QPushButton("Download & Install", self)
        self.download_btn.setFixedHeight(40)
        self.download_btn.clicked.connect(self.start_download)
        layout.addWidget(self.download_btn)
        
        self.progress_label = QLabel("", self)
        layout.addWidget(self.progress_label)
        
        self.progress_bar = QProgressBar(self)
        self.progress_bar.setRange(0, 100)
        self.progress_bar.setValue(0)
        layout.addWidget(self.progress_bar)
        
        # Center the window
        qtRectangle = self.frameGeometry()
        centerPoint = QDesktopWidget().availableGeometry().center()
        qtRectangle.moveCenter(centerPoint)
        self.move(qtRectangle.topLeft())
        
    def is_directory_empty(self, directory):
        script_name = os.path.basename(os.path.abspath(__file__)).lower()
        config_name = CONFIG_FILE.lower()
        print(f"[DEBUG] Checking directory: {directory}")
        for item in os.listdir(directory):
            if item.lower() not in [script_name, config_name]:
                print(f"[DEBUG] Directory is NOT empty because of: {item}")
                return False
        return True

    def start_download(self):
        print("[DEBUG] Download button clicked. Checking if directory is empty...")
        if not self.is_directory_empty(self.install_dir):
            print("[DEBUG] Showing error messagebox and opening folder.")
            QMessageBox.critical(self, "Folder Not Empty", "You need to place this in an empty folder.")
            try:
                os.startfile(self.install_dir)
            except Exception as e:
                print(f"[DEBUG] Failed to open folder: {e}")
            return
            
        selected = [opt for opt, cb in self.checkboxes.items() if cb.isChecked()]
        if not selected:
            QMessageBox.critical(self, "Error", "Please select at least one option to download.")
            return
            
        paths = []
        self.messages = []
        for opt in selected:
            paths.extend(extract_files_from_option(self.download_options[opt]))
            msg = extract_message_from_option(self.download_options[opt])
            if msg:
                self.messages.append({"title": opt, "message": msg})
                
        self.selected_options = selected
        
        self.download_btn.setEnabled(False)
        for cb in self.checkboxes.values():
            cb.setEnabled(False)
            
        self.worker = DownloadWorker(self.latest_version, paths, self.install_dir, self.test_mode)
        self.worker.progress_update.connect(self.update_ui_progress)
        self.worker.no_files_found.connect(self.on_no_files)
        self.worker.finished_update.connect(self.on_finished)
        self.worker.start()
        
    def update_ui_progress(self, current, total, text):
        val = int((current / total) * 100) if total > 0 else 0
        self.progress_bar.setValue(val)
        self.progress_label.setText(text)
        
    def on_no_files(self):
        self.update_ui_progress(1, 1, "No files found to download.")
        QMessageBox.information(self, "Done", "No matching files were found on the repository.")
        self.reset_ui()
        
    def on_finished(self):
        config = load_config()
        config['current_version'] = self.latest_version
        config['selected_options'] = self.selected_options
        config['install_dir'] = self.install_dir
        save_config(config)
        
        QMessageBox.information(self, "Success", "Download and installation complete!")
        
        for msg_info in self.messages:
            QMessageBox.information(self, msg_info["title"], msg_info["message"])
            
        self.reset_ui()
        
    def reset_ui(self):
        self.download_btn.setEnabled(True)
        for cb in self.checkboxes.values():
            cb.setEnabled(True)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--update-check", action="store_true", help="Silently check for updates and install if found")
    parser.add_argument("--relaunch", type=str, help="Script to relaunch after a successful update (e.g., start.bat)")
    parser.add_argument("--test", action="store_true", help="Pause after deletion to verify files were deleted")
    parser.add_argument("--install-dir", type=str, help="Target installation directory")
    parser.add_argument("--is-temp-runner", action="store_true", help="Flag indicating this updater is running from RAM/temp")
    parser.add_argument("--get-latest-version", action="store_true", help="Print latest version and exit")
    parser.add_argument("--download-file", type=str, help="Path of file to download from GitHub")
    parser.add_argument("--tag", type=str, help="Release tag for downloading")
    parser.add_argument("--out-file", type=str, help="Where to save the downloaded file")
    args = parser.parse_args()
    
    if args.get_latest_version:
        try:
            version = get_latest_release_version()
            if version:
                print(version)
        except:
            pass
        return
        
    if args.download_file and args.tag and args.out_file:
        content = download_file_content(args.download_file, args.tag)
        if content:
            with open(args.out_file, "wb") as f:
                f.write(content)
            print("SUCCESS")
        else:
            print("FAILED")
        return

    app_qt = QApplication(sys.argv)
    
    if args.update_check:
        run_update_check(args.relaunch, args.test, args.install_dir, args.is_temp_runner)
        return
        
    print("Fetching latest version info...")
    try:
        latest_version = get_latest_release_version()
    except Exception as e:
        print(str(e))
        QMessageBox.critical(None, "API Rate Limit", str(e))
        return

    if not latest_version:
        print("Could not fetch latest version from GitHub.")
        QMessageBox.critical(None, "Error", "Could not fetch latest version from GitHub. Check your internet connection.")
        return
        
    print("Fetching download options from the latest release...")
    download_options = fetch_download_options(latest_version)
    
    if not download_options:
        print("Error: download_options.json was not found in the latest release.")
        QMessageBox.critical(None, "Critical Error", "download_options.json was not found in the latest GitHub release.\nCannot proceed with installation.")
        return
        
    ex = UpdaterApp(latest_version, download_options, args.test)
    ex.show()
    sys.exit(app_qt.exec_())

if __name__ == "__main__":
    main()
