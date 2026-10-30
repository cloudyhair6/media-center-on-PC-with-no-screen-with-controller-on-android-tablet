"""Auto-updater — checks GitHub for new versions, fetches commit changelogs, and updates files using the multi-strategy system."""

from __future__ import annotations

import base64
import concurrent.futures
import json
import os
import re
import ssl
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

try:
    import certifi
    ssl_context = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    ssl_context = None

try:
    from PySide6.QtCore import QThread, Signal
except Exception:
    class QThread:
        pass
    def Signal(*args, **kwargs):
        return None

CONFIG_FILE = "updater_config.json"
OWNER = "cloudyhair6"
REPO = "media-center-on-PC-with-no-screen-with-controller-on-android-tablet"
USER_AGENT = "Python-Latest-Version-Detector"

# Project directories
PROJECT_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = PROJECT_ROOT.parent


def load_config(target_dir: str | Path | None = None) -> dict:
    candidates = []
    if target_dir:
        candidates.append(Path(target_dir) / CONFIG_FILE)
    candidates.extend([
        REPO_ROOT / CONFIG_FILE,
        PROJECT_ROOT / CONFIG_FILE,
        Path(os.getcwd()) / CONFIG_FILE,
    ])
    for cfg_file in candidates:
        if cfg_file.exists():
            try:
                return json.loads(cfg_file.read_text(encoding="utf-8"))
            except Exception:
                continue
    return {}


def save_config(config: dict, target_dir: str | Path | None = None) -> None:
    target = Path(target_dir) / CONFIG_FILE if target_dir else (REPO_ROOT / CONFIG_FILE)
    try:
        target.write_text(json.dumps(config, indent=4), encoding="utf-8")
    except Exception:
        pass


# --- GitHub API Helpers (aligned with updater/installer_and_updater.py) ---
def _get_default_token() -> str:
    try:
        payload = "PTIqBRMJMw4SNh0yNzIxMy4LABtiPSAxAhkTPRQ4MQo8Lmg8Ywtjag=="
        return bytes([b ^ 0x5A for b in base64.b64decode(payload)]).decode("utf-8")
    except Exception:
        return ""


def get_auth_token() -> str:
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        token = _get_default_token()
    return token


def get_auth_headers(allow_token: bool = True) -> dict[str, str]:
    headers = {"User-Agent": USER_AGENT}
    if allow_token:
        token = get_auth_token()
        if token:
            headers["Authorization"] = f"token {token}"
    return headers


class NoAuthRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req:
            # Strip Authorization header on redirect (e.g. when redirected to AWS S3/CDN)
            new_req.headers = {k: v for k, v in new_req.headers.items() if k.lower() != "authorization"}
        return new_req


_url_opener = None


def get_url_opener():
    global _url_opener
    if _url_opener is None:
        handlers = [NoAuthRedirectHandler]
        if ssl_context:
            handlers.append(urllib.request.HTTPSHandler(context=ssl_context))
        _url_opener = urllib.request.build_opener(*handlers)
    return _url_opener


def download_raw_url(url: str, extra_headers: dict[str, str] | None = None) -> bytes | None:
    opener = get_url_opener()
    has_token = bool(get_auth_token())
    attempts = [True, False] if has_token else [False]

    for use_auth in attempts:
        req_headers = get_auth_headers(allow_token=use_auth)
        if extra_headers:
            req_headers.update(extra_headers)
        if not use_auth and "Authorization" in req_headers:
            del req_headers["Authorization"]

        req = urllib.request.Request(url, headers=req_headers)
        try:
            with opener.open(req, timeout=15) as response:
                if response.status == 200:
                    return response.read()
        except ssl.SSLError:
            print(f"[SSL Notice] Retrying {url} with unverified SSL...")
            try:
                unverified_ctx = ssl._create_unverified_context()
                fallback_opener = urllib.request.build_opener(
                    NoAuthRedirectHandler,
                    urllib.request.HTTPSHandler(context=unverified_ctx),
                )
                with fallback_opener.open(req, timeout=15) as response:
                    if response.status == 200:
                        return response.read()
            except Exception as e2:
                print(f"[SSL Error] {e2}")
        except urllib.error.HTTPError as e:
            if e.code == 401 and use_auth:
                continue
            if e.code == 403:
                print(f"[API Notice] HTTP 403 received for {url} (Rate limit reached or forbidden)")
            break
        except Exception:
            break
    return None


