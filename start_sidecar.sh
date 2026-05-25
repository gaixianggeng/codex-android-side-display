#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-8765}"
PID_FILE="$APP_DIR/status_server.pid"
PLIST_TEMPLATE="$APP_DIR/com.gxg.codex-sidecar.plist"
RUNTIME_DIR="$APP_DIR/.runtime"
PLIST="$RUNTIME_DIR/com.gxg.codex-sidecar.plist"
LABEL="com.gxg.codex-sidecar"
DOMAIN="gui/$(id -u)"
PYTHON_BIN="${PYTHON_BIN:-/usr/bin/python3}"
ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
ADB_BIN="${ADB_BIN:-adb}"

mkdir -p "$RUNTIME_DIR"
"$PYTHON_BIN" - "$PLIST_TEMPLATE" "$PLIST" "$APP_DIR" "$PORT" "$PYTHON_BIN" "$ADB_BIN" <<'PY'
import sys
template, output, app_dir, port, python_bin, adb_bin = sys.argv[1:]
text = open(template, encoding="utf-8").read()
for key, value in {
    "__APP_DIR__": app_dir,
    "__PORT__": port,
    "__PYTHON_BIN__": python_bin,
    "__ADB_BIN__": adb_bin,
}.items():
    text = text.replace(key, value)
open(output, "w", encoding="utf-8").write(text)
PY

launchctl bootout "$DOMAIN" "$PLIST_TEMPLATE" >/dev/null 2>&1 || true
launchctl bootout "$DOMAIN" "$PLIST" >/dev/null 2>&1 || true
launchctl bootstrap "$DOMAIN" "$PLIST"
launchctl kickstart -k "$DOMAIN/$LABEL"
sleep 1

PID="$(pgrep -f "$APP_DIR/status_server.py --host 0.0.0.0 --port $PORT" | head -n 1 || true)"
if [ -z "$PID" ]; then
    echo "Sidecar did not start. Check $APP_DIR/status_server.err.log"
    exit 1
fi
echo "$PID" > "$PID_FILE"
echo "Started sidecar with pid $PID"

if [ -n "$ADB_BIN" ]; then
    "$ADB_BIN" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null 2>&1 || true
fi
LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
echo "LAN endpoint: http://${LAN_IP:-<mac-ip>}:$PORT/status"
