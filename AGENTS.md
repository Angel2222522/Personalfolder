# AGENTS.md

## Mandatory release continuity rules

These rules apply to every future coding agent, maintenance task, refactor and release of this repository.

- Treat Personal Folder as one continuously upgradeable Android application, never as disposable builds.
- Preserve the release `applicationId`: `com.angel.personalfolder`.
- Every release intended for the user must use the **same signing certificate/key as the previously installed release**.
- Never present an APK signed with a new/generated debug key as a compatible update.
- Never place a private signing key in this public repository. Use secure external signing material/secrets.
- If the correct signing material is unavailable, do not claim the APK can update the installed app.
- Increase `versionCode` monotonically for every new release.
- Preserve user data across every upgrade. Do not use destructive database migration, uninstall/reinstall, clearing app data, or changing storage/encryption assumptions as a shortcut.
- Any Room schema change must include explicit migrations that preserve existing rows, relations and identifiers.
- Preserve compatibility with existing encrypted documents, OCR data, metadata, cases, checklist items, timeline entries, reminders and backups.
- Before delivering a release APK, verify the upgrade path: applicationId, versionCode, signing certificate, migrations and access to existing stored files/data.
- Release continuity and data preservation are **release blockers**, not optional quality improvements.

See `docs/RELEASE_CONTINUITY.md` for the full policy.
