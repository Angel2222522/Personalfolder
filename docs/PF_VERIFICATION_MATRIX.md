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
| PF-005 | Fixed | `FolderApp`, `FolderViewModel` | filter/state-flow source audit; targeted UI wiring review | Run #89: pass | No full accessibility/UI snapshot suite. |
| PF-006 | Fixed | `MetadataExtractor.protocolRegex` | `MetadataExtractorTest` label/false-positive variants | Run #89: pass | OCR with severely damaged labels may remain unrecognized and needs manual entry. |
| PF-007 | Fixed | `MetadataApplicationPolicy`, `DocumentProcessor`, `ReminderScheduler` | `MetadataApplicationPolicyTest`, `MetadataConfidenceTest` | Run #89: pass | Reminder delivery still depends on OS scheduling and notification permission. |
| PF-008 | Fixed | per-field ownership and confirmation UI/repository | `MetadataOwnershipPolicyTest`, `MetadataApplicationPolicyTest` | Run #89: pass | UI confirmation is not covered by a full Compose interaction test. |
| PF-009 | Fixed | `DocumentProcessor`, `MetadataApplicationPolicy` | `MetadataApplicationPolicyTest` | Run #89: pass | Actual Tesseract recognition remains device/data dependent. |
| PF-010 | Fixed with limitation | `MetadataExtractor.providerScore` | provider hierarchy assertions in `MetadataExtractorTest` | Run #89: pass | Provider names are heuristic OCR candidates; ambiguous authorities remain visibly unconfirmed. |
| PF-011 | Fixed with limitation | `MetadataExtractor.categoryRules`, category UI | category rule assertion in `MetadataExtractorTest` | Run #89: pass | No domain-specific classifier beyond the explicit keyword rules. |
| PF-012 | Fixed | `ReminderWorker`, `ReminderDeliveryPolicy` | `ReminderDeliveryPolicyTest`; worker permission retry path | Run #89: pass | OS permission revocation race is source-defended and not physically toggled in CI. |
| PF-013 | Fixed | `ReminderScheduler` | `ReminderDeliveryPolicyTest` | Run #89: pass | Past reminders are delivered after recovery; no silent discard policy exists. |
| PF-014 | Fixed | `ReminderEntity.deadlineAt`, `ReminderCard` | existing reminder policy test and source/UI audit | Run #89: pass | Date rendering depends on device timezone. |
| PF-015 | Fixed | `PersonalFolderApp`, `TempFileCleaner` | startup/recovery source audit; Part 1 recovery tests | Part 1 run #87: pass | Process-crash interleavings are not all injectable in CI. |
| PF-016 | Fixed | `DataOperationCoordinator`, repository/services | `DataOperationCoordinatorTest` | Part 1 run #87: pass | Coordinator is process-local; multi-process access is outside the app design. |
| PF-017 | Fixed | backup exclusive operation boundary | `DataOperationCoordinatorTest`, `BackupRoundTripTest` | Part 1 run #87: pass | SQLite/filesystem snapshot is serialized, not a kernel-level snapshot. |
| PF-018 | Open product decision | `CaseStatus`, `ReminderScheduler`, case UI | No test intentionally encodes an unapproved semantic | Not claimable as fixed | Current behavior remains unchanged: COMPLETED is not excluded by a new rule and existing case reminders continue until product specifies lifecycle semantics. |
| PF-019 | Fixed | `DocumentViewerScreen`, `DocumentPageDisplayPolicy` | `DocumentPageDisplayPolicyTest` | Part 1 run #87: pass | Full rapid-swipe UI test is not included. |
| PF-020 | Fixed | `DocumentsScreen.hasActiveFilter` | source-level UI logic audit | Run #89: pass | No complete Compose screenshot suite. |
| PF-021 | Fixed | `ImportTypePolicy`, `FolderRepository` decoder validation | `ImportTypePolicyTest`, `RepositoryImportDeleteExportTest` | Run #89: pass | Test provider uses FileProvider; vendor SAF MIME quirks remain device-dependent. |
| PF-022 | Fixed with limitation | `PendingActivityStateStore`, `MainActivity` | `PendingActivityStateStoreTest` | Run #89: pass | State expires after 15 minutes; external incoming URI grants may not survive process death unless the provider grants persistable access. |
| PF-023 | Fixed | `DocumentSelectionPolicy`, `DocumentsScreen` | `DocumentSelectionPolicyTest` | Part 1 run #87: pass | Full interaction test of filter changes is not included. |
| PF-024 | Fixed | per-field columns, migration 3→4, confirmation UI | `MetadataOwnershipPolicyTest`, migration test | Run #89: pass; Part 1 run #87: pass | Existing v3 global flag is conservatively mapped to all fields for compatibility. |
| PF-025 | Fixed | `FolderViewModel` mutation wrappers | ViewModel source audit; repository instrumentation | Run #89: pass | Snackbar rendering itself is not separately UI-tested. |
| PF-026 | Fixed with release blocker | `scripts/prepare-signing.sh`, workflow | `bash -n`; fail-closed signing gate; main run #51 | Run #89 PR gates pass; main #51 blocked | The permanent secrets are empty, so no signed APK or certificate continuity result can be claimed. |
| PF-027 | Fixed | workflow debug artifact step | workflow inspection; debug artifact upload | Run #89: pass, artifact `9159204057` | Artifact availability still depends on GitHub Actions retention. |
| PF-028 | Fixed | workflow emulator step | `connectedDebugAndroidTest` | Run #89: 16/16 pass; Part 1 #87: 11/11 pass | No physical device. |
| PF-029 | Fixed | `FolderRepository.deleteDocument`, `DocumentDeletionRecovery` | `RepositoryImportDeleteExportTest`, Part 1 recovery tests | Run #89: pass | Forced filesystem permission failure is not generated on emulator. |
| PF-030 | Fixed | `FtsRepairPolicy`, `AppDatabase` | `FtsRepairPolicyTest` | Run #89: pass | SQLite corruption beyond mirror-row mismatch is outside this policy. |
| PF-031 | Fixed | `docs/TESTING_REPORT.md`, this matrix | final CI run recorded below | Run #89: pass | The final source/runtime record is complete; release signing remains separately blocked. |
| PF-032 | Fixed with limitation | `MainActivity`, `ExportService`, `TempFileCleaner` | `PdfOriginalPreservationTest`; share lifecycle source audit | Run #89: pass; Part 1 #87: pass | Android does not provide a reliable “consumer finished” callback; share files are retained for bounded cleanup (7 days), not deleted on a fixed 15-minute timer. |

## Final verification record

Final Part 2 CI: run **#89**, id `31641238727`, commit
`8fdca9a9b8f3341472769b3e40f359111ffbf92f`; emulator **16/16**, 0 failures,
0 errors, 0 skipped. Artifacts: debug APK `9159204057`, instrumentation
diagnostics `9159204738`, Room schemas `9159263536`.

The PR debug APK is not an update APK. Source continuity is
`com.angel.personalfolder`, versionCode 3 / versionName 2.0.1, Room version 4
with explicit non-destructive migrations. Permanent certificate continuity is
not verified: main run #51 (`31593963638`) failed closed because all four
signing secrets were empty, and no signed release/tag exists.

## Explicit PF-018 decision request

The current source deliberately does not invent a rule for whether
`CaseStatus.COMPLETED` should stop reminders, hide the case from “active”, or
retain the deadline as history. A product owner must choose those semantics.
Until then, the existing behavior is preserved and disclosed in the matrix.
