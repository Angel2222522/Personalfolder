# V2 feature inventory

> Historical V2 inventory. Current verification evidence is maintained in
> `CODE_AUDIT_REMEDIATION_STATUS.md`; references below to run #89 are historical.

| Area | V2 behavior | Verification state |
|---|---|---|
| Upgrade | Room 1→2→3→4 migrations, no destructive migration | Migration instrumentation and final run #89 passed; permanent signed update still blocked by missing signing secrets |
| Multi-page | Ordered page sources; nested PDF pages included in viewer/share/ZIP/PDF | Emulator run #89 passed original-PDF, multi-page and ZIP export tests |
| Viewer | Internal bounded PDF/image renderer, page controls, zoom/pan | Source-level verified; physical PDF/OEM test pending |
| Scanner | Multi-page camera session, retry, preview, EXIF rotation and conservative contrast enhancement; original capture is retained and no destructive auto-crop is applied | Source-level verified; difficult perspective shots remain a limitation |
| OCR | Bundled Greek/English models, bounded decode/text, failure and retry | Model assets/hash/size audited; real device recognition pending |
| Metadata | Original match ranges, context scoring, confidence, manual override | Unit tests cover date context and fallback confidence |
| Cases | Full fields, edit/delete, relations, checklist links, timeline, deadline reminders | Repository/UI source coverage; PF-018 lifecycle semantics remain a product decision |
| Reminders | Replace/cancel/reschedule, no duplicate unique work, past triggers remain pending for delivery | Emulator run #89 passed confirmed-expiry creation/removal; reboot/timezone/OEM behavior pending |
| Lock | Biometric/device credential fail-closed, secure window, masked passwords | Source-level verified; biometric hardware test pending |
| Temp files | Deterministic `finally` cleanup plus startup stale-cache recovery | Source-level verified; process-kill test pending |
| Backup | Portable AES-GCM envelope, bounded archive, validation, rollback journal | Instrumentation round-trip/wrong-password/corruption tests added |
| Search | FTS-backed query with metadata and case/expiry/processing filters | Room schema/query review; large-library benchmark pending |
| Export | All source pages in ZIP; streaming unified PDF | Emulator run #89 passed ZIP manifest/source-byte test; large/mixed-document device test pending |
| Privacy | Offline, no backend/analytics/INTERNET, backup excludes security settings | Manifest/source audit |