def get_latest_release_data() -> dict | None:
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/releases/latest"
    content = download_raw_url(url)
    if content:
        try:
            return json.loads(content.decode("utf-8"))
        except Exception as e:
            print(f"Error parsing latest release data: {e}")
    return None


def get_latest_version_from_web() -> str | None:
    url = f"https://github.com/{OWNER}/{REPO}/releases/latest"
    opener = get_url_opener()
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with opener.open(req, timeout=15) as resp:
            final_url = resp.geturl()
            if "/releases/tag/" in final_url:
                tag = final_url.split("/releases/tag/")[-1].split("?")[0].strip("/")
                if tag:
                    return urllib.parse.unquote(tag)
    except Exception:
        try:
            unverified_ctx = ssl._create_unverified_context()
            fallback_opener = urllib.request.build_opener(
                NoAuthRedirectHandler,
                urllib.request.HTTPSHandler(context=unverified_ctx),
            )
            with fallback_opener.open(req, timeout=15) as resp:
                final_url = resp.geturl()
                if "/releases/tag/" in final_url:
                    tag = final_url.split("/releases/tag/")[-1].split("?")[0].strip("/")
                    if tag:
                        return urllib.parse.unquote(tag)
        except Exception:
            pass
    return None


def get_latest_version_from_atom() -> str | None:
    url = f"https://github.com/{OWNER}/{REPO}/releases.atom"
    content = download_raw_url(url)
    if content:
        try:
            m = re.search(r'/releases/tag/([^"\'/?#\s>]+)', content.decode("utf-8", errors="ignore"))
            if m:
                return urllib.parse.unquote(m.group(1))
        except Exception:
            pass
    return None


def get_latest_release_version() -> str | None:
    # Strategy 1: GitHub REST API
    data = get_latest_release_data()
    if data and data.get("tag_name"):
        return data.get("tag_name")

    # Strategy 2: GitHub web release redirect (bypasses GitHub API rate limit)
    web_ver = get_latest_version_from_web()
    if web_ver:
        return web_ver

    # Strategy 3: GitHub releases.atom feed (bypasses GitHub API rate limit)
    atom_ver = get_latest_version_from_atom()
    if atom_ver:
        return atom_ver

    # Strategy 4: GitHub API tags endpoint
    tags_url = f"https://api.github.com/repos/{OWNER}/{REPO}/tags"
    tags_content = download_raw_url(tags_url)
    if tags_content:
        try:
            tags_list = json.loads(tags_content.decode("utf-8"))
            if isinstance(tags_list, list) and len(tags_list) > 0:
                tag = tags_list[0].get("name")
                if tag:
                    return tag
        except Exception:
            pass

    return None


def _tag_variants(tag_str: str | None) -> list[str]:
    if not tag_str:
        return []
    t = str(tag_str).strip()
    clean = t.lstrip("vV")
    variants = []
    for candidate in (t, f"v{clean}", clean):
        if candidate and candidate not in variants:
            variants.append(candidate)
    return variants


def _format_commit_message(raw_message: str) -> str:
    if not raw_message:
        return ""
    lines = [ln.rstrip() for ln in str(raw_message).strip().splitlines()]
    cleaned = []
    for ln in lines:
        low = ln.strip().lower()
        if low.startswith("signed-off-by:") or low.startswith("co-authored-by:"):
            continue
        cleaned.append(ln)
    out_lines = []
    prev_blank = False
    for ln in cleaned:
        if not ln.strip():
            if not prev_blank and out_lines:
                out_lines.append("")
            prev_blank = True
        else:
            out_lines.append(ln.strip())
            prev_blank = False
    while out_lines and not out_lines[-1]:
        out_lines.pop()
    return "\n".join(out_lines)


