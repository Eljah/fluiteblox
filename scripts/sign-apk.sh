#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="$REPO_ROOT/keystore/release-key.jks"
KEY_ALIAS="${KEY_ALIAS:-release}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-Tatarstan1920}"
KEY_PASSWORD="${KEY_PASSWORD:-Tatarstan1920}"

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
  jarsigner \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    "$apk" \
    "$KEY_ALIAS"
  echo "Signed $apk"
done
