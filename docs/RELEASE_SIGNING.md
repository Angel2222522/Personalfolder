# Permanent release signing

The Personal Folder permanent release signing chain begins with the next clean installation/release that uses the certificate below.

## Permanent public certificate identity

- Alias: `personalfolder-release`
- SHA-256 certificate fingerprint: `26:42:FD:5E:5E:43:A9:0E:B6:DA:7B:34:62:E1:43:13:BE:9D:A8:C4:79:EC:AA:BC:40:40:76:07:1F:0A:04:9B`

This fingerprint is public and is intentionally committed to the repository. The private keystore and passwords must never be committed.

## Required GitHub Actions secrets

The repository workflow expects all four secrets below for every normal release from `main`:

- `PERSONAL_FOLDER_KEYSTORE_BASE64`
- `PERSONAL_FOLDER_KEYSTORE_PASSWORD`
- `PERSONAL_FOLDER_KEY_ALIAS`
- `PERSONAL_FOLDER_KEY_PASSWORD`

If any secret is missing, or if the supplied keystore certificate does not match the permanent SHA-256 fingerprint, the workflow must refuse to create/upload a normal release APK.

The workflow also verifies the signature of the final APK before uploading the `personal-folder-signed-release-apk` artifact.

## Chain start

An APK signed with a previous/debug certificate cannot be updated in-place to this certificate. The first installation using this permanent certificate establishes the new signing chain. Every subsequent release must use this same certificate and preserve application data through normal Android upgrade and explicit database/storage migrations.
