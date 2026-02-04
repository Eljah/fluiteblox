#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_B64="$REPO_ROOT/keystore/release-key.jks.b64"
KEYSTORE_PATH="$REPO_ROOT/keystore/release-key.jks"

if [[ -f "$KEYSTORE_B64" ]]; then
  base64 -d "$KEYSTORE_B64" > "$KEYSTORE_PATH"
fi
