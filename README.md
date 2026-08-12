# Προσωπικός Φάκελος 2.0.1

Offline-first Android εφαρμογή για ιδιωτική οργάνωση εγγράφων, υποθέσεων και προθεσμιών. Η έκδοση 2.0.1 κρατά το ίδιο `applicationId` (`com.angel.personalfolder`) και αυξάνει το `versionCode` από 2 σε 3.

## Απαράβατη πολιτική μελλοντικών APK

Κάθε νέο κανονικό APK πρέπει να αποτελεί **ενημέρωση της ίδιας εγκατεστημένης εφαρμογής** και να διατηρεί τα υπάρχοντα δεδομένα. Αυτό είναι release-blocking απαίτηση.

- ίδιο release `applicationId`: `com.angel.personalfolder`,
- μεγαλύτερο `versionCode` σε κάθε έκδοση,
- **ίδια υπογραφή/signing certificate με την προηγούμενη εγκατεστημένη έκδοση**,
- καμία destructive migration ή uninstall/reinstall ως λύση αναβάθμισης,
- πλήρης διατήρηση συμβατότητας με υπάρχουσα βάση, κρυπτογραφημένα έγγραφα, OCR, metadata, υποθέσεις, σχέσεις, checklist, timeline, reminders και backups,
- αν το σωστό signing key δεν είναι διαθέσιμο, δεν επιτρέπεται να παρουσιαστεί νέο APK ως συμβατή ενημέρωση.

Το ιδιωτικό signing key δεν πρέπει να αποθηκεύεται στο δημόσιο repository. Η πλήρης πολιτική βρίσκεται στο [`docs/RELEASE_CONTINUITY.md`](docs/RELEASE_CONTINUITY.md) και οι ίδιοι κανόνες έχουν καταγραφεί στο [`AGENTS.md`](AGENTS.md) για κάθε μελλοντική εργασία πάνω στον κώδικα.

## Πραγματικές δυνατότητες

- Εισαγωγή PDF, εικόνων, πολλών εικόνων και Android share intents ως ένα λογικό document.
- Εσωτερικός viewer για PDF, εικόνες και mixed/multi-page documents με page counter, αλλαγή σελίδας, zoom και pan. Φορτώνεται μόνο η τρέχουσα σελίδα σε περιορισμένη ανάλυση.
- Πολυσέλιδο camera session με επανάληψη τελευταίας φωτογραφίας, βασική offline βελτίωση προσανατολισμού/περιθωρίου/αντίθεσης και preview πριν την εισαγωγή.
- Tesseract OCR με τα bundled `ell.traineddata` και `eng.traineddata`, bounded processing και εμφανές failed/retry state.
- OCR προτάσεις για τίτλο, κατηγορία, φορέα, ημερομηνίες και αριθμό πρωτοκόλλου. Κάθε πεδίο κρατά confidence/provenance· χαμηλής βεβαιότητας λήξη παραμένει πρόταση και δεν δημιουργεί reminder.
- Χειροκίνητη επεξεργασία όλων των metadata: τίτλος, κατηγορία, tags, φορέας, ημερομηνίες και αριθμός πρωτοκόλλου.
- Υποθέσεις με πλήρη πεδία, edit/delete, συνδεδεμένα documents, checklist με προαιρετικό document link, timeline και reminders deadline.
- Room FTS αναζήτηση σε τίτλο, filename, OCR, φορέα, κατηγορία, tags και protocol number, με φίλτρα κατηγορίας, υπόθεσης, processing state και σύντομης λήξης.
- Εξαγωγή ZIP με όλες τις σελίδες και streaming unified PDF χωρίς εξάρτηση από εξωτερικό viewer. Οι ordinary exports είναι μη κρυπτογραφημένοι.
- Password-protected portable backup με bounded/validated ZIP restore, interrupted-restore journal recovery, wrong-password/corruption handling, relations και reminder rescheduling.
- Biometric/device-credential lock με fail-closed policy, `FLAG_SECURE`, masked password fields και generic notifications χωρίς τίτλους εγγράφων.

## Απόρρητο και δεδομένα

Η εφαρμογή δεν έχει backend, λογαριασμό, analytics, trackers, διαφημίσεις ή `INTERNET` permission. Τα document bytes κρυπτογραφούνται με AES-GCM και Android Keystore. Το password μιας ενεργής επιλογής backup κρατιέται μόνο προσωρινά σε Keystore-backed κρυπτογραφημένο state.

Το portable backup περιλαμβάνει documents, OCR, metadata, cases, relations, checklist, timeline και reminders. Δεν περιλαμβάνει security settings, biometric state ή credentials. Για πραγματικά ευαίσθητη μεταφορά χρησιμοποίησε το encrypted backup και όχι ZIP/PDF export.

Το baseline security audit της V1 ολοκληρώθηκε με 9 source-backed findings (7 medium, 2 low). Οι διορθώσεις τους καταγράφονται στο changelog και στις σημειώσεις ασφαλείας· το audit καταγράφηκε πριν την αλλαγή του repository σε V2, επομένως δεν παρουσιάζεται ως scan του τελικού commit.

## Αναβάθμιση από V1

Η V2.0.1 χρησιμοποιεί Room migrations `1→2`, `2→3` και `3→4`, χωρίς destructive migration. Η `3→4` προσθέτει per-field confidence/manual ownership, expiry suggestions και πραγματικό deadline στις reminders. Το signing key πρέπει να είναι το ίδιο με της εγκατεστημένης έκδοσης για in-place APK upgrade.

## Build και quality gates

Απαιτούνται Java 17, Android SDK 36 και Gradle 8.11.1 ή Android Studio που υποστηρίζει το project.

```bash
gradle testDebugUnitTest
gradle lintDebug
gradle assembleDebug
gradle assembleDebugAndroidTest
gradle assembleRelease
```

Το debug APK παράγεται στο `app/build/outputs/apk/debug/`. Το debug variant έχει το υπάρχον `.debug` application suffix. Release signing δεν περιλαμβάνεται στο repository· για αναβάθμιση πάνω σε ήδη υπογεγραμμένη V1 χρειάζεται το ίδιο private signing material εκτός repository.

## Τεκμηρίωση

- [Release continuity](docs/RELEASE_CONTINUITY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Feature inventory](docs/FEATURE_INVENTORY.md)
- [Security and privacy](docs/SECURITY_PRIVACY.md)
- [Testing report](docs/TESTING_REPORT.md)
- [Changelog](CHANGELOG.md)

## Γνωστοί περιορισμοί

- Η scanner βελτίωση είναι σκόπιμα συντηρητική: δεν προσποιείται πλήρη AI/OpenCV four-corner perspective detection. Για δύσκολη λήψη με έντονη παραμόρφωση πρέπει να γίνει επανάληψη ή εξωτερική χειροκίνητη διόρθωση.
- Το Room database/FTS αποθηκεύει OCR και metadata ως app-private plaintext. Τα document bytes είναι κρυπτογραφημένα, αλλά πλήρης database encryption δεν προστέθηκε χωρίς ξεχωριστό audited SQLCipher/keystore design.
- Οι υπενθυμίσεις εξαρτώνται από τις ρυθμίσεις battery optimization/notifications της συσκευής. Παρελθοντικές reminders παραμένουν pending και ξαναπρογραμματίζονται όταν αποκατασταθεί η άδεια ειδοποιήσεων.
- Τα instrumentation tests εκτελούνται σε emulator μέσω CI· φυσική συσκευή παραμένει απαραίτητη για camera, biometric, διαφορετικούς SAF providers, OEM battery policies και πραγματική OCR συμπεριφορά.
