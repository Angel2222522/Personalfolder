# V2 testing report

## Checks that were executed

- Host Tesseract was run against the exact bundled `ell+eng` assets. Both assets passed `combine_tessdata -d` and the pinned SHA-256/size checks.
- Controlled fixtures were OCR'd: Greek image, Greek/English image, low-quality image and a two-page PDF rendered to bounded pages.
- The attached example PDF was used only in the local workspace. Its host OCR contained the specific ministry, protocol label/number and registry label/number, while no explicit expiry marker was found. The PDF and OCR output were not added to Git or GitHub.
- The SQLite FTS trigger/normalization rule was exercised with uppercase Greek, tonos and accentless prefix queries.
- `git diff --check` completed without whitespace errors.
- GitHub Actions run [31590944545](https://github.com/Angel2222522/Personalfolder/actions/runs/31590944545), commit `82b71cf7f1158dfffda5870ecdbd730188e0ce7f`, passed unit tests, lint, instrumentation-test compilation, debug APK assembly and release APK assembly.

## Test coverage

The CI JVM test task executed the metadata extractor/merge tests, including explicit expiry labels, unrelated/rejection/decision dates, composite date ranges, specific provider selection, protocol OCR variants, conservative null behavior and legacy/manual expiry handling. It also executed the existing pure JVM coverage.

The CI compiled, but did not execute, Android instrumentation tests:

- `AppDatabaseMigrationTest`: V1→V5 schema and the new per-field expiry-manual flag.
- `OcrWorkerIntegrationTest`: encrypted image/PDF OCR, page/document persistence, normalized FTS and failure state.
- `MetadataPersistenceTest`: the safe expiry value is the value persisted in `DocumentEntity`.
- Existing backup, file-format, search and restore coverage.

## APK artifact audit

- Release verification artifact: `app-release-unsigned.apk`.
- Its `ell.traineddata` is 1,419,514 bytes with SHA-256 `4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726`.
- Its `eng.traineddata` is 4,113,088 bytes with SHA-256 `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`.
- The release artifact is unsigned because the repository does not contain the private release key. An install/update requires signing with the same key as the existing installation.

## Checks intentionally not executed

No emulator, physical device, `adb` installation, camera test, biometric hardware test or end-to-end Android runtime test was performed. The workflow has no emulator step, as requested.
