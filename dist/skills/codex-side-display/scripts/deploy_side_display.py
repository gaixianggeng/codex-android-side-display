#!/usr/bin/env python3
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path


SKILL_DIR = Path(__file__).resolve().parents[1]
TEMPLATE_DIR = SKILL_DIR / "assets" / "android-side-display"


def run(command, cwd=None, check=True):
    print("+ " + " ".join(str(part) for part in command))
    return subprocess.run(command, cwd=cwd, check=check, text=True)


def capture(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


def detect_host():
    env_host = os.environ.get("SIDECAR_HOST", "").strip()
    if env_host:
        return env_host
    if sys.platform == "darwin":
        for iface in ("en0", "en1"):
            value = capture(["ipconfig", "getifaddr", iface])
            if value:
                return value
    value = capture(["hostname", "-I"]).split()
    return value[0] if value else "127.0.0.1"


def copy_template(target, force=False):
    if target.exists():
        if not force:
            print("目标目录已存在，复用现有目录: %s" % target)
            return
        shutil.rmtree(target)
    shutil.copytree(TEMPLATE_DIR, target)
    for path in (
        target / "build.sh",
        target / "status_server.py",
        target / "start_sidecar.sh",
        target / "stop_sidecar.sh",
        target / "update_status.sh",
    ):
        if path.exists():
            path.chmod(path.stat().st_mode | 0o755)


def configure_app_urls(target, host, port):
    java_file = target / "src" / "com" / "example" / "helloworld" / "MainActivity.java"
    text = java_file.read_text(encoding="utf-8")
    replacement = (
        'private static final String[] BASE_URLS = {\n'
        '            "http://%s:%d",\n'
        '            "http://127.0.0.1:%d"\n'
        '    };'
    ) % (host, port, port)
    text = re.sub(
        r'private static final String\[\] BASE_URLS = \{\s*".*?",\s*".*?"\s*\};',
        replacement,
        text,
        flags=re.S,
    )
    java_file.write_text(text, encoding="utf-8")


def configure_launch_agent(target, port):
    plist = target / "com.gxg.codex-sidecar.plist"
    text = plist.read_text(encoding="utf-8")
    adb = shutil.which("adb") or "/opt/homebrew/bin/adb"
    for key, value in {
        "__APP_DIR__": str(target),
        "__PORT__": str(port),
        "__PYTHON_BIN__": sys.executable or "/usr/bin/python3",
        "__ADB_BIN__": adb,
    }.items():
        text = text.replace(key, value)
    plist.write_text(text, encoding="utf-8")


def choose_device(adb, requested):
    if requested:
        return requested
    result = subprocess.check_output([adb, "devices", "-l"], text=True)
    devices = []
    for line in result.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    if len(devices) == 1:
        return devices[0]
    if len(devices) > 1:
        raise SystemExit("检测到多个设备，请用 --device 指定序列号: %s" % ", ".join(devices))
    raise SystemExit("没有检测到可用 Android 设备，请检查 adb devices。")


def main():
    parser = argparse.ArgumentParser(description="部署 Codex Android 侧屏与本地 sidecar。")
    parser.add_argument("--target", default="./codex-side-display-workspace", help="复制模板项目的目标目录")
    parser.add_argument("--device", help="adb 设备序列号；多设备连接时必须指定")
    parser.add_argument("--host", default=None, help="Android 访问 sidecar 的主机 IP，默认自动检测")
    parser.add_argument("--port", type=int, default=8765, help="sidecar 端口")
    parser.add_argument("--adb", default=os.environ.get("ADB_BIN") or shutil.which("adb") or "adb")
    parser.add_argument("--force", action="store_true", help="删除并重建目标目录")
    parser.add_argument("--skip-sidecar", action="store_true", help="不启动 sidecar")
    parser.add_argument("--skip-install", action="store_true", help="只构建 APK，不安装到设备")
    args = parser.parse_args()

    target = Path(args.target).expanduser().resolve()
    host = args.host or detect_host()
    copy_template(target, force=args.force)
    configure_app_urls(target, host, args.port)
    configure_launch_agent(target, args.port)

    if not args.skip_sidecar:
        run([str(target / "stop_sidecar.sh"), str(args.port)], cwd=target, check=False)
        run([str(target / "start_sidecar.sh"), str(args.port)], cwd=target)
        time.sleep(1)

    run([str(target / "build.sh")], cwd=target)

    if not args.skip_install:
        serial = choose_device(args.adb, args.device)
        apk = target / "build" / "HelloWorld.apk"
        run([args.adb, "-s", serial, "install", "-r", str(apk)])
        run([args.adb, "-s", serial, "shell", "am", "force-stop", "com.example.helloworld"], check=False)
        run([args.adb, "-s", serial, "shell", "am", "start", "-W", "-n", "com.example.helloworld/.MainActivity"])

    print("部署完成。Sidecar: http://%s:%d/status" % (host, args.port))


if __name__ == "__main__":
    main()
