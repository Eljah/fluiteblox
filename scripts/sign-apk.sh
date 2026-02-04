#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="$REPO_ROOT/keystore/release-key.jks"
KEY_ALIAS="${KEY_ALIAS:-release}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-Tatarstan1920}"
KEY_PASSWORD="${KEY_PASSWORD:-Tatarstan1920}"

BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set." >&2
  exit 1
fi

ZIPALIGN="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/zipalign"
APKSIGNER="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/apksigner"

if [[ ! -x "$ZIPALIGN" ]]; then
  echo "zipalign not found at $ZIPALIGN" >&2
  exit 1
fi
if [[ ! -x "$APKSIGNER" ]]; then
  echo "apksigner not found at $APKSIGNER" >&2
  exit 1
fi

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Keystore not found at $KEYSTORE_PATH" >&2
  exit 1
fi

shopt -s nullglob
APK_FILES=("$REPO_ROOT/target"/*.apk)
if [[ ${#APK_FILES[@]} -eq 0 ]]; then
  echo "No APK files found in $REPO_ROOT/target" >&2
  exit 1
fi

for apk in "${APK_FILES[@]}"; do
  echo "Processing: $apk"

  (zip -d "$apk" "META-INF/*" >/dev/null 2>&1) || true

  aligned="${apk%.apk}-aligned.apk"
  "$ZIPALIGN" -f -p 4 "$apk" "$aligned"

  signed="${apk%.apk}-signed.apk"
  "$APKSIGNER" sign \
    --ks "$KEYSTORE_PATH" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASSWORD" \
    --key-pass "pass:$KEY_PASSWORD" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$signed" \
    "$aligned"

  "$APKSIGNER" verify --verbose --print-certs "$signed"

  echo "OK: $signed"
done
