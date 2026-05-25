# 部署文档

## 架构

项目由两部分组成：

- Android app：原生 Java Activity，轮询本地 sidecar 并绘制黑胶唱片风格界面。
- Python sidecar：监听 `0.0.0.0:8765`，提供 `/status`、`/spotify-art` 和 `/playback/toggle`。

Android app 会优先请求局域网地址，再尝试 `127.0.0.1`。USB 连接时可通过 `adb reverse tcp:8765 tcp:8765` 让设备访问主机 sidecar；无线部署时设备和主机需要在同一网络。

## 本地部署

1. 检查设备：

```bash
adb devices -l
```

2. 启动 sidecar：

```bash
./start_sidecar.sh
```

3. 构建 APK：

```bash
./build.sh
```

4. 安装并启动：

```bash
adb -s <设备序列号> install -r build/HelloWorld.apk
adb -s <设备序列号> shell am force-stop com.example.helloworld
adb -s <设备序列号> shell am start -W -n com.example.helloworld/.MainActivity
```

5. 截图验证：

```bash
adb -s <设备序列号> shell screencap -p /sdcard/codex-side-display.png
adb -s <设备序列号> pull /sdcard/codex-side-display.png ./docs/screenshot.png
```

## 作为 skill 部署

`dist/skills/codex-side-display/` 是给 OpenClaw、Hermes、Codex 等代理使用的 skill。它包含完整模板和一键部署脚本。

在 skill 目录运行：

```bash
python3 scripts/deploy_side_display.py --target ./workspace --device <设备序列号> --force
```

脚本会复制模板、写入主机 IP、启动 sidecar、构建 APK、安装并打开应用。

常用参数：

- `--host <主机 IP>`：手动指定 Android 访问 sidecar 的地址。
- `--port 8765`：修改 sidecar 端口。
- `--skip-sidecar`：只构建或安装，不启动 sidecar。
- `--skip-install`：只生成 APK，不安装到设备。

## 无线运行

应用本身不要求一直插着数据线。只要 Android 设备能访问主机的 `http://<主机 IP>:8765/status`，就可以脱离 USB 运行。

如果打开后仍是默认状态，优先检查：

```bash
curl http://127.0.0.1:8765/status
curl http://<主机 IP>:8765/status
```

本机可访问但 Android 不可访问时，通常是网络隔离、防火墙、IP 写错或设备没有在同一 Wi-Fi。USB 调试状态下可以临时使用：

```bash
adb -s <设备序列号> reverse tcp:8765 tcp:8765
```

## Codex 用量来源

Codex 用量不是写死的模拟数据。sidecar 会启动本机 Codex app-server，并读取：

```text
account/rateLimits/read
```

界面中的 `5 小时` 和 `1 周` 使用该接口返回的剩余百分比和重置时间。需要本机 Codex 已登录，且 Codex app-server 可正常启动。

## 常见问题

### 打开后只有默认状态

- 确认 sidecar 正在运行。
- 确认 `AndroidManifest.xml` 包含 `android.permission.INTERNET` 和 `android:usesCleartextTraffic="true"`。
- 确认 APK 中的 `BASE_URLS` 第一项是当前主机 IP。
- USB 调试时确认执行过 `adb reverse tcp:8765 tcp:8765`。

### 同时连接多台设备

所有安装、启动和截图命令都要带 `-s <设备序列号>`。

### Spotify 或 Apple Music 图标/封面不显示

Spotify 图标来自 `assets/spotify_icon.png`，Apple Music 图标来自 `assets/apple_music_icon.png`。Spotify 专辑封面来自当前曲目的 artwork URL；Apple Music 专辑封面来自 macOS Music app 的当前曲目 artwork raw data。sidecar 会缓存当前播放源封面，并通过 `/spotify-art` 提供给 Android app。

### LaunchAgent 路径不对

仓库中的 `com.gxg.codex-sidecar.plist` 是模板文件，包含 `__APP_DIR__` 这类占位符。`start_sidecar.sh` 会在 `.runtime/` 下生成当前机器可用的 plist，不需要手动修改模板。
