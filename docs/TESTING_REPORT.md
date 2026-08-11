# V2 testing report

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, original-text date context, confidence for labeled and fallback dates, empty input and JSON escaping.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3, verifies documents/pages/cases/relations/checklist/timeline/reminders, and verifies cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, and corrupted-backup rejection.
- CI compiles the instrumentation source and runs unit tests, lint, debug build, debug androidTest build and release build verification.

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
