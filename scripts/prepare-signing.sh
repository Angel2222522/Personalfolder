#!/usr/bin/env bash
set -euo pipefail

if [ -z "${KEYSTORE_BASE64:-}" ] || [ -z "${KEYSTORE_PASSWORD:-}" ] || [ -z "${KEY_ALIAS:-}" ] || [ -z "${KEY_PASSWORD:-}" ]; then
  echo "Permanent Personal Folder signing secret is missing. Refusing release."
  exit 1
fi

SIGNING_DIR="${RUNNER_TEMP:-/tmp}/personal-folder-signing"
mkdir -p "$SIGNING_DIR"
KEYSTORE_PATH="$SIGNING_DIR/personal-folder-release.jks"
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE_PATH"

EXPECTED_SHA256="2642FD5E5E43A90EB6DA7B3462E14313BE9DA8C479ECAABC404076071F0A049B"
ACTUAL_SHA256=$(keytool -list -v \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$KEYSTORE_PASSWORD" \
  -alias "$KEY_ALIAS" \
  | sed -n 's/^[[:space:]]*SHA256: //p' \
  | head -n 1 \
  | tr -d ':' \
  | tr '[:lower:]' '[:upper:]')

if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "Signing certificate does not match the permanent Personal Folder certificate. Refusing release."
  exit 1
fi

echo "PERSONAL_FOLDER_KEYSTORE_PATH=$KEYSTORE_PATH" >> "$GITHUB_ENV"
echo "PERSONAL_FOLDER_KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD" >> "$GITHUB_ENV"
echo "PERSONAL_FOLDER_KEY_ALIAS=$KEY_ALIAS" >> "$GITHUB_ENV"
echo "PERSONAL_FOLDER_KEY_PASSWORD=$KEY_PASSWORD" >> "$GITHUB_ENV"
