# FINAL LOCK — Personal Folder v2.0.1 OCR Fix — 2026-08-13

This file records the final accepted checkpoint requested by the project owner.

## Final code reference

The final application code used to build the accepted test APK is commit:

`dca6dadb1372ccd4da878250657b79977f62cdef`

Branch at build time:

`codex/personal-folder-rapid-no-emulator`

This exact commit is the canonical source reference for the final accepted OCR/metadata remediation checkpoint. Future work must start from this commit or a verified descendant, never by restarting from `main` or an earlier remediation state.

## Final APK reference

Accepted file name:

`PersonalFolder-v2.0.1-OCRFix-Test-FINAL.apk`

APK SHA-256:

`61a7996aa4f0abfb6a56b85b2ecc433d86db862c2690e3ec74f72e1c05c79b81`

APK size:

`72,625,039 bytes`

The APK was built by GitHub Actions from commit `dca6dadb1372ccd4da878250657b79977f62cdef` using the debug build type, which intentionally installs as the separate `.ocrfix` application for private validation.

## Final-state rule

Treat the code checkpoint and APK hash above as the final accepted baseline. Do not overwrite, reinterpret, or replace this baseline when continuing later work. Any future change is a new version/checkpoint and must preserve a clear lineage back to this final state.

The previously recorded private-document evaluation conclusions and synthetic regression tests remain part of this baseline. No real private PDF or personal data is stored by this lock file.
