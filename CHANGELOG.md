# Changelog

## 2.0.1

### Integrity, PDF and OCR

- Added Room `3→4` migration with per-field metadata confidence/manual ownership, expiry suggestions, reminder deadlines and repair of partial FTS indexes.
- Added a shared filesystem/Room operation boundary, recoverable document-delete quarantine and conservative interrupted-restore recovery.
- Fixed PDF rendering by compositing rendered pages onto opaque white ARGB bitmaps. Viewer and OCR now use independent bitmap pipelines; originals remain encrypted and unchanged.
- Corrected protocol labels, provider hierarchy, UI category rules, OCR title application and expiry/reminder confidence handling.
- Preserved past reminders for delivery after notification-permission recovery and kept the real deadline separate from the trigger date.

### Verification and release

- Added migration, recovery-policy, metadata, reminder and representative-PDF regression coverage.
- CI now executes instrumentation tests on an Android emulator and uploads the debug APK before any release-signing gate.
- Raised `versionCode` to 3 / `versionName` to 2.0.1 while preserving package identity and the explicit release signing policy.

## 2.0.0

### Data safety

- Added safe Room `1→2→3` migrations without destructive fallback.
- Added document/page/case/checklist/reminder foreign keys and consistent cascade/SET NULL behavior.
- Added manual metadata persistence so OCR reruns do not overwrite user corrections.
- Added durable restore journal and startup recovery for interrupted filesystem/database swaps.

### Documents and processing

- Documents now preserve ordered image/PDF sources and expand nested PDF pages for viewing, sharing and export.
- Added internal bounded viewer with page navigation, zoom and pan.
- Added multi-page camera session, retry-last-page flow, conservative offline crop/rotation/contrast processing and pre-save preview.
- Hardened Greek/English Tesseract asset installation, OCR bounds, cancellation handling and retryable I/O failures.
- Reworked metadata date matching to use original OCR ranges and context confidence; low-confidence expiry guesses do not create reminders automatically.

### Cases, search and reminders

- Completed case fields/editing, checklist-to-document links, timeline and deadline reminders.
- Replaced full-text `LIKE` search with Room FTS plus useful library filters.
- Reminder editing/deletion/restore paths cancel unique work and avoid duplicate or past-date immediate jobs.

### Security and UX

- Biometric lock is fail-closed, sensitive windows use `FLAG_SECURE`, and password inputs are masked.
- Added deterministic temporary plaintext cleanup and startup recovery, generic notifications, and fail-closed Keystore-loss handling.
- New backups require a 12-character password minimum; restore keeps compatibility with existing 8-character backups.
- Updated Greek Material 3 UI, empty/error states, dark theme, edge-to-edge handling and honest backup/export copy.

### Verification

- Added metadata unit tests, migration instrumentation tests and backup crypto/restore instrumentation tests.
- CI quality gates now include unit tests, lint, debug APK, androidTest compilation and release build verification.
