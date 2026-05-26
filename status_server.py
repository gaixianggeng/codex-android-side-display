#!/usr/bin/env python3
import argparse
import base64
import binascii
import json
import os
import re
import select
import socket
import subprocess
import sqlite3
import struct
import time
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock


STARTED_AT = time.time()
STATE_LOCK = Lock()
STATE = {
    "manual_override": None,
}
CODEX_HOME = os.environ.get("CODEX_HOME", os.path.expanduser("~/.codex"))
STATE_DB = os.path.join(CODEX_HOME, "state_5.sqlite")
LOGS_DB = os.path.join(CODEX_HOME, "logs_2.sqlite")
GOALS_DB = os.path.join(CODEX_HOME, "goals_1.sqlite")
CODEX_BIN = os.environ.get("CODEX_BIN", "/Applications/Codex.app/Contents/Resources/codex")
RATE_LIMIT_CACHE_TTL = 60
RATE_LIMIT_LOCK = Lock()
RATE_LIMIT_CACHE = {
    "fetched_at": 0,
    "data": None,
}

def startup_adb_info():
    env_first = os.environ.get("SIDECAR_ADB_FIRST_DEVICE")
    env_count = os.environ.get("SIDECAR_ADB_DEVICE_COUNT")
    if env_first is not None and env_count is not None:
        return {
            "device_count": int(env_count),
            "first_device": env_first,
        }

    adb_bin = os.environ.get("ADB_BIN", "/opt/homebrew/bin/adb")
    try:
        result = subprocess.run(
            [adb_bin, "devices", "-l"],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=1.5,
        )
    except Exception:
        return {
            "device_count": 0,
            "first_device": "adb unavailable",
        }

    devices = []
    for line in result.stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(line)
    return {
        "device_count": len(devices),
        "first_device": devices[0] if devices else "no authorized device",
    }


ADB_INFO = startup_adb_info()
SPOTIFY_LOCK = Lock()
SPOTIFY_ART = {
    "url": None,
    "content_type": "image/jpeg",
    "bytes": None,
    "fetched_at": 0,
}


def iso_now():
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def open_ro(path):
    return sqlite3.connect("file:%s?mode=ro" % path, uri=True, timeout=0.2)


def local_time_from_epoch(seconds):
    try:
        return datetime.fromtimestamp(int(seconds)).astimezone().isoformat(timespec="seconds")
    except Exception:
        return "-"


def free_local_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def recv_exact(sock, length):
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise RuntimeError("websocket closed")
        data += chunk
    return data


def websocket_connect(port):
    sock = socket.create_connection(("127.0.0.1", port), timeout=2)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    request = (
        "GET /rpc HTTP/1.1\r\n"
        "Host: 127.0.0.1:%d\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: %s\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    ) % (port, key)
    sock.sendall(request.encode("ascii"))
    response = b""
    while b"\r\n\r\n" not in response:
        response += sock.recv(4096)
    if b" 101 " not in response.split(b"\r\n", 1)[0]:
        raise RuntimeError("app-server websocket handshake failed")
    return sock


def websocket_send_json(sock, payload):
    data = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    header = bytearray([0x81])
    if len(data) < 126:
        header.append(0x80 | len(data))
    elif len(data) < 65536:
        header.append(0x80 | 126)
        header.extend(struct.pack("!H", len(data)))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack("!Q", len(data)))
    mask = os.urandom(4)
    header.extend(mask)
    masked = bytes(byte ^ mask[index % 4] for index, byte in enumerate(data))
    sock.sendall(bytes(header) + masked)


