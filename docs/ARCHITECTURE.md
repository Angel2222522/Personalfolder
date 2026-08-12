# V2.0.1 architecture

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

Document bytes remain under `filesDir/documents/<documentId>/` and database paths are accepted only when they resolve below that root. Viewer, OCR and share/export decrypt one source at a time into cache and delete it in `finally`; startup recovery removes stale disposable temp files. Restore and document deletion use durable journals; their recovery trees are never removed by a time-only cache cleaner.

PDF viewer and OCR render through separate bitmap instances. `PdfBitmapRenderer` creates an ARGB buffer, renders with `PdfRenderer`, and composites onto an opaque white surface so transparent PDF pixels cannot become a black page. OCR preprocessing never mutates the viewer bitmap or encrypted source.

All database/filesystem mutations, plus backup snapshots and OCR persistence, pass through `DataOperationCoordinator`. This provides one process-wide generation boundary around Room and the encrypted document tree.

## Persistence and migrations

The current Room version is 4:

- `1→2`: preserved the already-compatible V1 schema.
- `2→3`: adds manual metadata state, page source metadata, foreign keys, cascades, indexes and the FTS search table/triggers. The migration also creates the checklist linked-document index.
- `3→4`: adds per-field confidence/provenance storage, per-field manual ownership, expiry suggestions and `ReminderEntity.deadlineAt`; old global manual edits are migrated conservatively to all fields, while old automatic values become non-authoritative.

Restore parses and validates all parent/child IDs before a transaction. Files are staged first; a durable restore journal and explicit recovery policy support startup recovery if the filesystem swap and Room transaction are interrupted. A prepared journal never authorizes deletion of the only live root.

## Background work

OCR is unique per document and serialized in-process to bound Tesseract memory. It preserves manual metadata and treats cancellation as cancellation. Reminder work uses unique IDs, tags, cancellation on delete/edit and a complete reschedule pass after restore/startup. Past triggers are scheduled immediately, and missing notification permission causes retry rather than permanent success.
