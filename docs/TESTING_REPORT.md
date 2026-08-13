# V2.0.1 testing report

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, rejection of registry/application false positives, original-text date context, confidence/provenance for labeled and fallback dates, provider hierarchy, category rules, empty input and JSON escaping.
- `RestoreRecoveryPolicyTest`: proves a `prepared` journal cannot delete the only live root and that committed/failed generations choose finalize/rollback safely.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3→4, verifies documents/pages/cases/relations/checklist/timeline/reminders, per-field defaults, deadline migration, linked-document index and cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, and corrupted-backup rejection.
- `PdfBitmapRendererTest` (instrumentation): renders a representative PDF and checks that the background remains bright, dark content remains dark and the output is opaque.
- `PdfOriginalPreservationTest` (instrumentation): verifies that single-source PDF open/share returns the original bytes and that a two-page PDF renders the two pages in order.
- `MetadataApplicationPolicyTest`: verifies extracted title application, low-confidence expiry suggestions and preservation of manually owned provider metadata.
- `FtsRepairPolicyTest` and `ImportTypePolicyTest`: verify full FTS mirror mismatch repair and conservative MIME fallback/rejection.
- `RepositoryImportDeleteExportTest` (instrumentation): verifies invalid-image rejection before Room commit, ZIP manifest/source bytes, private-tree deletion and confirmed-expiry reminder creation/removal.
- `PendingActivityStateStoreTest`: verifies encrypted picker-list persistence and one-time consumption across the activity boundary.
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

Workflow run **#89** (`31641238727`) on remediation commit
`8fdca9a9b8f3341472769b3e40f359111ffbf92f` passed unit tests, lint,
instrumentation compilation, debug APK build, emulator instrumentation,
pull-request release compilation and Room schema upload.

The emulator XML report records **16 tests with 0 failures, 0 errors and 0
skipped**: 2 migration tests, 4 backup/restore tests, 2 PDF bitmap tests, 2
original-PDF/multi-page tests, 4 repository import/delete/export/reminder
tests and 2 encrypted picker-state tests. This is the actual remote runtime
result; local source inspection or APK compilation alone is not treated as
runtime verification.

The earlier run #87 (`31636678409`) remains the Part 1 closure run with 11/11
tests. Run #86 was red only because the emulator runner's multiline shell
handling discarded the captured exit status after a successful 11/11 run.

## Release continuity result

The release source still uses application id `com.angel.personalfolder`,
versionCode 3 and versionName 2.0.1, with explicit Room migrations through
version 4 and no destructive migration. Pull-request run #89 correctly skips
signing. Main run #51 (`31593963638`) failed closed at signing preparation
because the four permanent signing secrets were empty. There is no signed
release APK, tag or GitHub release available to verify in-place update
continuity, so no APK is claimed as a final update artifact.
