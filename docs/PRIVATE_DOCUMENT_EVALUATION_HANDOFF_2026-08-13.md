# Private document evaluation handoff — 2026-08-13

## Purpose

This file preserves the engineering conclusions from a private evaluation set so future work can continue from the current state instead of repeating the same investigation. It intentionally contains no real uploaded document, person name, identifier, protocol value, address, personal date, OCR transcript, or other private data.

## Branch / continuity

- Continue from branch `codex/personal-folder-rapid-no-emulator` or a descendant of its current verified tip.
- Do not restart the remediation from `main`.
- Preserve the existing OCR, metadata, independent-import, and no-emulator verification work unless a concrete regression proves a change is necessary.

## Private evaluation set — anonymized outcomes

Eight private PDFs were reviewed structurally. Their real contents are not stored in the repository. The reusable lessons are represented by synthetic regression tests and production rules.

1. School study certificate — PASS after OCR/layout hardening.
   - Prefer the actual certificate title over republic/ministry headers.
   - Prefer the concrete school as issuer.
   - Accept common abbreviated Greek protocol labels and separator spacing.
   - A place/date dateline may be issue-date evidence, never expiry evidence.
   - Mixed Greek/Latin OCR and school ordinal layout must be repaired conservatively.

2. Retail order — PASS.
   - Recognize the document-type field as the title.
   - Keep the order/document date as issue date.
   - Do not reinterpret order numbers or commercial identifiers as protocol.
   - Do not invent expiry.

3. Medical certificate — PASS.
   - Classify as `Υγεία` from medical structure.
   - Keep the healthcare unit as issuer.
   - A blank expiry field remains no expiry.
   - A certificate number is not protocol unless explicitly labeled as protocol.

4. School attendance certificate — PASS.
   - Repair split/joined school headings in native PDF text.
   - Preserve school issuer, issue date and labeled protocol.
   - Do not let a referenced administrative purpose change the document's own identity.

5. Civil-registry birth record — initially FAILED category semantics, then FIXED and PASS.
   - A birth civil-registry record is `Ταυτότητα / προσωπικά` rather than the generic `Δημόσιες υπηρεσίες` category.
   - Recover a specific registry office from flattened header structure when the raw issuer is only generic `ΛΗΞΙΑΡΧΕΙΟ`.
   - Prefer the document's leading/current protocol over historical references later in the body.

6. Multi-page administrative application dossier — PASS.
   - Leading document evidence controls metadata; attachments must not override it.
   - The application's own issue date is valid evidence.
   - A referenced permit expiry inside the dossier must not become the application's expiry.
   - Do not fabricate protocol from unrelated numbers in attachments.

7. Residence-permit decision — PASS.
   - Recover protocol from stacked header layouts.
   - Use the explicit validity-range end as expiry.
   - Keep the migration authority as issuer and classify under migration/residence permits.
   - Historical protocols in legal background text must not replace the current document protocol.

8. Bank account statement — PASS.
   - Classify bank account statements as `Οικονομικά`, not utility `Λογαριασμοί`.
   - Accept an explicitly labeled two-digit-year issue date using the deterministic date rule.
   - Statement period end is not document expiry.
   - Do not infer protocol from transaction/reference numbers.

## Permanent regression protection

The reusable lessons above are covered by synthetic tests, especially `MetadataEvaluationSuiteTest`, plus targeted OCR/title/provider/protocol/date tests. Synthetic fixtures must stay fully fake and must never copy private values from the evaluation documents.

Key invariants for future changes:

- Never infer expiry from an unrelated date.
- Never copy issue date into expiry.
- Never treat registry, student, certificate, order, transaction or account numbers as protocol without protocol evidence.
- Prefer the most concrete issuing authority over a generic parent government header.
- Prefer the actual document-type title over decorative/government headers.
- Keep leading-document metadata protected from later attachments in dossiers.
- Keep OCR corrections deterministic and conservative; never invent missing personal text.
- Preserve independent multi-file import and failure isolation.
- Keep heavyweight OCR serialized and avoid holding the global library lock during OCR.
- Keep private evaluation material out of GitHub, logs, CI artifacts and commit messages.

## Verification state

After the civil-registry category correction, the full no-emulator verification workflow passed: unit tests, lint, Android-test compilation, debug compilation, release compilation and schema upload. Incremental evaluation does not package an APK automatically.
