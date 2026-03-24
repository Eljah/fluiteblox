#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"
AVD_NAME="${ANDROID_AVD_NAME:-codexApi34}"
SYSTEM_IMAGE="${ANDROID_SYSTEM_IMAGE:-system-images;android-34;google_apis;x86_64}"
DEVICE_NAME="${ANDROID_AVD_DEVICE:-pixel_6}"

if command -v apt-get >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y \
    libx11-xcb1 \
    libxcb-cursor0 \
    libxcb-icccm4 \
    libxcb-image0 \
    libxcb-keysyms1 \
    libxcb-render-util0 \
    libxcb-xkb1 \
    libxkbcommon-x11-0
fi

"$(dirname "$0")/setup_android_sdk.sh"

yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses --sdk_root="$SDK_ROOT" >/dev/null || true
"$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_ROOT" \
  "emulator" \
  "$SYSTEM_IMAGE"

if ! "$SDK_ROOT/cmdline-tools/latest/bin/avdmanager" list avd | grep -q "Name: ${AVD_NAME}$"; then
  echo "no" | "$SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd \
    -n "$AVD_NAME" \
    -k "$SYSTEM_IMAGE" \
    --device "$DEVICE_NAME"
fi

echo "Emulator setup complete"
echo "AVD: $AVD_NAME"
