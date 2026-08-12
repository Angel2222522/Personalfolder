# V2.0.1 testing report

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, rejection of registry/application false positives, original-text date context, confidence/provenance for labeled and fallback dates, provider hierarchy, category rules, empty input and JSON escaping.
- `RestoreRecoveryPolicyTest`: proves a `prepared` journal cannot delete the only live root and that committed/failed generations choose finalize/rollback safely.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3→4, verifies documents/pages/cases/relations/checklist/timeline/reminders, per-field defaults, deadline migration, linked-document index and cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, and corrupted-backup rejection.
- `PdfBitmapRendererTest` (instrumentation): renders a representative PDF and checks that the background remains bright, dark content remains dark and the output is opaque.
- `PdfOriginalPreservationTest` (instrumentation): verifies that single-source PDF open/share returns the original bytes and that a two-page PDF renders the two pages in order.
- CI runs unit tests, lint, debug build, instrumentation compilation and `connectedDebugAndroidTest` on an Android emulator. Release signing remains release-blocked until the permanent signing bundle is available.
- Standard baseline security scan: 9 source-backed V1 findings (7 medium, 2 low), with remediation status reviewed against the V2 source. It is explicitly a pre-V2 scan, not a claim that the final commit was independently rescanned.

## Commands

```bash
gradle testDebugUnitTest
gradle lintDebug
gradle assembleDebug
gradle assembleDebugAndroidTest
gradle assembleRelease
```

## Not claimed as verified without a device/emulator

Camera permission and multi-page capture, SAF providers from several document apps, biometric/device credential callbacks, Android 16 edge-to-edge/OEM rendering, large/malformed PDFs, actual Greek/English Tesseract recognition, reboot/timezone reminder delivery, and the complete UI accessibility tree.

The presence of a passing compile or lint run is not treated as proof of those flows. The final handoff should record the CI run and separately identify any physical-device scenarios not executed.

## Latest remote verification

Workflow run **#87** (`31636678409`) on remediation commit `840275e956a250305d50d4075fe9f730a7682cbe` passed. The job passed unit tests, lint, instrumentation compilation, debug APK build, emulator instrumentation, pull-request release compilation and Room schema upload.

The emulator XML report records 11 tests with 0 failures, 0 errors and 0 skipped: 2 migration tests, 4 backup/restore tests, 2 PDF bitmap tests, 2 original-PDF/multi-page tests and 1 Android Keystore state test. Run #86 was red only because the emulator runner's multiline shell handling discarded the captured Gradle exit status after a successful 11/11 test run; the workflow was corrected and #87 is green. This is the actual remote runtime result; local source inspection or APK compilation alone is not treated as runtime verification.
