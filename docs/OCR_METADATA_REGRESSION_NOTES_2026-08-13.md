# OCR / metadata regression notes — 2026-08-13

## Scope

This note records narrowly-scoped fixes discovered while validating a scanned Greek public-school certificate. No personal names, document identifiers, uploaded files, or other user data are retained here.

The fixes intentionally do **not** change PDF rendering, encryption, database migrations, backup/restore, reminders, sharing, or screenshot policy.

## Regressions and rules

### 1. Document title

Problem: the first OCR line can be a generic state heading, so treating `lines.first()` as the title promotes text such as a republic/ministry heading instead of the real document title.

Rule: prefer explicit document-type headings (`βεβαίωση`, `πιστοποιητικό`, `άδεια`, `απόφαση`, etc.). A meaningful filename-derived fallback is preferable when no real heading is found. Generic state/ministry hierarchy is not itself a document title.

Regression coverage: `prefersActualDocumentHeadingOverGenericGovernmentHeader` and `schoolCertificateRegressionKeepsFieldsIndependent`.

### 2. Issuing authority

Problem: a parent ministry can outscore the concrete issuing institution.

Rule: when OCR contains a concrete school/university issuer, prefer it over the parent ministry. `Ελληνική Δημοκρατία` remains a weak generic heading, not the issuer.

Regression coverage: `prefersConcreteSchoolIssuerOverParentMinistry`.

### 3. Protocol number

Problem: common Greek forms such as abbreviated `Αριθμ. Πρωτ:` and OCR-inserted whitespace around `/` were rejected.

Rule: accept established protocol-label variants at the start of their line, preserve the captured value's script/case, and remove only separator whitespace. Never treat `Αριθμός Μητρώου` or an arbitrary application number as protocol.

Regression coverage: `acceptsAbbreviatedProtocolAndOcrWhitespaceAroundSeparators` plus the existing negative registry-number tests.

### 4. Issue date vs expiry date

Problem: official Greek documents commonly use a place dateline (`Πόλη: dd-MM-yyyy`) without the words `ημερομηνία έκδοσης`.

Rule: a short place-name prefix ending in `:` or `,` is medium-confidence issue-date evidence. It must never create an expiry date. Explicit creation/issue/expiry labels retain priority, and unlabelled dates remain untrusted.

Regression coverage: `officialPlaceDatelineIsIssueDateButNeverExpiry`, plus existing tests that forbid invented expiry dates.

### 5. OCR script/character cleanup

Problem: OCR can mix visually-confusable Latin/Greek capitals or produce recurring administrative-label transliterations.

Rule: deterministic cleanup may repair known script confusions and standard administrative field words, but must not reconstruct missing names, signatures, stamps, or values from guesses. School ordinal symbols such as `28° Γυμνάσιο` may be normalized only in explicit school context.

Regression coverage: `OcrTextPostProcessorTest`.

## Invariants for future changes

- Never derive expiry merely because another date exists.
- Never copy the creation/issue date into expiry.
- Never interpret registry/student/member number as protocol without a protocol label.
- Prefer the most concrete issuer present in the document.
- Do not assume the first OCR line is the title.
- Do not rewrite arbitrary English text just because the document is Greek.
- Do not invent unreadable signature/seal text; retain uncertainty rather than fabricate content.
- Keep fixes test-backed and isolated from unrelated application subsystems.
- Verification for this branch remains no-emulator: unit tests, lint, instrumentation-test compilation, debug APK build, and release compilation only.