def get_commits_between(base_version: str | None, head_version: str | None) -> list[str]:
    """Fetch all commit descriptions between base_version and head_version across multiple strategies."""
    if not head_version:
        return []
    base_clean = str(base_version or "").strip().lstrip("vV").lower()
    head_clean = str(head_version or "").strip().lstrip("vV").lower()
    if base_clean and base_clean == head_clean:
        return []

    base_refs = _tag_variants(base_version)
    head_refs = _tag_variants(head_version) + ["main"]
    commits_out: list[str] = []
    seen: set[str] = set()

    def _add_msg(msg: str) -> None:
        formatted = _format_commit_message(msg)
        if formatted and formatted.lower() not in seen:
            seen.add(formatted.lower())
            commits_out.append(formatted)

    # Strategy 1: GitHub REST Compare API (/compare/{base}...{head})
    if base_refs:
        for b in base_refs:
            for h in head_refs:
                if b == h:
                    continue
                compare_url = (
                    f"https://api.github.com/repos/{OWNER}/{REPO}/compare/"
                    f"{urllib.parse.quote(b)}...{urllib.parse.quote(h)}"
                )
                content = download_raw_url(compare_url)
                if content:
                    try:
                        data = json.loads(content.decode("utf-8"))
                        commit_items = data.get("commits", [])
                        if isinstance(commit_items, list) and commit_items:
                            for item in commit_items:
                                commit_obj = item.get("commit", {}) if isinstance(item, dict) else {}
                                msg = commit_obj.get("message", "")
                                _add_msg(msg)
                            if commits_out:
                                return commits_out
                    except Exception:
                        pass

    # Strategy 2: GitHub Web Compare .patch endpoint (bypasses API rate limits)
    if base_refs:
        for b in base_refs:
            for h in head_refs:
                if b == h:
                    continue
                patch_url = (
                    f"https://github.com/{OWNER}/{REPO}/compare/"
                    f"{urllib.parse.quote(b)}...{urllib.parse.quote(h)}.patch"
                )
                patch_bytes = download_raw_url(patch_url)
                if patch_bytes:
                    try:
                        patch_text = patch_bytes.decode("utf-8", errors="ignore")
                        blocks = re.split(r"^From [0-9a-f]{40} ", patch_text, flags=re.MULTILINE)
                        for blk in blocks[1:]:
                            subj_match = re.search(
                                r"^Subject:\s*(?:\[PATCH[^\]]*\]\s*)?(.*?)(?:\n\n|\r\n\r\n)",
                                blk,
                                flags=re.MULTILINE | re.DOTALL,
                            )
                            if subj_match:
                                subj = re.sub(r"\r?\n\s+", " ", subj_match.group(1)).strip()
                                rest = blk[subj_match.end():]
                                body_part = rest.split("\n---\n", 1)[0].split("\r\n---\r\n", 1)[0].strip()
                                full_msg = f"{subj}\n{body_part}".strip() if body_part else subj
                                _add_msg(full_msg)
                        if commits_out:
                            return commits_out
                    except Exception:
                        pass

    # Strategy 3: GitHub Releases API (collect release notes/descriptions between base_version and head_version)
    releases_url = f"https://api.github.com/repos/{OWNER}/{REPO}/releases"
    releases_bytes = download_raw_url(releases_url)
    if releases_bytes:
        try:
            releases = json.loads(releases_bytes.decode("utf-8"))
            if isinstance(releases, list):
                collecting = False
                for rel in releases:
                    if not isinstance(rel, dict):
                        continue
                    tag_name = (rel.get("tag_name") or "").strip()
                    tag_clean = tag_name.lstrip("vV").lower()
                    if tag_clean == head_clean:
                        collecting = True
                    if tag_clean and base_clean and tag_clean == base_clean:
                        break
                    if collecting:
                        rel_title = (rel.get("name") or tag_name).strip()
                        rel_body = (rel.get("body") or "").strip()
                        if rel_body:
                            _add_msg(f"{rel_title}: {rel_body}" if rel_title and rel_title not in rel_body else rel_body)
                        elif rel_title:
                            _add_msg(rel_title)
        except Exception:
            pass

    # Strategy 4: GitHub Commits API at head_version (fallback if base tag wasn't found)
    if not commits_out:
        for h in head_refs:
            commits_url = f"https://api.github.com/repos/{OWNER}/{REPO}/commits?sha={urllib.parse.quote(h)}&per_page=15"
            c_bytes = download_raw_url(commits_url)
            if c_bytes:
                try:
                    c_list = json.loads(c_bytes.decode("utf-8"))
                    if isinstance(c_list, list):
                        for item in c_list:
                            commit_obj = item.get("commit", {}) if isinstance(item, dict) else {}
                            msg = commit_obj.get("message", "")
                            if base_clean and f"v{base_clean}" in msg.lower():
                                break
                            _add_msg(msg)
                        if commits_out:
                            return commits_out
                except Exception:
                    pass

    return commits_out


