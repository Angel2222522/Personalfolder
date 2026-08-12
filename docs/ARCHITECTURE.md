# V2 architecture

## Boundaries

```text
Compose screens/dialogs
        ↓
FolderViewModel (state, user actions, messages)
        ↓
FolderRepository / BackupService / ExportService
        ↓
Room DAOs + encrypted files + WorkManager
        ↓
DocumentProcessor / Tesseract / DocumentRenderService / ReminderWorker
```

- `ui/` contains screens, reusable cards, dialogs, filters and the internal viewer.
- `FolderViewModel` owns UI state and turns repository failures into user-visible messages. It does not access SQL directly.
- `FolderRepository` owns document/case operations, import validation, transactions and work enqueueing.
- `data/` contains Room entities/DAOs, migrations, FTS setup, export, backup and rendering services.
- `processing/` contains OCR, metadata extraction and conservative scanner image processing.
- `security/` contains Keystore file encryption, password backup encryption and temporary-file recovery.
- `workers/` contains bounded background OCR and reminders.

## Document model

`DocumentEntity` is the logical document. `DocumentPageEntity` is an ordered source: an image is one logical page and a PDF source expands to its rendered page count. A document can therefore contain multiple images, multiple PDFs, or mixed sources without treating the first source as the whole document.

Document bytes remain under `filesDir/documents/<documentId>/` and database paths are accepted only when they resolve below that root. Viewer, OCR and share/export decrypt one source at a time into cache and delete it in `finally`; startup recovery removes stale temp files.

## Persistence and migrations

The current Room version is 5:

- `1→2`: preserved the already-compatible V1 schema.
- `2→3`: adds manual metadata state, page source metadata, foreign keys, cascades, indexes and the FTS search table/triggers.
- `3→4`: rebuilds the FTS table with the Unicode61 tokenizer and normalized Greek search values; it does not modify document or OCR rows.
- `4→5`: adds a per-field `expiryDateManuallyEdited` flag so OCR can clear old automatic expiry values without overwriting a user correction.

Restore parses and validates all parent/child IDs before a transaction. Files are staged first; a durable restore journal supports startup recovery if the filesystem swap and Room transaction are interrupted.

## Background work

OCR is unique per document and serialized in-process to bound Tesseract memory. It validates the bundled model size and SHA-256 before installation, decrypts one source at a time, renders PDF pages, applies EXIF rotation and bounded preprocessing, then commits page OCR and document OCR together. Metadata extraction is conservative: unlabeled dates do not become issue or expiry dates, and only the exact expiry value committed to Room is passed to reminder scheduling. It preserves manual metadata, exposes failure text, and requeues interrupted work after startup. Reminder work uses unique IDs, tags, cancellation on delete/edit and a complete reschedule pass after restore/startup.
