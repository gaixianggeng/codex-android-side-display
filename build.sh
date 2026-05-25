#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_ROOT" ]; then
    for candidate in "$HOME/Library/Android/sdk" "/opt/homebrew/share/android-commandlinetools"; do
        if [ -d "$candidate/build-tools" ] && [ -d "$candidate/platforms" ]; then
            SDK_ROOT="$candidate"
            break
        fi
    done
fi
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
if [ -z "$SDK_ROOT" ] || [ ! -d "$SDK_ROOT/build-tools" ] || [ ! -d "$SDK_ROOT/platforms" ]; then
    echo "Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
    exit 1
fi

BUILD_TOOLS="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -r | head -n 1)"
ANDROID_PLATFORM="$(find "$SDK_ROOT/platforms" -mindepth 1 -maxdepth 1 -type d -name 'android-35*' | sort -r | head -n 1)"
if [ -z "$ANDROID_PLATFORM" ]; then
    ANDROID_PLATFORM="$(find "$SDK_ROOT/platforms" -mindepth 1 -maxdepth 1 -type d | sort -r | head -n 1)"
fi
ANDROID_JAR="$ANDROID_PLATFORM/android.jar"
if [ -z "$BUILD_TOOLS" ] || [ ! -x "$BUILD_TOOLS/aapt2" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "Android build tools or platform android.jar not found under $SDK_ROOT." >&2
    exit 1
fi
OUT_DIR="$APP_DIR/build"
GEN_DIR="$OUT_DIR/gen"
CLASS_DIR="$OUT_DIR/classes"
DEX_DIR="$OUT_DIR/dex"
CLASSES_JAR="$OUT_DIR/classes.jar"
UNSIGNED_APK="$OUT_DIR/HelloWorld-unsigned.apk"
DEXED_APK="$OUT_DIR/HelloWorld-dexed.apk"
ALIGNED_APK="$OUT_DIR/HelloWorld-aligned.apk"
SIGNED_APK="$OUT_DIR/HelloWorld.apk"
KEYSTORE="$APP_DIR/debug.keystore"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf "$OUT_DIR"
mkdir -p "$GEN_DIR" "$CLASS_DIR" "$DEX_DIR"

"$BUILD_TOOLS/aapt2" link \
    -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    --java "$GEN_DIR" \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    -o "$UNSIGNED_APK"

javac \
    -source 8 \
    -target 8 \
    -classpath "$ANDROID_JAR" \
    -d "$CLASS_DIR" \
    "$APP_DIR/src/com/example/helloworld/MainActivity.java"

jar cf "$CLASSES_JAR" -C "$CLASS_DIR" .

"$BUILD_TOOLS/d8" \
    --min-api 21 \
    --lib "$ANDROID_JAR" \
    --output "$DEX_DIR" \
    "$CLASSES_JAR"

cp "$UNSIGNED_APK" "$DEXED_APK"
(cd "$DEX_DIR" && zip -q -r "$DEXED_APK" classes.dex)
if [ -d "$APP_DIR/assets" ]; then
    (cd "$APP_DIR" && zip -q -r "$DEXED_APK" assets)
fi

"$BUILD_TOOLS/zipalign" -f -p 4 "$DEXED_APK" "$ALIGNED_APK"

if [ ! -f "$KEYSTORE" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" \
        -noprompt >/dev/null
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

"$BUILD_TOOLS/apksigner" verify --verbose "$SIGNED_APK"
echo "$SIGNED_APK"