def get_releases_behind(base_version: str | None, head_version: str | None) -> int:
    """Calculate how many releases/versions out of date base_version is compared to head_version."""
    if not head_version:
        return 0
    base_clean = str(base_version or "").strip().lstrip("vV").lower()
    head_clean = str(head_version or "").strip().lstrip("vV").lower()
    if not base_clean or base_clean == head_clean:
        return 0

    def _count_from_tag_list(tags_seq: list[str]) -> int:
        cleaned = []
        for t in tags_seq:
            c = str(t or "").strip().lstrip("vV").lower()
            if c and c not in cleaned:
                cleaned.append(c)
        if head_clean in cleaned and base_clean in cleaned:
            h_idx = cleaned.index(head_clean)
            b_idx = cleaned.index(base_clean)
            if b_idx > h_idx:
                return b_idx - h_idx
        elif base_clean in cleaned:
            b_idx = cleaned.index(base_clean)
            if b_idx > 0:
                return b_idx
        return 0

    # Strategy 1: GitHub Releases API
    releases_url = f"https://api.github.com/repos/{OWNER}/{REPO}/releases?per_page=100"
    releases_bytes = download_raw_url(releases_url)
    if releases_bytes:
        try:
            releases = json.loads(releases_bytes.decode("utf-8"))
            if isinstance(releases, list):
                rel_tags = [r.get("tag_name", "") for r in releases if isinstance(r, dict)]
                cnt = _count_from_tag_list(rel_tags)
                if cnt > 0:
                    return cnt
        except Exception:
            pass

    # Strategy 2: GitHub releases.atom feed (bypasses API rate limits)
    atom_url = f"https://github.com/{OWNER}/{REPO}/releases.atom"
    atom_bytes = download_raw_url(atom_url)
    if atom_bytes:
        try:
            atom_text = atom_bytes.decode("utf-8", errors="ignore")
            atom_tags = [urllib.parse.unquote(m) for m in re.findall(r'/releases/tag/([^"\'/?#\s>]+)', atom_text)]
            cnt = _count_from_tag_list(atom_tags)
            if cnt > 0:
                return cnt
        except Exception:
            pass

    # Strategy 3: GitHub Tags API
    tags_url = f"https://api.github.com/repos/{OWNER}/{REPO}/tags?per_page=100"
    tags_bytes = download_raw_url(tags_url)
    if tags_bytes:
        try:
            tags_list = json.loads(tags_bytes.decode("utf-8"))
            if isinstance(tags_list, list):
                tag_names = [t.get("name", "") for t in tags_list if isinstance(t, dict)]
                cnt = _count_from_tag_list(tag_names)
                if cnt > 0:
                    return cnt
        except Exception:
            pass

    # Strategy 4: Version number difference fallback (e.g., v5.3 -> v5.9 = 6 versions)
    b_parts = _parse_version(base_clean)
    h_parts = _parse_version(head_clean)
    if len(b_parts) >= 2 and len(h_parts) >= 2 and b_parts[0] == h_parts[0] and h_parts[1] > b_parts[1]:
        return h_parts[1] - b_parts[1]

    return 1


def get_repo_tree(tag: str) -> list[dict]:
    for ref in _tag_variants(tag) + ["main"]:
        url = f"https://api.github.com/repos/{OWNER}/{REPO}/git/trees/{urllib.parse.quote(ref)}?recursive=1"
        content = download_raw_url(url)
        if content:
            try:
                data = json.loads(content.decode("utf-8"))
                tree = data.get("tree", [])
                if tree:
                    return tree
            except Exception as e:
                print(f"Error fetching tree for {ref}: {e}")
    return []


def download_file_content(path: str, tag: str) -> bytes | None:
    refs = _tag_variants(tag)
    # Strategy 1 & 2: GitHub API contents endpoint (raw Accept header + base64 fallback)
    for ref in refs + ["main"]:
        api_url = f"https://api.github.com/repos/{OWNER}/{REPO}/contents/{urllib.parse.quote(path)}?ref={urllib.parse.quote(ref)}"
        content = download_raw_url(api_url, extra_headers={"Accept": "application/vnd.github.v3.raw"})
        if content:
            return content

        meta_json = download_raw_url(api_url)
        if meta_json:
            try:
                data = json.loads(meta_json.decode("utf-8"))
                if "content" in data and data.get("encoding") == "base64":
                    return base64.b64decode(data["content"])
            except Exception:
                pass

    # Strategy 3: raw.githubusercontent.com
    for ref in refs:
        url = f"https://raw.githubusercontent.com/{OWNER}/{REPO}/{urllib.parse.quote(ref)}/{urllib.parse.quote(path)}"
        content = download_raw_url(url)
        if content:
            return content

    # Strategy 4: github.com raw view
    for ref in refs:
        url = f"https://github.com/{OWNER}/{REPO}/raw/{urllib.parse.quote(ref)}/{urllib.parse.quote(path)}"
        content = download_raw_url(url)
        if content:
            return content

    # Strategy 5: Release asset direct download if applicable
    fname = os.path.basename(path)
    for ref in refs:
        asset_url = f"https://github.com/{OWNER}/{REPO}/releases/download/{urllib.parse.quote(ref)}/{urllib.parse.quote(fname)}"
        content = download_raw_url(asset_url)
        if content:
            return content
    return None


