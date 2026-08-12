# V2.0.1 testing report

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, rejection of registry/application false positives, original-text date context, confidence/provenance for labeled and fallback dates, provider hierarchy, category rules, empty input and JSON escaping.
- `RestoreRecoveryPolicyTest`: proves a `prepared` journal cannot delete the only live root and that committed/failed generations choose finalize/rollback safely.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3→4, verifies documents/pages/cases/relations/checklist/timeline/reminders, per-field defaults, deadline migration, linked-document index and cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, and corrupted-backup rejection.
- `PdfBitmapRendererTest` (instrumentation): renders a representative PDF and checks that the background remains bright, dark content remains dark and the output is opaque.
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

The remediation branch has been published for a fresh CI run. This section is updated only from the actual workflow result; local source inspection or APK compilation is not treated as runtime verification.
