# V2 feature inventory

| Area | V2 behavior | Verification state |
|---|---|---|
| Upgrade | Room 1→2→4 migrations, no destructive migration; `linkedDocumentId` index preserved | Migration instrumentation test added; execution still requires Android instrumentation environment |
| Multi-page | Ordered page sources; nested PDF pages included in viewer/share/ZIP/PDF | Unit structure reviewed; device export test still required |
| Viewer | Internal bounded PDF/image renderer, page controls, zoom/pan | Source-level verified; physical PDF/OEM test pending |
| Scanner | Multi-page camera session, retry, preview, EXIF rotation, conservative auto crop/contrast | Source-level verified; difficult perspective shots remain a limitation |
| OCR | Bundled verified Greek/English models, URI/signature detection, PDF rendering, EXIF rotation, bounded decode/text, atomic page/document commit, failure/retry/recovery | Host OCR fixtures and model integrity verified; Android Worker/device execution still pending |
| Metadata | Original match ranges, context scoring, confidence, manual override | Unit tests cover date context and fallback confidence |
| Cases | Full fields, edit/delete, relations, checklist links, timeline, deadline reminders | Repository/UI source coverage; end-to-end device test pending |
| Reminders | Replace/cancel/reschedule, no duplicate unique work, no past-date immediate jobs | Source-level verified; reboot/timezone/OEM behavior pending |
| Lock | Biometric/device credential fail-closed, secure window, masked passwords | Source-level verified; biometric hardware test pending |
| Temp files | Deterministic `finally` cleanup plus startup stale-cache recovery | Source-level verified; process-kill test pending |
| Backup | Portable AES-GCM envelope, bounded archive, validation, rollback journal | Instrumentation round-trip/wrong-password/corruption tests added |
| Search | Unicode61 FTS-backed query with Greek case/tonos normalization and metadata/case/expiry/processing filters | Normalization and trigger path covered in source/tests; large-library benchmark pending |
| Export | All source pages in ZIP; streaming unified PDF | Source-level verified; large/mixed-document device test pending |
| Privacy | Offline, no backend/analytics/INTERNET, backup excludes security settings | Manifest/source audit |
