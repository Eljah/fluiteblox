#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_B64="$REPO_ROOT/keystore/release-key.jks.b64"
KEYSTORE_PATH="$REPO_ROOT/keystore/release-key.jks"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-34}"
BUILD_TOOLS_AAPT2="${BUILD_TOOLS_AAPT2:-34.0.0}"

if [[ -f "$KEYSTORE_B64" ]]; then
  base64 -d "$KEYSTORE_B64" > "$KEYSTORE_PATH"
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set." >&2
  exit 1
fi

AAPT2="$ANDROID_HOME/build-tools/$BUILD_TOOLS_AAPT2/aapt2"
if [[ ! -x "$AAPT2" ]]; then
  echo "aapt2 not found at $AAPT2" >&2
  exit 1
fi

BUNDLETOOL_JAR="$REPO_ROOT/tools/bundletool.jar"
if [[ ! -f "$BUNDLETOOL_JAR" ]]; then
  mkdir -p "$REPO_ROOT/tools"
  curl -sSL -o "$BUNDLETOOL_JAR" \
    https://github.com/google/bundletool/releases/download/1.16.0/bundletool-all-1.16.0.jar
fi

WORK_DIR=$(mktemp -d)
COMPILED_RES_DIR="$WORK_DIR/compiled-res"
BASE_APK="$WORK_DIR/base.apk"
MODULE_ZIP="$WORK_DIR/base.zip"

mkdir -p "$COMPILED_RES_DIR"
RES_DIRS=("$REPO_ROOT/src/main/res")
if [[ -d "$REPO_ROOT/target/unpacked-libs" ]]; then
  while IFS= read -r -d '' lib_res; do
    RES_DIRS+=("$lib_res")
  done < <(find "$REPO_ROOT/target/unpacked-libs" -type d -name res -print0)
fi

COMPILED_ARGS=()
index=0
for res_dir in "${RES_DIRS[@]}"; do
  if [[ -d "$res_dir" ]]; then
    compiled_zip="$COMPILED_RES_DIR/res-$index.zip"
    "$AAPT2" compile --dir "$res_dir" -o "$compiled_zip"
    COMPILED_ARGS+=("-R" "$compiled_zip")
    index=$((index + 1))
  fi
done

"$AAPT2" link --proto-format \
  -I "$ANDROID_HOME/platforms/android-$ANDROID_PLATFORM/android.jar" \
  --manifest "$REPO_ROOT/src/main/AndroidManifest.xml" \
  --auto-add-overlay \
  -o "$BASE_APK" \
  "${COMPILED_ARGS[@]}"

unzip -q "$BASE_APK" -d "$WORK_DIR/apk"
mkdir -p "$WORK_DIR/module/manifest" "$WORK_DIR/module/dex" "$WORK_DIR/module/res"
cp "$WORK_DIR/apk/AndroidManifest.xml" "$WORK_DIR/module/manifest/AndroidManifest.xml"
cp "$WORK_DIR/apk/resources.pb" "$WORK_DIR/module/resources.pb"
if [[ -d "$WORK_DIR/apk/res" ]]; then
  cp -R "$WORK_DIR/apk/res/"* "$WORK_DIR/module/res/"
fi
if compgen -G "$REPO_ROOT/target/classes*.dex" > /dev/null; then
  cp "$REPO_ROOT/target"/classes*.dex "$WORK_DIR/module/dex/"
fi

(
  cd "$WORK_DIR/module"
  zip -qr "$MODULE_ZIP" .
)

mkdir -p "$REPO_ROOT/target"
AAB_PATH="$REPO_ROOT/target/app-release.aab"
java -jar "$BUNDLETOOL_JAR" build-bundle \
  --modules="$MODULE_ZIP" \
  --output="$AAB_PATH" \
  --overwrite

jarsigner \
  -keystore "$KEYSTORE_PATH" \
  -storepass changeit \
  -keypass changeit \
  "$AAB_PATH" release

rm -rf "$WORK_DIR"
