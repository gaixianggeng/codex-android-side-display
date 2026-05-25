# Codex 侧屏部署参考

## 架构

这个 skill 打包了两部分：

- Android app：原生 Java Activity，无 Gradle，使用 `build.sh` 调用 Android SDK 工具链构建 APK。
- 本地 sidecar：`status_server.py` 监听 `0.0.0.0:8765`，提供 `/status` 和 `/spotify-art`。

Android app 轮询 `http://<host>:8765/status`，展示：

- Spotify 或 Apple Music 当前播放、进度、专辑封面。
- Codex 本地线程 token。
- Codex 真实账户限额：`5 小时` 和 `1 周`，来自本机 Codex app-server 的 `account/rateLimits/read`。

## 运行依赖

主机侧：

- macOS 优先；LaunchAgent 脚本按 macOS 写法提供。
- Python 3。
- Android SDK build-tools 与 platform，默认查找 `$ANDROID_SDK_ROOT`、`$ANDROID_HOME`、`~/Library/Android/sdk`。
- JDK 17，默认查找 `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。
- `adb`。
- Codex CLI 或 Codex Desktop app，默认路径 `/Applications/Codex.app/Contents/Resources/codex`。
- Spotify 和 Apple Music 可选；没有音乐 App 时 app 仍可显示 Codex 数据。

Android 侧：

- Android 5.0+。
- 设备和主机在同一局域网，或可通过 `adb reverse` 访问。
- 新版 Android 需要允许明文 HTTP；模板已设置 `android:usesCleartextTraffic="true"`。

## 一键部署

在 skill 根目录运行：

```bash
python3 scripts/deploy_side_display.py --target ./workspace --device <adb-serial> --force
```

脚本会做这些事：

1. 把 `assets/android-side-display/` 复制到目标目录。
2. 自动检测局域网 IP，并写入 `MainActivity.java` 的 `BASE_URLS`。
3. 修正 LaunchAgent plist 中的工作目录、日志路径和 `status_server.py` 路径。
4. 启动或重启 sidecar。
5. 构建 `build/HelloWorld.apk`。
6. 安装并启动 Android app。

## 手工部署

复制模板：

```bash
cp -R assets/android-side-display ./workspace
cd ./workspace
```

如果主机 IP 不是模板里的地址，编辑：

```text
src/com/example/helloworld/MainActivity.java
```

把 `BASE_URLS` 第一项改成 `http://<host-ip>:8765`。

构建：

```bash
./build.sh
```

启动 sidecar：

```bash
./stop_sidecar.sh
./start_sidecar.sh
```

安装到指定设备：

```bash
adb -s <serial> install -r build/HelloWorld.apk
adb -s <serial> shell am start -W -n com.example.helloworld/.MainActivity
```

截图验证：

```bash
adb -s <serial> shell screencap -p /sdcard/codex-side-display.png
adb -s <serial> pull /sdcard/codex-side-display.png ./codex-side-display.png
```

## 常见问题

### 打开后是默认状态

先在主机测试：

```bash
curl http://127.0.0.1:8765/status
curl http://<host-ip>:8765/status
```

如果本机通但 Android 不通，检查设备是否和主机在同一 Wi-Fi，或尝试 USB 反向代理：

```bash
adb -s <serial> reverse tcp:8765 tcp:8765
```

如果 Pixel 或新版 Android 不通，确认 manifest 中存在：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<application android:usesCleartextTraffic="true" ...>
```

### 多设备连接

必须指定设备序列号：

```bash
adb devices -l
python3 scripts/deploy_side_display.py --device <serial>
```

### Codex 用量不真实或为空

sidecar 通过临时本地 app-server 读取：

```text
account/rateLimits/read
```

需要本机 Codex 已登录。可单独检查：

```bash
curl http://127.0.0.1:8765/status | jq '.usage.account_rate_limits'
```

### LaunchAgent 路径不对

不要直接复用别人机器上的 plist。用 `scripts/deploy_side_display.py` 复制模板，它会把 plist 路径重写到当前目标目录。