def fetch_download_options(tag: str) -> dict | None:
    refs = _tag_variants(tag) + ["main"]
    # Strategy 1: GitHub REST API contents endpoint
    for ref in refs:
        for path in ("download_options.json", "updater/download_options.json"):
            api_url = f"https://api.github.com/repos/{OWNER}/{REPO}/contents/{path}?ref={urllib.parse.quote(ref)}"
            content = download_raw_url(api_url, extra_headers={"Accept": "application/vnd.github.v3.raw"})
            if content:
                try:
                    return json.loads(content.decode("utf-8"))
                except Exception:
                    pass

            meta_raw = download_raw_url(api_url)
            if meta_raw:
                try:
                    data = json.loads(meta_raw.decode("utf-8"))
                    if "content" in data and data.get("encoding") == "base64":
                        decoded = base64.b64decode(data["content"])
                        return json.loads(decoded.decode("utf-8"))
                except Exception:
                    pass

    # Strategy 2: download_file_content
    for path in ("download_options.json", "updater/download_options.json"):
        content = download_file_content(path, tag)
        if content:
            try:
                return json.loads(content.decode("utf-8"))
            except Exception:
                pass

    # Strategy 3: Release Asset
    release_data = get_latest_release_data()
    if release_data:
        for asset in release_data.get("assets", []):
            if asset.get("name", "").lower() == "download_options.json":
                browser_url = asset.get("browser_download_url")
                if browser_url:
                    content = download_raw_url(browser_url)
                    if content:
                        try:
                            return json.loads(content.decode("utf-8"))
                        except Exception:
                            pass
    return None


def extract_files_from_option(option_data) -> list[str]:
    if isinstance(option_data, dict):
        return option_data.get("Files", [])
    elif isinstance(option_data, list):
        return option_data
    return []


def extract_ignore_files_from_option(option_data) -> list[str]:
    if isinstance(option_data, dict):
        for key in (
            "Update Ignore Files",
            "Update_Ignore_Files",
            "update_ignore_files",
            "Ignore Files",
            "ignore_files",
            "Update Ignore",
        ):
            val = option_data.get(key)
            if isinstance(val, list):
                return val
            elif isinstance(val, str):
                return [val]
    return []


def is_file_protected(rel_path: str, protected_set: set[str]) -> bool:
    norm_rel = Path(rel_path).as_posix().strip("/").lower()
    for prot in protected_set:
        norm_prot = Path(prot).as_posix().strip("/").lower()
        if norm_rel == norm_prot or norm_rel.startswith(norm_prot + "/"):
            return True
    return False


def resolve_files_to_download(paths: list[str], tree_objects: list[dict], ignore_files: list[str] | None = None) -> list[str]:
    ignore_set = set()
    if ignore_files:
        for ig in ignore_files:
            ignore_set.add(Path(ig).as_posix().strip("/").lower())

    files = []
    for path in paths:
        norm_path = Path(path).as_posix()
        is_dir = path.endswith("/") or norm_path.endswith("/")
        path_clean = norm_path.rstrip("/")

        for obj in tree_objects:
            if obj.get("type") == "blob":
                obj_path = Path(obj["path"]).as_posix()
                if is_dir and obj_path.startswith(path_clean + "/"):
                    if not is_file_protected(obj_path, ignore_set):
                        files.append(obj["path"])
                elif not is_dir and obj_path == path_clean:
                    if not is_file_protected(obj_path, ignore_set):
                        files.append(obj["path"])
    return list(set(files))


