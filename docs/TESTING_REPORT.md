# V2 testing report

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, original-text date context, confidence for labeled and fallback dates, empty input and JSON escaping.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3→4, verifies documents/pages/cases/relations/checklist/timeline/reminders, and verifies the linked-document index plus cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, and corrupted-backup rejection.
- `DocumentSourceClassifierTest`: keeps PDF classification rules consistent for MIME-only, filename-only and image sources.
- `LibraryOperationCoordinatorTest`: verifies that filesystem/database replacement operations are serialized rather than overlapping.
- CI compiles the instrumentation source and runs unit tests, lint, debug build, debug androidTest build and release build verification.
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

PR run 57 (`31600158508`) completed successfully for commit `fe617e5f7019159b05b91648b5efcf83db7e4276`. Unit tests, lint, instrumentation-test compilation, debug APK build, unsigned release compilation and Room schema publication passed. Permanent signing steps were skipped because this was a pull-request run; they are only executed on a push to `main` with the permanent signing secrets.

The local workspace did not contain a Gradle wrapper and did not have a usable Gradle installation, so the commands above were verified through GitHub Actions rather than claimed as locally executed.
