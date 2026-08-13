# Personal Folder remediation handoff — 2026-08-12

This file is the continuation checkpoint for the two-part remediation requested for the Personal Folder Android application.

## Scope of Part 1

Part 1 covers the shared integrity foundations and the complete PDF rendering/OCR-input path:

- explicit Room/database and encrypted-filesystem operation boundary;
- safe Room migrations through schema version 4;
- restore journal decisions that preserve the only known valid generation;
- safe first restore on an empty installation, including an empty rollback generation;
- backup size contract and portable backup round-trip handling;
- recoverable document deletion and startup cleanup boundaries;
- faithful PDF rendering onto opaque white ARGB bitmaps;
- independent viewer and OCR bitmap instances;
- original-byte external open/share for a single-source imported PDF;
- multi-page PDF page ordering and page-indicator/display consistency;
- protocol/provider/expiry confidence foundations used by the OCR path.

## What has been changed

The original encrypted document bytes remain under `filesDir/documents/<document-id>/`. The internal viewer decrypts a page only into a temporary file and renders that page. OCR creates another render bitmap and never mutates the viewer bitmap or the encrypted source. A single-source PDF opened/shared outside the app is decrypted byte-for-byte to a temporary share file; images, mixed documents and explicit unified-PDF export use a derived PDF because they do not have one original PDF byte stream.

Restore now stages and validates files before the Room replacement transaction. A durable journal records the generation boundary. If a process stops before the database commit and a previous generation is proven, the old generation is restored. If there is no previous generation, the replacement is preserved for evidence-based recovery rather than deleting the only available copy. A fresh empty installation materialises an empty previous generation so a failed first restore can roll back without leaving Room and filesystem generations split.

## Part 1 closure verification

Local machine limitations: this workspace does not contain the Android SDK, Gradle wrapper or Kotlin compiler, so Android verification was performed through the repository GitHub Actions workflow.

The exact passing verification is GitHub Actions workflow run **#87**, run id `31636678409`, on commit `840275e956a250305d50d4075fe9f730a7682cbe` of `codex/personal-folder-remediation`:

- `gradle testDebugUnitTest`: passed;
- `gradle lintDebug`: passed;
- `gradle assembleDebugAndroidTest`: passed;
- `gradle assembleDebug`: passed;
- emulator `connectedDebugAndroidTest`: passed, **11 tests, 0 failures, 0 errors, 0 skipped**;
- `gradle assembleRelease` for the pull request: passed;
- Room schema artifact upload: passed.

The emulator report groups the 11 passing tests as follows:

- Room migration and orphan-data protection: 2/2;
- encrypted backup/restore, password and corruption/size-boundary coverage: 4/4;
- PDF bitmap rendering and opaque white/dark-content behavior: 2/2;
- original single-PDF bytes and multi-page ordering: 2/2;
- Android Keystore picker-boundary state: 1/1.

The preceding run #86 was red for a CI wrapper defect, not an application test failure: its Gradle output was `BUILD SUCCESSFUL` and the device report was 11/11 with 0 failures, but the emulator action executed each multiline script line in a separate shell and lost the saved exit status. Commit `840275e956a250305d50d4075fe9f730a7682cbe` keeps status capture, diagnostics and the final exit in one shell. Run #87 then passed the complete job without bypassing instrumentation tests.

The PDF end-to-end path is now covered in source and regression tests: imported PDF bytes are encrypted and retained; PDF pages are decoded through `PdfRenderer` and image pages through `BitmapFactory`; the viewer receives an opaque white-background bitmap without OCR filters; OCR renders its own independent bitmap and sends that to Tesseract; original PDF bytes are used for external open/share of a single PDF; and multi-page PDF ordering is preserved. Images and PDFs therefore do not share an unsafe decoder path.

### Γιατί σκοτείνιαζε το PDF

Το PDF δεν αλλοιωνόταν μέσα στην αποθήκευση. Το πρόβλημα ήταν η bitmap που δινόταν στο `PdfRenderer`: ξεκινούσε χωρίς ρητά ορισμένο λευκό, αδιαφανές φόντο. Σε σελίδες με λευκό υπόβαθρο, τα διαφανή/μη αρχικοποιημένα pixels μπορούσαν να εμφανιστούν μαύρα στον viewer ή να δώσουν κακή είσοδο στο OCR. Η διόρθωση γεμίζει πρώτα bitmap ARGB με λευκό, κάνει το render και παράγει αδιαφανές αντίγραφο. Ο viewer και το OCR χρησιμοποιούν χωριστά bitmap, ενώ το αρχικό PDF παραμένει byte-for-byte ίδιο.

Permanent signing and signed release artifact verification remain intentionally restricted to a push on `main` with the existing four signing secrets. They are not required to close Part 1 and are not claimed here.

## Part 2 closure verification

Part 2 implementation and targeted verification were run on the current PR head
`8fdca9a9b8f3341472769b3e40f359111ffbf92f` (`codex/personal-folder-remediation`).
The complete PF-001 to PF-032 matrix is in
[`docs/PF_VERIFICATION_MATRIX.md`](PF_VERIFICATION_MATRIX.md).

The final pull-request Actions run is **#89**, run id `31641238727`:

- unit tests: passed;
- lint: passed;
- instrumentation compilation: passed;
- debug APK: passed;
- emulator instrumentation: **16 tests, 0 failures, 0 errors, 0 skipped**;
- pull-request release compilation: passed;
- Room schema upload: passed.

The 16 emulator tests cover 2 migration tests, 4 backup/restore tests, 2 PDF
bitmap tests, 2 original-PDF/multi-page tests, 4 repository
import/delete/export/reminder tests and 2 encrypted picker-state tests. The
run artifacts are:

- debug APK, artifact `9159204057`, digest
  `sha256:d1595e52ccab97f5756da26927fb774ba99fd4ccddb20fd21666266bcaaa69e5`;
- instrumentation diagnostics, artifact `9159204738`, digest
  `sha256:ecc9a205fdb6b6ea92e67ffbe540c1148f50fe29f10b301486163b5e2280e859`;
- Room schemas, artifact `9159263536`, digest
  `sha256:def0dea8430e6fd818a8954c9395a6b82b6cb311add1dfdc2251b973f3201024`.

The Part 2 changes cover filtered document state, per-field OCR ownership and
confirmation, strict protocol/provider/date/category parsing, reminder
permission and lifecycle behavior, serialized operations, import validation,
encrypted picker state, complete FTS repair, share-file cleanup and the single
signing helper. Import/delete/export bytes and reminder creation/removal are
verified in `RepositoryImportDeleteExportTest`.

PF-018 remains explicitly open as a product decision. The code does not invent
whether `COMPLETED` cases should be hidden from active views or stop retaining
deadline reminders. Current behavior is preserved and recorded in the matrix.

## Release blocker

The PR workflow intentionally skips permanent signing. Release continuity was
checked against the source: release application id is
`com.angel.personalfolder`, current `versionCode` is 3 / `versionName` is
2.0.1, and Room migrations remain explicit through version 4 with no
destructive fallback. The latest `main` signing run is **#51**, id
`31593963638`; it stopped in the signing-preparation step because all four
permanent signing secrets were empty. The repository has no tag, GitHub
release, or signed release artifact from which an update certificate can be
verified.

Therefore no APK is presented as a final or compatible update. The PR debug
APK is a test artifact only; producing the requested final release APK requires
the existing permanent keystore/secrets to be restored and a successful push to
`main` that verifies the recorded certificate fingerprint. PF-018 also needs a
product-owner decision before the remediation can be called fully closed.
