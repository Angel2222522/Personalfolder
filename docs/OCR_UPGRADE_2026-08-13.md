# OCR Upgrade — 2026-08-13

## Goal
Improve Greek/English document recognition without changing or degrading the already-correct PDF viewer path.

## Implemented pipeline

1. **Native PDF text first (Android API 35+)**
   - For digital PDFs, `PdfRenderer.Page.textContents` is read first.
   - Native text is normalized and sanity-checked.
   - If it is usable, it is persisted directly instead of rasterizing the page and guessing the text again with OCR.

2. **High-resolution OCR fallback**
   - Scanned/image-only PDF pages are rendered through a dedicated OCR path at a target of about 300 DPI, capped at 3600 px on the longest side.
   - The normal PDF display renderer remains independent and unchanged in behavior.
   - Imported images are decoded up to 3600 px for OCR.

3. **Accuracy-first Tesseract models**
   - Replaced `tessdata_fast` with pinned, checksum-verified official `tessdata_best` Greek and English models.
   - Greek model: `ell.traineddata` from tessdata_best commit `e12c65a915945e4c28e237a9b52bc4a8f39a0cec`.
   - English model: `eng.traineddata` from the same pinned commit.
   - Runtime uses LSTM mode, automatic page segmentation, 300-DPI hint and preserved inter-word spacing.

4. **Greek/Latin OCR script repair**
   - Added conservative Unicode cleanup.
   - Repairs visually identical Latin capitals embedded inside an otherwise clearly Greek token (for example OCR output like `EΛΛHNIKH` or `AΔEIA`).
   - Pure English/Latin words are intentionally not converted.

5. **Residence-permit classification improvements**
   - Expanded migration/permit vocabulary for Greek and English variants such as `άδεια διαμονής`, `τίτλος διαμονής`, `κάρτα διαμονής`, `residence permit`, `permit type`, `δεύτερης γενιάς`, and ministry/migration markers.

## Regression protection

Added unit tests for:
- Greek/Latin confusable-character cleanup.
- Preservation of genuine English text.
- Unicode/control-character cleanup.
- Native-PDF-text sanity checks.
- Greek and English residence-permit classification.
- Mixed-script residence-permit classification.

No emulator is required for this branch's verification workflow. The no-emulator CI checks unit tests, lint, instrumentation-test compilation, debug APK build and unsigned release compilation.

## Future engine option: PaddleOCR

Research on 2026-08-13 found that PP-OCRv5 has a dedicated Greek recognition model, while the newer PP-OCRv6 multilingual recognition model does not include Greek. The current official Greek PP-OCRv5 model is distributed in Paddle format, whereas the current official Android SDK uses ONNX assets. Do not add an unofficial converted model merely to claim PaddleOCR support. Integrate it only when the Greek model can be converted or consumed through a reproducible, checksum-pinned and tested path.

## Invariant

**Do not modify the source PDF or couple OCR preprocessing to the PDF viewer.** OCR may render a separate bitmap or use native text extraction, but the original encrypted document and viewer rendering path must remain intact.
