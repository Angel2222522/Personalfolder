# Independent batch import / OCR queue regression notes — 2026-08-13

## Scope

This note records only general engineering rules learned while validating multi-file import behavior. It contains no uploaded documents, names, identifiers, dates, addresses, OCR transcripts, or other user data.

## Rules

### 1. Multi-selection means independent documents

A system picker or share-sheet selection containing multiple independent files must create one logical `DocumentEntity` per source. Multi-page scanner captures remain one document because the scanner session explicitly represents pages of the same document.

### 2. Failure isolation

One invalid or unreadable source must not cancel the remaining files in the same batch. Import admission is sequential and records successes/failures independently.

### 3. Heavy OCR is serialized

Only one worker may enter the heavyweight OCR section at a time. Waiting jobs must not render large bitmaps or load Tesseract models before they acquire the OCR execution gate. The gate must always be released after success or failure.

### 4. Do not hold the global library lock during OCR

The process-wide library coordinator remains appropriate for short database/filesystem snapshots and mutations, but it must not be held while PDF rendering or Tesseract recognition runs. Long OCR under the global lock can block unrelated imports and make the application appear frozen.

### 5. Resource cleanup

PDF pages are processed one at a time. Rendered bitmaps are recycled in `finally` blocks and decrypted temporary OCR files are deleted when processing completes or fails.

### 6. No automatic APK during the evaluation loop

The no-emulator verification workflow runs unit tests, lint and compilation checks, but does not package/upload an application APK during incremental evaluation. APK packaging is reserved for an explicit release checkpoint.

## Regression coverage

- `IndependentImportBatchRunnerTest`: at least ten synthetic independent PDF names, one injected failure, all remaining sources still attempted, unique successes, sequential admission.
- `OcrExecutionGateTest`: ten queued synthetic OCR jobs, maximum heavyweight concurrency of one, injected failure releases the gate and does not prevent the other jobs from completing.

## Invariants

- Never persist real evaluation PDFs or data derived from them in the repository or CI artifacts.
- A multi-file picker selection must never silently become one document.
- Scanner page grouping must stay explicit and limited to the scanner session.
- OCR state remains per document: queued, processing, processed or failed.
- A failed document must not poison the queue.
- Keep unrelated viewer, encryption, backup, reminder, signing and visual behavior unchanged unless a concrete regression requires otherwise.
