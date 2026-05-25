# Codex Android Side Display

A native Android side-display app for turning an old or idle Android tablet into a dedicated sidecar display for Spotify playback, album art, and real Codex usage. The repository includes the Android app, a local Python sidecar, and a packaged skill that can be used by agents such as OpenClaw, Hermes, or Codex to reproduce the deployment.

![Latest tablet screenshot](docs/screenshot.png)

## Features

- Turns an unused Android tablet into a useful always-on desk display.
- Shows the Spotify icon, track, artist, album, playback progress, and album art.
- Shows real Codex account usage, including the `5 hour` and `1 week` remaining limits and reset times.
- Adapts between landscape and portrait layouts.
- Supports both USB `adb reverse` and local network access.
- Builds without Gradle by using the Android SDK command-line tools directly.

## Project Layout

- `src/com/example/helloworld/MainActivity.java`: native Android UI and status polling.
- `status_server.py`: local sidecar that serves `/status`, `/spotify-art`, and playback control.
- `build.sh`: builds and signs a debug APK.
- `start_sidecar.sh` / `stop_sidecar.sh`: starts or stops the local sidecar.
- `dist/skills/codex-side-display/`: complete skill package for agent-driven deployment.
- `docs/DEPLOYMENT.md`: deployment and troubleshooting guide in Chinese.

## Quick Start

Requirements:

- macOS, Python 3, and ADB.
- Android SDK build-tools and platform packages.
- JDK 17.
- Codex Desktop or Codex CLI. The default Codex binary path is `/Applications/Codex.app/Contents/Resources/codex`.
- Spotify is optional. Codex data still works when Spotify is not running.

Build the APK:

```bash
./build.sh
```

Start the sidecar:

```bash
./start_sidecar.sh
```

Install and launch on a connected Android device:

```bash
adb devices -l
adb -s <device-serial> install -r build/HelloWorld.apk
adb -s <device-serial> shell am start -W -n com.example.helloworld/.MainActivity
```

For one-command deployment, LAN setup, and troubleshooting, see [the deployment guide](docs/DEPLOYMENT.md).

## Skill Deployment

`dist/skills/codex-side-display/` is a complete skill package for OpenClaw, Hermes, Codex, and similar agents. From the skill directory, run:

```bash
python3 scripts/deploy_side_display.py --target ./workspace --device <device-serial> --force
```

The script copies the template, writes the current host IP, starts the sidecar, builds the APK, installs it, and launches the app.

## Security Notes

This repository does not require API keys, access tokens, or private keys. `debug.keystore`, build outputs, logs, PID files, local screenshots, and local technical notes are ignored by `.gitignore`.

`status_server.py` reads local Codex state databases and Codex app-server account rate-limit data at runtime. Those values are read dynamically on the host machine and are not committed to the repository.
