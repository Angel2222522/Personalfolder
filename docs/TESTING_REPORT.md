# V2.0.1 testing report

> This report now records the current `codex/personal-folder-audit-remediation`
> evidence. Older V2 reports and run #89 references are historical; the
> immutable `CODE_AUDIT_REPORT.md` is not modified.

## Automated coverage added

- `MetadataExtractorTest`: Greek/English-compatible protocol/date parsing, rejection of registry/application false positives, original-text date context, confidence/provenance for labeled and fallback dates, provider hierarchy, category rules, empty input and JSON escaping.
- `RestoreRecoveryPolicyTest`: proves a `prepared` journal cannot delete the only live root and that committed/failed generations choose finalize/rollback safely.
- `AppDatabaseMigrationTest` (instrumentation): constructs a V1 schema, migrates through 1→2→3→4, verifies documents/pages/cases/relations/checklist/timeline/reminders, per-field defaults, deadline migration, linked-document index and cascade/SET NULL behavior.
- `BackupRoundTripTest` (instrumentation): AES-GCM password round-trip, wrong-password rejection, portable backup restore preserving page bytes/OCR, corrupted-backup rejection, and a confirmed far-future reminder round-trip.
- `PdfBitmapRendererTest` (instrumentation): renders a representative PDF and checks that the background remains bright, dark content remains dark and the output is opaque.
- `PdfOriginalPreservationTest` (instrumentation): verifies that single-source PDF open/share returns the original bytes and that a two-page PDF renders the two pages in order.
- `MetadataApplicationPolicyTest`: verifies extracted title application, low-confidence expiry suggestions and preservation of manually owned provider metadata.
- `FtsRepairPolicyTest` and `ImportTypePolicyTest`: verify full FTS mirror mismatch repair and conservative MIME fallback/rejection.
- `RepositoryImportDeleteExportTest` (instrumentation): verifies invalid-image rejection before Room commit, ZIP manifest/source bytes for every source, private-tree deletion, malformed deletion journal fail-closed behavior, and confirmed-expiry reminder creation/removal.
- `TempFileCleanupPolicyTest`: verifies stale temporary-file cleanup with old, recent, unknown and future timestamps without deleting files because of clock skew.
- `AppDatabaseMigrationTest` also exercises document, page, case, relation, event, checklist and reminder orphan references; migration refuses inconsistent state instead of silently dropping rows.
- `PendingActivityStateStoreTest`: verifies encrypted picker-list persistence and one-time consumption across the activity boundary.
- CI runs unit tests, lint, debug build, instrumentation compilation, `connectedDebugAndroidTest` on an Android emulator, unsigned release compilation, wrapper/provenance checks and Room schema upload. Release signing remains release-blocked until the permanent signing bundle is available.
- Standard baseline security scan: 9 source-backed V1 findings (7 medium, 2 low), with remediation status reviewed against the V2 source. It is explicitly a pre-V2 scan, not a claim that the final commit was independently rescanned.

## Commands

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew assembleRelease
```

## Not claimed as verified without a device/emulator

Camera permission and multi-page capture, SAF providers from several document apps, biometric/device credential callbacks, Android 16 edge-to-edge/OEM rendering, large/malformed PDFs, reboot/timezone reminder delivery, and the complete UI accessibility tree.

The presence of a passing compile or lint run is not treated as proof of those flows. The final handoff should record the CI run and separately identify any physical-device scenarios not executed.

## Latest remote verification

Workflow run **#106** (`31716441666`) on remediation commit
`39071cfd063ef231d1f15a23293bcd730f8da7dd` passed unit tests, lint,
instrumentation compilation, debug APK build, emulator instrumentation,
unsigned release compilation and Room schema upload. Job `verify` was
`94502252089`.

The emulator log records **22 tests with 0 failures and 0 ignored**. This is
the actual remote runtime result; local source inspection or APK compilation
alone is not treated as runtime verification. The run also produced the debug
artifact ZIP (ID `9187858558`, SHA-256
`c0f219cf5ad23f71aebcf70c75e4617d514579d060cadf58d09e8e54d67eb7e8`),
instrumentation diagnostics (ID `9187859334`) and Room schemas (ID
`9187945308`).

Runs #89, #102 and #106 are retained as historical/current CI evidence for
their respective heads. Runs #103 and #104 are recorded as non-authoritative
failures: #103 had emulator/ADB infrastructure errors, while #104 exposed the
incorrect far-future test fixture that was corrected in `696be0e`.

## Release continuity result

The release source still uses application id `com.angel.personalfolder`,
versionCode 3 and versionName 2.0.1, with explicit Room migrations through
version 4 and no destructive migration. Run #106 correctly skips signing.
The permanent signing bundle is unavailable, so there is no signed release
APK, certificate validation or in-place update continuity proof. The debug
artifact is deliberately not presented as a final update artifact.
