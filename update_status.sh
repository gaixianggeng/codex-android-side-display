#!/usr/bin/env bash
set -euo pipefail

STATE="${1:-working}"
TITLE="${2:-Codex task}"
PHASE="${3:-running}"
DETAIL="${4:-Status updated from local command.}"
PROGRESS="${5:-45}"

python3 - "$STATE" "$TITLE" "$PHASE" "$DETAIL" "$PROGRESS" <<'PY'
import json
import sys
import urllib.request

state, title, phase, detail, progress = sys.argv[1:6]
payload = {
    "state": state,
    "progress": int(progress),
    "task": {
        "title": title,
        "phase": phase,
        "detail": detail,
        "progress": int(progress),
    },
}
request = urllib.request.Request(
    "http://127.0.0.1:8765/status",
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(request, timeout=3) as response:
    print(response.read().decode("utf-8"))
PY