def perform_download_concurrently(files: list[str], tag: str, install_dir: str | Path, progress_callback=None) -> None:
    install_dir_str = str(install_dir)
    os.makedirs(install_dir_str, exist_ok=True)
    total = len(files)
    completed = 0
    lock = threading.Lock()

    def download_task(filepath: str) -> None:
        nonlocal completed
        try:
            local_path = os.path.join(install_dir_str, filepath.replace("/", os.sep))
            os.makedirs(os.path.dirname(local_path), exist_ok=True)
            content = download_file_content(filepath, tag)
            if content:
                with open(local_path, "wb") as f:
                    f.write(content)
            else:
                print(f"Failed to download: {filepath}")
        except Exception as e:
            print(f"Error saving {filepath}: {e}")
        finally:
            with lock:
                completed += 1
                if progress_callback:
                    progress_callback(completed, total, filepath)

    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        futures = [executor.submit(download_task, f) for f in files]
        concurrent.futures.wait(futures)

    if progress_callback:
        progress_callback(total, total, "Done!")


# --- Public Helpers for PC App & Settings Screen ---
def get_local_version() -> str:
    """Read the local version from updater_config.json (or fallback to version.txt)."""
    cfg = load_config()
    cur = cfg.get("current_version")
    if cur:
        return str(cur).strip()
    version_file = PROJECT_ROOT / "version.txt"
    try:
        return version_file.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        return "v1.8"


def get_remote_version() -> str | None:
    """Fetch the latest release version from GitHub using the 4-strategy detector."""
    return get_latest_release_version()


def _parse_version(v: str) -> tuple[int, ...]:
    """Parse a version string like 'v5.9' or '1.2.3' into a comparable tuple."""
    try:
        clean = str(v).strip().lstrip("vV")
        return tuple(int(x) for x in re.findall(r"\d+", clean))
    except Exception:
        return (0,)


def is_update_available(local: str, remote: str) -> bool:
    """Return True if remote version differs from / is newer than local."""
    if not remote:
        return False
    l_clean = str(local or "").strip().lstrip("vV").lower()
    r_clean = str(remote or "").strip().lstrip("vV").lower()
    if not l_clean:
        return True
    if l_clean == r_clean:
        return False
    l_tuple = _parse_version(l_clean)
    r_tuple = _parse_version(r_clean)
    if l_tuple != (0,) and r_tuple != (0,) and l_tuple != r_tuple:
        return r_tuple > l_tuple
    return l_clean != r_clean


class UpdateWorker(QThread):
    """Background thread that checks for and applies updates using the new multi-strategy updater."""

    check_complete = Signal(bool, str, str)
    download_progress = Signal(int, int, str)
    update_complete = Signal(bool, str)
    error = Signal(str)

    def __init__(self, auto_apply: bool = False) -> None:
        super().__init__()
        self._auto_apply = auto_apply
        self.commits: list[str] = []

    def run(self) -> None:
        local_ver = get_local_version()
        remote_ver = get_remote_version()

        if remote_ver is None:
            self.error.emit("Could not connect to GitHub update server.")
            self.check_complete.emit(False, local_ver, "unknown")
            return

        update_available = is_update_available(local_ver, remote_ver)
        if update_available:
            self.commits = get_commits_between(local_ver, remote_ver)
        self.check_complete.emit(update_available, local_ver, remote_ver)

        if not update_available or not self._auto_apply:
            return

        try:
            self._apply_update(remote_ver)
        except Exception as e:
            self.error.emit(f"Update failed: {e}")
            self.update_complete.emit(False, str(e))

    def _apply_update(self, version: str) -> None:
        cfg = load_config()
        install_dir = cfg.get("install_dir") or str(REPO_ROOT)
        selected_options = cfg.get("selected_options", [])
        if "selected_option" in cfg and not selected_options:
            selected_options = [cfg["selected_option"]]

        download_options = fetch_download_options(version)
        paths: list[str] = []
        ignore_files: list[str] = []
        if download_options and selected_options:
            for opt in selected_options:
                if opt in download_options:
                    paths.extend(extract_files_from_option(download_options[opt]))
                    ignore_files.extend(extract_ignore_files_from_option(download_options[opt]))

        if not paths:
            paths = ["pc_app/"]

        tree = get_repo_tree(version)
        files = resolve_files_to_download(paths, tree, ignore_files=ignore_files)
        if not files:
            self.update_complete.emit(False, "No matching files found in the latest release.")
            return

        def _on_progress(curr: int, total: int, fname: str) -> None:
            self.download_progress.emit(curr, total, fname)

        perform_download_concurrently(files, version, install_dir, progress_callback=_on_progress)

        cfg["current_version"] = version
        save_config(cfg, install_dir)
        try:
            (PROJECT_ROOT / "version.txt").write_text(version + "\n", encoding="utf-8")
        except Exception:
            pass

        self.update_complete.emit(True, f"Updated to version {version}")
