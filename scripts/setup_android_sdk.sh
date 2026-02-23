#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"
BASE_DIR="$(dirname "$SDK_ROOT")"
CMDLINE_VER="11076708"

mkdir -p "$SDK_ROOT" "$BASE_DIR"

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK_ROOT/cmdline-tools"
  curl -fsSL -o "$BASE_DIR/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VER}_latest.zip"
  unzip -q -o "$BASE_DIR/cmdline-tools.zip" -d "$SDK_ROOT/cmdline-tools"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

# Some legacy Maven Android plugins still probe this file.
if [ ! -f "$SDK_ROOT/tools/source.properties" ]; then
  curl -fsSL -o "$BASE_DIR/tools_r25.2.5-linux.zip" \
    "https://dl.google.com/android/repository/tools_r25.2.5-linux.zip"
  unzip -q -o "$BASE_DIR/tools_r25.2.5-linux.zip" -d "$SDK_ROOT"
fi

set +o pipefail
yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses --sdk_root="$SDK_ROOT" >/dev/null || true
set -o pipefail
"$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_ROOT" \
  "platforms;android-34" \
  "build-tools;28.0.3" \
  "build-tools;34.0.0" \
  "platform-tools"

echo "SDK ready: $SDK_ROOT"
ls "$SDK_ROOT/platforms/android-34/android.jar"
