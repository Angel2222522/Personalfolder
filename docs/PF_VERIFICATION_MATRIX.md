# Personal Folder remediation matrix — PF-001 to PF-032

This is the Part 2 evidence matrix for branch `codex/personal-folder-remediation`.
“Fixed” means the current source has a targeted regression test or an existing
runtime test. “Open product decision” is intentionally not treated as fixed.

| PF | Κατάσταση | Σχετικός κώδικας | Regression test | CI αποτέλεσμα | Περιορισμός |
|---|---|---|---|---|---|
| PF-001 | Fixed | `BackupService`, `RestoreRecoveryPolicy` | `RestoreRecoveryPolicyTest`, `BackupRoundTripTest` | Part 1 run #87: pass | Fault injection of power loss is policy-level, not physical-device tested. |
| PF-002 | Fixed | `AppDatabase` migrations, linked-document index | `AppDatabaseMigrationTest` | Part 1 run #87: pass | Legacy orphan rejection is intentional and blocks migration for ambiguous data. |
| PF-003 | Fixed | `BackupService`, restore journal | `RestoreRecoveryPolicyTest` | Part 1 run #87: pass | No forced process kill during live restore in emulator. |
| PF-004 | Fixed | `BackupSizePolicy`, `BackupService` | `BackupSizePolicyTest`, `BackupRoundTripTest` | Part 1 run #87: pass | Maximum-size boundary is policy-tested; multi-gigabyte I/O is not exercised in CI. |
| PF-005 | Fixed | `FolderApp`, `FolderViewModel` | filter/state-flow source audit; targeted UI wiring review | Part 2 final CI: pending | No full accessibility/UI snapshot suite. |
| PF-006 | Fixed | `MetadataExtractor.protocolRegex` | `MetadataExtractorTest` label/false-positive variants | Part 2 final CI: pending | OCR with severely damaged labels may remain unrecognized and needs manual entry. |
| PF-007 | Fixed | `MetadataApplicationPolicy`, `DocumentProcessor`, `ReminderScheduler` | `MetadataApplicationPolicyTest`, `MetadataConfidenceTest` | Part 2 final CI: pending | Reminder delivery still depends on OS scheduling and notification permission. |
| PF-008 | Fixed | per-field ownership and confirmation UI/repository | `MetadataOwnershipPolicyTest`, `MetadataApplicationPolicyTest` | Part 2 final CI: pending | UI confirmation is not covered by a full Compose interaction test. |
| PF-009 | Fixed | `DocumentProcessor`, `MetadataApplicationPolicy` | `MetadataApplicationPolicyTest` | Part 2 final CI: pending | Actual Tesseract recognition remains device/data dependent. |
| PF-010 | Fixed with limitation | `MetadataExtractor.providerScore` | provider hierarchy assertions in `MetadataExtractorTest` | Part 2 final CI: pending | Provider names are heuristic OCR candidates; ambiguous authorities remain visibly unconfirmed. |
| PF-011 | Fixed with limitation | `MetadataExtractor.categoryRules`, category UI | category rule assertion in `MetadataExtractorTest` | Part 2 final CI: pending | No domain-specific classifier beyond the explicit keyword rules. |
| PF-012 | Fixed | `ReminderWorker`, `ReminderDeliveryPolicy` | `ReminderDeliveryPolicyTest`; worker permission retry path | Part 2 final CI: pending | OS permission revocation race is source-defended and not physically toggled in CI. |
| PF-013 | Fixed | `ReminderScheduler` | `ReminderDeliveryPolicyTest` | Part 2 final CI: pending | Past reminders are delivered after recovery; no silent discard policy exists. |
| PF-014 | Fixed | `ReminderEntity.deadlineAt`, `ReminderCard` | existing reminder policy test and source/UI audit | Part 2 final CI: pending | Date rendering depends on device timezone. |
| PF-015 | Fixed | `PersonalFolderApp`, `TempFileCleaner` | startup/recovery source audit; Part 1 recovery tests | Part 1 run #87: pass | Process-crash interleavings are not all injectable in CI. |
| PF-016 | Fixed | `DataOperationCoordinator`, repository/services | `DataOperationCoordinatorTest` | Part 1 run #87: pass | Coordinator is process-local; multi-process access is outside the app design. |
| PF-017 | Fixed | backup exclusive operation boundary | `DataOperationCoordinatorTest`, `BackupRoundTripTest` | Part 1 run #87: pass | SQLite/filesystem snapshot is serialized, not a kernel-level snapshot. |
| PF-018 | Open product decision | `CaseStatus`, `ReminderScheduler`, case UI | No test intentionally encodes an unapproved semantic | Not claimable as fixed | Current behavior remains unchanged: COMPLETED is not excluded by a new rule and existing case reminders continue until product specifies lifecycle semantics. |
| PF-019 | Fixed | `DocumentViewerScreen`, `DocumentPageDisplayPolicy` | `DocumentPageDisplayPolicyTest` | Part 1 run #87: pass | Full rapid-swipe UI test is not included. |
| PF-020 | Fixed | `DocumentsScreen.hasActiveFilter` | source-level UI logic audit | Part 2 final CI: pending | No complete Compose screenshot suite. |
| PF-021 | Fixed | `ImportTypePolicy`, `FolderRepository` decoder validation | `ImportTypePolicyTest`, `RepositoryImportDeleteExportTest` | Part 2 final CI: pending | Test provider uses FileProvider; vendor SAF MIME quirks remain device-dependent. |
| PF-022 | Fixed | `PendingActivityStateStore`, `MainActivity` | `PendingActivityStateStoreTest` | Part 1 run #87: pass; Part 2 final CI: pending | State expires after 15 minutes and requires the same app Keystore. |
| PF-023 | Fixed | `DocumentSelectionPolicy`, `DocumentsScreen` | `DocumentSelectionPolicyTest` | Part 1 run #87: pass | Full interaction test of filter changes is not included. |
| PF-024 | Fixed | per-field columns, migration 3→4, confirmation UI | `MetadataOwnershipPolicyTest`, migration test | Part 1 run #87: pass; Part 2 final CI: pending | Existing v3 global flag is conservatively mapped to all fields for compatibility. |
| PF-025 | Fixed | `FolderViewModel` mutation wrappers | ViewModel source audit; repository instrumentation | Part 2 final CI: pending | Snackbar rendering itself is not separately UI-tested. |
| PF-026 | Fixed | `scripts/prepare-signing.sh`, workflow | `bash -n`; workflow signature verification | Part 2 final CI: pending | Secret-backed signed path only runs on `main`, not pull requests. |
| PF-027 | Fixed | workflow debug artifact step | workflow inspection; debug artifact upload | Part 2 final CI: pending | Artifact availability still depends on GitHub Actions retention. |
| PF-028 | Fixed | workflow emulator step | `connectedDebugAndroidTest` | Part 1 run #87: 11/11 pass; Part 2 final CI: pending | No physical device. |
| PF-029 | Fixed | `FolderRepository.deleteDocument`, `DocumentDeletionRecovery` | `RepositoryImportDeleteExportTest`, Part 1 recovery tests | Part 2 final CI: pending | Forced filesystem permission failure is not generated on emulator. |
| PF-030 | Fixed | `FtsRepairPolicy`, `AppDatabase` | `FtsRepairPolicyTest` | Part 2 final CI: pending | SQLite corruption beyond mirror-row mismatch is outside this policy. |
| PF-031 | Fixed | `docs/TESTING_REPORT.md`, this matrix | final CI run recorded below | Part 2 final CI: pending | This row is complete only after the final run IDs are written. |
| PF-032 | Fixed with limitation | `MainActivity`, `ExportService`, `TempFileCleaner` | `PdfOriginalPreservationTest`; share lifecycle source audit | Part 1 run #87: pass; Part 2 final CI: pending | Android does not provide a reliable “consumer finished” callback; share files are retained for bounded cleanup (7 days), not deleted on a fixed 15-minute timer. |

## Final verification record

The final Part 2 CI run, emulator test count, artifact IDs and any signed
release run must be written here after the last code/documentation commit. A
pull-request debug APK is not an update APK: release identity is only accepted
after `applicationId`, monotonic `versionCode`, migration path and the
permanent certificate fingerprint are verified on `main`.

## Explicit PF-018 decision request

The current source deliberately does not invent a rule for whether
`CaseStatus.COMPLETED` should stop reminders, hide the case from “active”, or
retain the deadline as history. A product owner must choose those semantics.
Until then, the existing behavior is preserved and disclosed in the matrix.
