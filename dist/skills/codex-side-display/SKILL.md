---
name: codex-side-display
description: Deploy and maintain a native Android side-display app plus a local Python sidecar that shows Spotify playback, album art, and real Codex account usage/rate-limit data. Use when the user asks to install, package, deploy, run, debug, or adapt this Android black-vinyl Codex/Spotify status screen on connected Android devices, or when preparing the project for OpenClaw, Hermes, Codex, or another coding agent to reproduce the deployment.
---

# Codex 侧屏部署

## 核心流程

1. 读取 `references/deployment.md`，确认目标机器是否有 Android SDK、ADB、Python 3、Codex CLI/desktop app、Spotify。
2. 使用 `scripts/deploy_side_display.py` 从 `assets/android-side-display/` 复制模板项目，自动写入当前局域网 IP，启动 sidecar，构建 APK，并安装到指定 Android 设备。
3. 多台设备同时连接时，先运行 `adb devices -l`，再把序列号传给 `--device`。
4. 安装后截图验证：界面应显示真实 Spotify 信息、专辑封面、Codex `5 小时` 与 `1 周` 剩余用量。

## 常用命令

在 skill 根目录运行：

```bash
python3 scripts/deploy_side_display.py --target ./workspace --device <adb-serial> --force
```

只构建不安装：

```bash
python3 scripts/deploy_side_display.py --target ./workspace --skip-install
```

指定 sidecar 主机地址：

```bash
python3 scripts/deploy_side_display.py --target ./workspace --host 192.168.31.163 --device <adb-serial>
```

## 调试判断

- Android 打开后仍是默认状态：检查 Mac/主机上的 `http://<host>:8765/status` 是否可访问，确认 APK 里 `BASE_URLS` 已写入当前主机 IP。
- Pixel/新版 Android 无法请求 HTTP：确认 `AndroidManifest.xml` 有 `android:usesCleartextTraffic="true"` 和 `android.permission.INTERNET`。
- 连接多台设备：所有 `adb install/start/screencap` 命令都必须带 `-s <serial>`。
- Codex 用量为空：优先检查 sidecar 日志；真实用量来自本机 `codex app-server --listen ws://127.0.0.1:<port>` 的 `account/rateLimits/read`。

## 资源说明

- `assets/android-side-display/`：完整可部署模板，包括原生 Android Java app、Python sidecar、构建脚本、LaunchAgent 模板和 Spotify 图标。
- `scripts/deploy_side_display.py`：一键复制、配置、启动、构建、安装脚本。
- `references/deployment.md`：架构、依赖、手工部署命令和常见问题。
