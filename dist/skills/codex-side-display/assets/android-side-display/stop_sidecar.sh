#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-8765}"
PID_FILE="$APP_DIR/status_server.pid"
PLIST_TEMPLATE="$APP_DIR/com.gxg.codex-sidecar.plist"
PLIST="$APP_DIR/.runtime/com.gxg.codex-sidecar.plist"
DOMAIN="gui/$(id -u)"

if launchctl bootout "$DOMAIN" "$PLIST" >/dev/null 2>&1 || launchctl bootout "$DOMAIN" "$PLIST_TEMPLATE" >/dev/null 2>&1; then
    rm -f "$PID_FILE"
    echo "Stopped sidecar"
else
    echo "Sidecar is not running"
fi

ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
if [ -n "$ADB_BIN" ]; then
    "$ADB_BIN" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
fi
