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

## Verification status at handoff

Local machine limitations: this workspace does not contain the Android SDK, Gradle wrapper or Kotlin compiler, so Android verification is performed through the repository GitHub Actions workflow. The CI workflow has been changed to run unit tests, lint, debug build, instrumentation compilation, emulator instrumentation tests, and PR release compilation. Permanent signing and signed release artifact verification are intentionally restricted to a push on `main` with the existing four signing secrets.

The first fresh CI run reached unit-test execution after the Kotlin compiler fixes. It exposed and led to a correction in the expiry confidence rule: explicit `Ισχύει έως` is high-confidence contextual evidence, while an unlabelled last date remains low-confidence. A newer CI run must be checked before Part 1 is declared closed.

## Part 2 work still required

Continue from this file and the latest remediation branch; do not restart the audit. Part 2 must:

1. inspect the latest CI result and fix only evidence-backed failures;
2. complete the current-code verification matrix for PF-001 through PF-032;
3. add the separate `PDF-DARK-RENDER / OCR INPUT CORRUPTION` audit entry with its technical cause and test evidence;
4. finish any remaining lifecycle, reminder, UI selection, import, backup/restore and OCR edge-case tests;
5. resolve or explicitly record the product decision required by PF-018, without inventing semantics;
6. run the complete CI/emulator verification and perform a fresh end-to-end source/runtime audit;
7. verify release identity, monotonic version code, migration path and permanent signing on `main` before producing an APK;
8. add the final report with one honest status for every PF item and the APK/build evidence.

The current branch is intentionally not presented as a finished release until those Part 2 checks are complete.
