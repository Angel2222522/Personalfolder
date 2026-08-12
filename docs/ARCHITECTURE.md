# V2 architecture

## Boundaries

```text
Compose screens/dialogs
        ↓
FolderViewModel (state, user actions, messages)
        ↓
FolderRepository / BackupService / ExportService
        ↓
LibraryOperationCoordinator
        ↓
Room DAOs + encrypted files + WorkManager
        ↓
DocumentProcessor / Tesseract / DocumentRenderService / ReminderWorker
```

- `ui/` contains the app shell, screens, reusable cards, dialogs, filters and the internal viewer. `FolderApp.kt` is limited to navigation/shell composition; `FolderScreens.kt`, `FolderComponents.kt` and `FolderDialogs.kt` hold the corresponding UI groups.
- `FolderViewModel` owns UI state and turns repository failures into user-visible messages. It does not access SQL directly.
- `FolderRepository` owns document/case operations, import validation, transactions and work enqueueing.
- `data/` contains Room entities/DAOs, migrations, FTS setup, export, backup and rendering services. `LibraryOperationCoordinator` serializes operations that can change both the database and encrypted filesystem; Room transactions still protect database-only atomicity.
- `processing/` contains OCR, metadata extraction, conservative scanner image processing and the shared `DocumentSourceClassifier` used by import, OCR and rendering.
- `security/` contains Keystore file encryption, password backup encryption and temporary-file recovery.
- `workers/` contains bounded background OCR and reminders.

## Document model

`DocumentEntity` is the logical document. `DocumentPageEntity` is an ordered source: an image is one logical page and a PDF source expands to its rendered page count. A document can therefore contain multiple images, multiple PDFs, or mixed sources without treating the first source as the whole document.

Document bytes remain under `filesDir/documents/<documentId>/` and database paths are accepted only when they resolve below that root. `DocumentStorage` is the single path/containment helper used by import and file crypto. Viewer, OCR and share/export decrypt one source at a time into cache and delete it in `finally`; startup recovery removes stale temp files.

PDF detection is centralized in `DocumentSourceClassifier`, so MIME type and filename fallback rules do not diverge between import, OCR and rendering. Unknown metadata remains unknown: the extractor does not invent an expiry date from an unlabeled date merely because multiple dates are present.

## Persistence and migrations

The current Room version is 4:

- `1→2`: preserved the already-compatible V1 schema.
- `2→3`: adds manual metadata state, page source metadata, foreign keys, cascades, indexes and the FTS search table/triggers.
- `3→4`: adds the declared `checklist_items.linkedDocumentId` index that was missing from the earlier migration. It is a non-destructive index-only migration.

Restore parses and validates all parent/child IDs before a transaction. Files are staged first; a durable restore journal supports startup recovery if the filesystem swap and Room transaction are interrupted.

## Background work

OCR is unique per document and serialized in-process to bound Tesseract memory. It preserves manual metadata and treats cancellation as cancellation. Restore acquires the OCR processing lock before the library-operation lock, matching the worker order and preventing lock inversion. Reminder work uses unique IDs, tags, cancellation on delete/edit and a complete reschedule pass after restore/startup. The ViewModel exposes busy state from an active-operation count, so overlapping operations cannot make the UI appear idle while work still runs.
