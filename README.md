# Προσωπικός Φάκελος 2

Offline-first Android εφαρμογή για ιδιωτική οργάνωση εγγράφων, υποθέσεων και προθεσμιών. Η έκδοση 2.0.0 κρατά το ίδιο `applicationId` (`com.angel.personalfolder`) και αυξάνει το `versionCode` από 1 σε 2.

## Πραγματικές δυνατότητες

- Εισαγωγή PDF, εικόνων, πολλών εικόνων και Android share intents ως ένα λογικό document.
- Εσωτερικός viewer για PDF, εικόνες και mixed/multi-page documents με page counter, αλλαγή σελίδας, zoom και pan. Φορτώνεται μόνο η τρέχουσα σελίδα σε περιορισμένη ανάλυση.
- Πολυσέλιδο camera session με επανάληψη τελευταίας φωτογραφίας, βασική offline βελτίωση προσανατολισμού/περιθωρίου/αντίθεσης και preview πριν την εισαγωγή.
- Tesseract OCR με τα bundled `ell.traineddata` και `eng.traineddata`, bounded processing και εμφανές failed/retry state.
- OCR προτάσεις για τίτλο, κατηγορία, φορέα, ημερομηνίες και αριθμό πρωτοκόλλου. Οι ημερομηνίες αξιολογούνται στο πραγματικό range του αρχικού OCR text και φέρουν confidence.
- Χειροκίνητη επεξεργασία όλων των metadata: τίτλος, κατηγορία, tags, φορέας, ημερομηνίες και αριθμός πρωτοκόλλου.
- Υποθέσεις με πλήρη πεδία, edit/delete, συνδεδεμένα documents, checklist με προαιρετικό document link, timeline και reminders deadline.
- Room FTS αναζήτηση σε τίτλο, filename, OCR, φορέα, κατηγορία, tags και protocol number, με φίλτρα κατηγορίας, υπόθεσης, processing state και σύντομης λήξης.
- Εξαγωγή ZIP με όλες τις σελίδες και streaming unified PDF χωρίς εξάρτηση από εξωτερικό viewer. Οι ordinary exports είναι μη κρυπτογραφημένοι.
- Password-protected portable backup με bounded/validated ZIP restore, wrong-password/corruption handling, relations και reminder rescheduling.
- Biometric/device-credential lock με fail-closed policy, `FLAG_SECURE`, masked password fields και generic notifications χωρίς τίτλους εγγράφων.

## Απόρρητο και δεδομένα

Η εφαρμογή δεν έχει backend, λογαριασμό, analytics, trackers, διαφημίσεις ή `INTERNET` permission. Τα document bytes κρυπτογραφούνται με AES-GCM και Android Keystore. Το backup password δεν αποθηκεύεται.

Το portable backup περιλαμβάνει documents, OCR, metadata, cases, relations, checklist, timeline και reminders. Δεν περιλαμβάνει security settings, biometric state ή credentials. Για πραγματικά ευαίσθητη μεταφορά χρησιμοποίησε το encrypted backup και όχι ZIP/PDF export.

Το baseline security audit της V1 ολοκληρώθηκε με 9 source-backed findings (7 medium, 2 low). Οι διορθώσεις τους καταγράφονται στο changelog και στις σημειώσεις ασφαλείας· το audit καταγράφηκε πριν την αλλαγή του repository σε V2, επομένως δεν παρουσιάζεται ως scan του τελικού commit.

## Αναβάθμιση από V1

Η V2 χρησιμοποιεί Room migrations `1→2` και `2→3`, χωρίς destructive migration. Η `2→3` προσθέτει manual-metadata flag, source metadata για κάθε page, foreign keys/cascades και FTS index, διατηρώντας τα υπάρχοντα rows και αρχεία. Το signing key πρέπει να είναι το ίδιο με της εγκατεστημένης V1 για in-place APK upgrade.

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

- [Architecture](docs/ARCHITECTURE.md)
- [Feature inventory](docs/FEATURE_INVENTORY.md)
- [Security and privacy](docs/SECURITY_PRIVACY.md)
- [Testing report](docs/TESTING_REPORT.md)
- [Changelog](CHANGELOG.md)

## Γνωστοί περιορισμοί

- Η scanner βελτίωση είναι σκόπιμα συντηρητική: δεν προσποιείται πλήρη AI/OpenCV four-corner perspective detection. Για δύσκολη λήψη με έντονη παραμόρφωση πρέπει να γίνει επανάληψη ή εξωτερική χειροκίνητη διόρθωση.
- Το Room database/FTS αποθηκεύει OCR και metadata ως app-private plaintext. Τα document bytes είναι κρυπτογραφημένα, αλλά πλήρης database encryption δεν προστέθηκε χωρίς ξεχωριστό audited SQLCipher/keystore design.
- Οι υπενθυμίσεις εξαρτώνται από τις ρυθμίσεις battery optimization/notifications της συσκευής. Παρελθοντικές reminders δεν προγραμματίζονται ξανά αυτόματα.
- Τα instrumentation tests ελέγχουν Android APIs μόνο όταν τρέξουν σε emulator/συσκευή. Η CI κάνει compile το `androidTest`, αλλά φυσική συσκευή παραμένει απαραίτητη για camera, biometric, SAF providers, OEM battery policies και πραγματική OCR συμπεριφορά.
