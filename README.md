# Codex Android Side Display / Codex Android 侧屏

[中文](README.zh-CN.md) | [English](README.en.md)

An Android side-display app for turning an old or idle Android tablet into a dedicated sidecar display for Spotify playback, album art, and real Codex usage.

一个 Android 侧屏应用，重点是把老旧或闲置的 Android 平板改造成常驻 sidecar 展示屏，用来展示 Spotify 当前播放、专辑封面和 Codex 真实用量。

![Latest tablet screenshot](docs/screenshot.png)

## Quick Start / 快速开始

Build the APK / 构建 APK：

```bash
./build.sh
```

Start the local sidecar / 启动本地 sidecar：

```bash
./start_sidecar.sh
```

Install and launch on a connected Android device / 安装并启动到已连接设备：

```bash
adb devices -l
adb -s <device-serial> install -r build/HelloWorld.apk
adb -s <device-serial> shell am start -W -n com.example.helloworld/.MainActivity
```

More details / 更多说明：

- [中文 README](README.zh-CN.md)
- [English README](README.en.md)
- [中文部署文档](docs/DEPLOYMENT.md)
