#!/usr/bin/env bash
set -euo pipefail

if [ -z "${SIGNING_BUNDLE_BASE64:-}" ]; then
  echo "Permanent Personal Folder signing secret is missing. Refusing release."
  exit 1
fi

SIGNING_DIR="${RUNNER_TEMP:-/tmp}/personal-folder-signing"
mkdir -p "$SIGNING_DIR"
printf '%s' "$SIGNING_BUNDLE_BASE64" | base64 --decode > "$SIGNING_DIR/signing-bundle.zip"
unzip -q "$SIGNING_DIR/signing-bundle.zip" -d "$SIGNING_DIR"

KEYSTORE_PATH="$SIGNING_DIR/PersonalFolder-release.jks"
CREDENTIALS_FILE="$SIGNING_DIR/signing.env"

if [ ! -f "$KEYSTORE_PATH" ] || [ ! -f "$CREDENTIALS_FILE" ]; then
  echo "Signing bundle is incomplete. Refusing release."
  exit 1
fi

set -a
source "$CREDENTIALS_FILE"
set +a

EXPECTED_SHA256="2642FD5E5E43A90EB6DA7B3462E14313BE9DA8C479ECAABC404076071F0A049B"
ACTUAL_SHA256=$(keytool -list -v \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$PERSONAL_FOLDER_KEYSTORE_PASSWORD" \
  -alias "$PERSONAL_FOLDER_KEY_ALIAS" \
  | sed -n 's/^[[:space:]]*SHA256: //p' \
  | head -n 1 \
  | tr -d ':' \
  | tr '[:lower:]' '[:upper:]')

if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "Signing certificate does not match the permanent Personal Folder certificate. Refusing release."
  exit 1
fi

echo "PERSONAL_FOLDER_KEYSTORE_PATH=$KEYSTORE_PATH" >> "$GITHUB_ENV"
echo "PERSONAL_FOLDER_KEYSTORE_PASSWORD=$PERSONAL_FOLDER_KEYSTORE_PASSWORD" >> "$GITHUB_ENV"
echo "PERSONAL_FOLDER_KEY_ALIAS=$PERSONAL_FOLDER_KEY_ALIAS" >> "$GITHUB_ENV"
echo "PERSONAL_FOLDER_KEY_PASSWORD=$PERSONAL_FOLDER_KEY_PASSWORD" >> "$GITHUB_ENV"
