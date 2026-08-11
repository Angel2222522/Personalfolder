# V2 testing report

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, original-text date context, confidence for labeled and fallback dates, empty input and JSON escaping.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3, verifies documents/pages/cases/relations/checklist/timeline/reminders, and verifies cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, and corrupted-backup rejection.
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

Run 27 (`31522474942`) passed unit tests, lint, instrumentation compilation, debug APK build, release build verification and artifact publication for commit `24af72269e2c5588fa63d3ce247a73080a15a830`. A later hardening/docs commit requires the final CI run to be recorded before release handoff.
