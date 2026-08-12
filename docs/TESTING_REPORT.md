# V2 testing report

## What was actually executed

- Host Tesseract was run against the exact bundled `ell+eng` assets. Both assets passed `combine_tessdata -d` and SHA-256/size checks.
- Controlled fixtures were OCR'd: Greek image, Greek/English image, low-quality image, and a two-page PDF rendered to bounded 2400-pixel pages.
- The SQLite FTS trigger/normalization rule was exercised with uppercase Greek, tonos and accentless prefix queries.
- `git diff --check` completed without whitespace errors.

The host checks do not replace Android execution. The current checkout has no Gradle wrapper, system Gradle, Android SDK, `adb`, emulator or attached device, so the local Android build and instrumentation tests could not be run here.

## Android tests added but not executed in this environment

- `AppDatabaseMigrationTest`: V1→V4 relations, cascade/SET NULL behavior and the `linkedDocumentId` index.
- `OcrWorkerIntegrationTest`: encrypted Greek image, multi-page PDF rendering, persisted page/document OCR, normalized FTS search and unreadable-input failure state.
- `DocumentFileFormatTest` and `SearchTextTest`: pure JVM checks for PDF signature detection and Greek normalization.
- Existing `BackupRoundTripTest`: portable backup, wrong password and corrupted archive behavior.

## External CI artifact audit

The latest available remote debug artifact (GitHub Actions run `31524486919`, source commit `55cd08109ecb9898a1808308355030be291861b3`) was inspected before modifying this checkout. Its APK contained `ell.traineddata` and `eng.traineddata` at exactly 786,444 bytes; `combine_tessdata -d` could not read the model components. The APK therefore could install while its OCR initialization failed. That artifact is not a build of the uncommitted local fixes.

The remote workflow compiled instrumentation sources but did not execute them. No physical installed APK was available for inspection, so the installed APK on a user's device cannot be identified from this checkout.

## Required final verification on an Android environment

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
```

Then install the newly produced debug APK and exercise clean Greek image, scanned Greek PDF, embedded-text PDF, mixed-language, multi-page, EXIF-rotated and low-quality inputs, including cold start, process stop, cancellation, retry and low-memory conditions. Record `adb logcat` and the final Room/Worker states for each case.