def websocket_recv_json(sock, timeout=5):
    readable, _, _ = select.select([sock], [], [], timeout)
    if not readable:
        return None
    first, second = recv_exact(sock, 2)
    opcode = first & 0x0F
    length = second & 0x7F
    masked = second & 0x80
    if length == 126:
        length = struct.unpack("!H", recv_exact(sock, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", recv_exact(sock, 8))[0]
    mask = recv_exact(sock, 4) if masked else b""
    payload = recv_exact(sock, length)
    if masked:
        payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
    if opcode == 8:
        return None
    return json.loads(payload.decode("utf-8"))


def wait_for_app_server(port, process):
    deadline = time.time() + 4
    url = "http://127.0.0.1:%d/readyz" % port
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError("app-server exited before ready")
        try:
            with urllib.request.urlopen(url, timeout=0.2):
                return
        except Exception:
            time.sleep(0.1)
    raise RuntimeError("app-server ready timeout")


def normalize_rate_window(window):
    if not isinstance(window, dict):
        return None
    used = int(round(float(window.get("usedPercent") or 0)))
    used = max(0, min(100, used))
    resets_at = window.get("resetsAt")
    return {
        "used_percent": used,
        "remaining_percent": max(0, min(100, 100 - used)),
        "window_duration_mins": window.get("windowDurationMins"),
        "resets_at": resets_at,
        "resets_at_local": local_time_from_epoch(resets_at) if resets_at else None,
    }


def normalize_rate_limits(payload):
    rate_limits = payload.get("rateLimits") or {}
    by_limit_id = payload.get("rateLimitsByLimitId") or {}
    codex_limits = by_limit_id.get("codex") or rate_limits
    primary = normalize_rate_window(codex_limits.get("primary"))
    secondary = normalize_rate_window(codex_limits.get("secondary"))
    return {
        "account_limits_available": bool(primary or secondary),
        "limit_id": codex_limits.get("limitId") or "codex",
        "limit_name": codex_limits.get("limitName"),
        "plan_type": codex_limits.get("planType"),
        "primary": primary,
        "secondary": secondary,
        "raw_source": "codex app-server account/rateLimits/read",
    }


def fetch_rate_limits_uncached():
    port = free_local_port()
    process = subprocess.Popen(
        [CODEX_BIN, "app-server", "--listen", "ws://127.0.0.1:%d" % port],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    sock = None
    try:
        wait_for_app_server(port, process)
        sock = websocket_connect(port)
        websocket_send_json(sock, {
            "id": 1,
            "method": "initialize",
            "params": {
                "clientInfo": {
                    "name": "android-sidecar",
                    "title": "Android Sidecar",
                    "version": "1",
                },
                "capabilities": {
                    "experimentalApi": True,
                    "requestAttestation": False,
                    "optOutNotificationMethods": [],
                },
            },
        })
        deadline = time.time() + 8
        while time.time() < deadline:
            message = websocket_recv_json(sock, timeout=1)
            if message and message.get("id") == 1:
                if "error" in message:
                    raise RuntimeError(message["error"].get("message", "initialize failed"))
                break
        websocket_send_json(sock, {
            "id": 2,
            "method": "account/rateLimits/read",
        })
        deadline = time.time() + 20
        while time.time() < deadline:
            message = websocket_recv_json(sock, timeout=1)
            if not message or message.get("id") != 2:
                continue
            if "error" in message:
                raise RuntimeError(message["error"].get("message", "rate limit read failed"))
            return normalize_rate_limits(message.get("result") or {})
        raise RuntimeError("rate limit read timeout")
    finally:
        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass
        process.terminate()
        try:
            process.wait(timeout=2)
        except Exception:
            process.kill()


def read_account_rate_limits():
    with RATE_LIMIT_LOCK:
        cached = RATE_LIMIT_CACHE.get("data")
        fetched_at = RATE_LIMIT_CACHE.get("fetched_at") or 0
        if cached is not None and time.time() - fetched_at < RATE_LIMIT_CACHE_TTL:
            return cached

    try:
        data = fetch_rate_limits_uncached()
    except Exception as exception:
        data = {
            "account_limits_available": False,
            "error": str(exception),
            "raw_source": "codex app-server account/rateLimits/read",
        }

    with RATE_LIMIT_LOCK:
        RATE_LIMIT_CACHE["fetched_at"] = time.time()
        RATE_LIMIT_CACHE["data"] = data
    return data


def json_or_text(value):
    if value is None:
        return "-"
    try:
        parsed = json.loads(value)
        if isinstance(parsed, dict) and "type" in parsed:
            return parsed["type"]
        return json.dumps(parsed, ensure_ascii=False)
    except Exception:
        return str(value)


def read_latest_thread():
    with open_ro(STATE_DB) as connection:
        connection.row_factory = sqlite3.Row
        row = connection.execute(
            """
            SELECT id, title, cwd, model, reasoning_effort, sandbox_policy,
                   approval_mode, tokens_used, updated_at, cli_version,
                   source, thread_source, preview
            FROM threads
            ORDER BY updated_at DESC
            LIMIT 1
            """
        ).fetchone()
    return dict(row) if row else None


def read_goal(thread_id):
    if not thread_id or not os.path.exists(GOALS_DB):
        return None
    try:
        with open_ro(GOALS_DB) as connection:
            connection.row_factory = sqlite3.Row
            row = connection.execute(
                """
                SELECT objective, status, token_budget, tokens_used,
                       time_used_seconds, updated_at_ms
                FROM thread_goals
                WHERE thread_id = ?
                LIMIT 1
                """,
                (thread_id,),
            ).fetchone()
        return dict(row) if row else None
    except Exception:
        return None


def recent_log_count():
    if not os.path.exists(LOGS_DB):
        return 0
    try:
        cutoff = int(time.time()) - 300
        with open_ro(LOGS_DB) as connection:
            row = connection.execute(
                "SELECT COUNT(*) FROM logs WHERE ts >= ?",
                (cutoff,),
            ).fetchone()
        return int(row[0]) if row else 0
    except Exception:
        return 0


def app_running(name):
    try:
        return subprocess.run(
            ["pgrep", "-x", name],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=0.5,
        ).returncode == 0
    except Exception:
        return False


def read_spotify():
    if not app_running("Spotify"):
        return {
            "available": False,
            "source_id": "spotify",
            "source_name": "Spotify",
            "state": "not_running",
            "track": "-",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": False,
        }

    separator = "\u001f"
    script = (
        'tell application "Spotify"\n'
        'set t to current track\n'
        'return (player state as string) & "%s" & (name of t) & "%s" & (artist of t) & "%s" & '
        '(album of t) & "%s" & (artwork url of t) & "%s" & ((duration of t) as string) & "%s" & '
        '((player position) as string)\n'
        'end tell'
    ) % (separator, separator, separator, separator, separator, separator)

    try:
        result = subprocess.run(
            ["osascript", "-e", script],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=1.2,
        )
    except subprocess.TimeoutExpired:
        return {
            "available": False,
            "source_id": "spotify",
            "source_name": "Spotify",
            "state": "timeout",
            "track": "Spotify did not respond",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": bool(SPOTIFY_ART["bytes"]),
        }

    if result.returncode != 0:
        return {
            "available": False,
            "source_id": "spotify",
            "source_name": "Spotify",
            "state": "error",
            "track": result.stderr.strip()[:160] or "Spotify AppleScript error",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": bool(SPOTIFY_ART["bytes"]),
        }

    parts = result.stdout.rstrip("\n").split(separator)
    while len(parts) < 7:
        parts.append("")

    state, track, artist, album, artwork_url, duration_ms, position_s = parts[:7]
    try:
        duration = max(0, int(float(duration_ms)))
    except Exception:
        duration = 0
    try:
        position_ms = max(0, int(float(position_s) * 1000))
    except Exception:
        position_ms = 0
    progress = min(100, int(round(position_ms * 100.0 / duration))) if duration else 0

    update_spotify_art(artwork_url)
    return {
        "available": True,
        "source_id": "spotify",
        "source_name": "Spotify",
        "state": state or "unknown",
        "track": track or "-",
        "artist": artist or "-",
        "album": album or "-",
        "duration_ms": duration,
        "position_ms": position_ms,
        "progress_percent": progress,
        "artwork_available": bool(SPOTIFY_ART["bytes"]),
        "artwork_endpoint": "/spotify-art" if SPOTIFY_ART["bytes"] else None,
        "artwork_key": str(hash(artwork_url)) if artwork_url else "",
    }


def update_spotify_art(url):
    if not url:
        return
    with SPOTIFY_LOCK:
        if SPOTIFY_ART["url"] == url and SPOTIFY_ART["bytes"]:
            return
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "CodexSidecar/1.0"})
        with urllib.request.urlopen(request, timeout=2.0) as response:
            data = response.read(2 * 1024 * 1024)
            content_type = response.headers.get("Content-Type", "image/jpeg")
    except Exception:
        return
    with SPOTIFY_LOCK:
        SPOTIFY_ART.update({
            "url": url,
            "content_type": content_type,
            "bytes": data,
            "fetched_at": time.time(),
        })


def update_apple_music_art(track, artist, album):
    cache_key = "apple_music:%s:%s:%s" % (track, artist, album)
    with SPOTIFY_LOCK:
        if SPOTIFY_ART["url"] == cache_key and SPOTIFY_ART["bytes"]:
            return cache_key

    try:
        result = subprocess.run(
            ["osascript", "-e", 'tell application "Music" to get raw data of artwork 1 of current track'],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=2.5,
        )
    except Exception:
        return ""

    if result.returncode != 0 or not result.stdout:
        return ""

    text = result.stdout.decode("utf-8", "ignore")
    payload = text.split("tdta", 1)[1] if "tdta" in text else text
    hex_data = "".join(re.findall(r"[0-9A-Fa-f]+", payload))
    if len(hex_data) % 2 == 1:
        hex_data = hex_data[:-1]

    try:
        data = binascii.unhexlify(hex_data)
    except Exception:
        return ""

    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        content_type = "image/png"
    elif data.startswith(b"\xff\xd8\xff"):
        content_type = "image/jpeg"
    else:
        content_type = "application/octet-stream"

    with SPOTIFY_LOCK:
        SPOTIFY_ART.update({
            "url": cache_key,
            "content_type": content_type,
            "bytes": data,
            "fetched_at": time.time(),
        })
    return cache_key


def read_apple_music():
    if not app_running("Music"):
        return {
            "available": False,
            "source_id": "apple_music",
            "source_name": "Apple Music",
            "state": "not_running",
            "track": "-",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": False,
        }

    separator = "\u001f"
    script = (
        'tell application "Music"\n'
        'if player state is stopped then\n'
        'return "stopped" & "%s" & "-" & "%s" & "-" & "%s" & "-" & "%s" & "0" & "%s" & "0"\n'
        'end if\n'
        'set t to current track\n'
        'return (player state as string) & "%s" & (name of t) & "%s" & (artist of t) & "%s" & '
        '(album of t) & "%s" & ((duration of t) as string) & "%s" & ((player position) as string)\n'
        'end tell'
    ) % (separator, separator, separator, separator, separator, separator, separator, separator, separator, separator)

    try:
        result = subprocess.run(
            ["osascript", "-e", script],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=1.2,
        )
    except subprocess.TimeoutExpired:
        return {
            "available": False,
            "source_id": "apple_music",
            "source_name": "Apple Music",
            "state": "timeout",
            "track": "Apple Music did not respond",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": False,
        }

    if result.returncode != 0:
        return {
            "available": False,
            "source_id": "apple_music",
            "source_name": "Apple Music",
            "state": "error",
            "track": result.stderr.strip()[:160] or "Music AppleScript error",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": False,
        }

    parts = result.stdout.rstrip("\n").split(separator)
    while len(parts) < 6:
        parts.append("")

    state, track, artist, album, duration_s, position_s = parts[:6]
    try:
        duration = max(0, int(float(duration_s) * 1000))
    except Exception:
        duration = 0
    try:
        position_ms = max(0, int(float(position_s) * 1000))
    except Exception:
        position_ms = 0
    progress = min(100, int(round(position_ms * 100.0 / duration))) if duration else 0
    artwork_key = update_apple_music_art(track, artist, album)

    return {
        "available": state not in ("stopped", ""),
        "source_id": "apple_music",
        "source_name": "Apple Music",
        "state": state or "unknown",
        "track": track or "-",
        "artist": artist or "-",
        "album": album or "-",
        "duration_ms": duration,
        "position_ms": position_ms,
        "progress_percent": progress,
        "artwork_available": bool(artwork_key),
        "artwork_endpoint": "/spotify-art" if artwork_key else None,
        "artwork_key": artwork_key,
    }


def read_playback():
    spotify = read_spotify()
    if spotify.get("available") or spotify.get("state") not in ("not_running",):
        return spotify
    music = read_apple_music()
    if music.get("available") or music.get("state") not in ("not_running",):
        return music
    return spotify


def safe_read_playback():
    try:
        return read_playback()
    except Exception as exception:
        return {
            "available": False,
            "source_id": "unknown",
            "source_name": "Playback",
            "state": "error",
            "track": str(exception)[:160] or "playback unavailable",
            "artist": "-",
            "album": "-",
            "progress_percent": 0,
            "artwork_available": False,
        }


def toggle_playback():
    playback = read_playback()
    source_id = playback.get("source_id")
    source_name = playback.get("source_name") or "Playback"

    if source_id == "spotify":
        app_name = "Spotify"
    elif source_id in ("apple_music", "music"):
        app_name = "Music"
    else:
        return {
            "ok": False,
            "error": "no supported playback source",
            "playback": playback,
        }

    if not app_running(app_name):
        return {
            "ok": False,
            "error": "%s is not running" % source_name,
            "playback": playback,
        }

    result = subprocess.run(
        ["osascript", "-e", 'tell application "%s" to playpause' % app_name],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=1.2,
    )
    if result.returncode != 0:
        return {
            "ok": False,
            "error": result.stderr.strip() or "playpause failed",
            "playback": playback,
        }

    time.sleep(0.2)
    return {
        "ok": True,
        "source_id": source_id,
        "source_name": source_name,
        "playback": read_playback(),
    }


def make_codex_unavailable_status(error):
    return {
        "state": "codex_unavailable",
        "progress": -1,
        "progress_known": False,
        "message": "Codex local state is temporarily unavailable; playback data is still live.",
        "task": {
            "title": "Codex unavailable",
            "phase": "waiting for local state",
            "detail": error,
            "progress": -1,
        },
        "codex": {
            "thread_id": None,
            "title": "Codex unavailable",
            "cwd": os.getcwd(),
            "model": "-",
            "reasoning_effort": "-",
            "sandbox_policy": "-",
            "approval_mode": "-",
            "cli_version": "-",
            "source": "-",
            "updated_at": None,
            "updated_at_local": iso_now(),
        },
        "usage": {
            "thread_tokens_used": 0,
            "goal_tokens_used": None,
            "token_budget": None,
            "weekly_usage": None,
            "weekly_reset_at": None,
            "account_limits_available": False,
            "account_rate_limits": {
                "account_limits_available": False,
                "error": error,
            },
            "thread_tokens_source": STATE_DB,
            "goal_tokens_source": None,
            "account_limits_note": error,
        },
        "logs": {
            "events_last_5m": 0,
        },
        "spotify": safe_read_playback(),
    }


def make_codex_status():
    try:
        thread = read_latest_thread()
    except Exception as exception:
        return make_codex_unavailable_status(str(exception))

    if not thread:
        return {
            "state": "offline",
            "message": "No local Codex thread found in state_5.sqlite.",
            "task": {
                "title": "No Codex thread",
                "phase": "unavailable",
                "detail": STATE_DB,
            },
            "progress_known": False,
            "progress": -1,
            "spotify": safe_read_playback(),
        }

    try:
        goal = read_goal(thread.get("id"))
    except Exception:
        goal = None
    token_budget = goal.get("token_budget") if goal else None
    goal_tokens = goal.get("tokens_used") if goal else None
    thread_tokens = int(thread.get("tokens_used") or 0)
    progress_known = bool(token_budget and int(token_budget) > 0)
    progress = int(min(100, round((int(goal_tokens or thread_tokens) / int(token_budget)) * 100))) if progress_known else -1
    try:
        logs_5m = recent_log_count()
    except Exception:
        logs_5m = 0
    state = goal.get("status") if goal and goal.get("status") else ("active" if logs_5m > 0 else "idle")
    model = thread.get("model") or "unknown"
    reasoning = thread.get("reasoning_effort") or "default"
    try:
        account_rate_limits = read_account_rate_limits()
    except Exception as exception:
        account_rate_limits = {
            "account_limits_available": False,
            "error": str(exception),
        }

    return {
        "state": state,
        "progress": progress,
        "progress_known": progress_known,
        "message": "真实本地 Codex 线程数据；账户限额来自 Codex app-server。",
        "task": {
            "title": thread.get("title") or "Untitled Codex thread",
            "phase": "%s / reasoning %s" % (model, reasoning),
            "detail": thread.get("preview") or thread.get("cwd") or "-",
            "progress": progress,
        },
        "codex": {
            "thread_id": thread.get("id"),
            "title": thread.get("title"),
            "cwd": thread.get("cwd"),
            "model": model,
            "reasoning_effort": reasoning,
            "sandbox_policy": json_or_text(thread.get("sandbox_policy")),
            "approval_mode": thread.get("approval_mode") or "-",
            "cli_version": thread.get("cli_version") or "0.121.0",
            "source": thread.get("thread_source") or thread.get("source") or "-",
            "updated_at": thread.get("updated_at"),
            "updated_at_local": local_time_from_epoch(thread.get("updated_at")),
        },
        "usage": {
            "thread_tokens_used": thread_tokens,
            "goal_tokens_used": goal_tokens,
            "token_budget": token_budget,
            "weekly_usage": account_rate_limits.get("secondary"),
            "weekly_reset_at": (account_rate_limits.get("secondary") or {}).get("resets_at"),
            "account_limits_available": account_rate_limits.get("account_limits_available", False),
            "account_rate_limits": account_rate_limits,
            "thread_tokens_source": STATE_DB,
            "goal_tokens_source": GOALS_DB if goal else None,
            "account_limits_note": account_rate_limits.get("error") or account_rate_limits.get("raw_source"),
        },
        "logs": {
            "events_last_5m": logs_5m,
        },
        "spotify": safe_read_playback(),
    }


def make_status():
    with STATE_LOCK:
        manual = STATE.get("manual_override")

    state = make_codex_status()
    if manual:
        state.update(manual)

    state.update({
        "service": "Codex Local Sidecar",
        "updated_at": iso_now(),
        "uptime_seconds": int(time.time() - STARTED_AT),
        "workspace": os.getcwd(),
        "adb": ADB_INFO,
    })
    return state


def merge_status(payload):
    with STATE_LOCK:
        if payload.get("clear_manual_override"):
            STATE["manual_override"] = None
        else:
            STATE["manual_override"] = payload


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/spotify-art" or self.path.startswith("/spotify-art?"):
            self.send_spotify_art()
            return
        if self.path not in ("/", "/status"):
            self.send_json({"error": "not found"}, status=404)
            return
        self.send_json(make_status())

    def do_POST(self):
        if self.path == "/playback/toggle":
            self.send_json(toggle_playback())
            return

        if self.path != "/status":
            self.send_json({"error": "not found"}, status=404)
            return

        length = int(self.headers.get("Content-Length", "0"))
        if length > 32768:
            self.send_json({"error": "payload too large"}, status=413)
            return

        try:
            raw = self.rfile.read(length).decode("utf-8") if length else "{}"
            payload = json.loads(raw)
            if not isinstance(payload, dict):
                raise ValueError("JSON body must be an object")
            merge_status(payload)
            self.send_json(make_status())
        except Exception as exc:
            self.send_json({"error": str(exc)}, status=400)

    def send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def send_spotify_art(self):
        with SPOTIFY_LOCK:
            data = SPOTIFY_ART["bytes"]
            content_type = SPOTIFY_ART["content_type"]
        if not data:
            self.send_json({"error": "no spotify artwork cached"}, status=404)
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print("Serving Codex Local Sidecar on http://%s:%s/status" % (args.host, args.port), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
