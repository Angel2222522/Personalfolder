# Τεχνικός έλεγχος κώδικα - Personal Folder / Προσωπικός Φάκελος

## 1. Ταυτότητα ελεγχόμενου κώδικα

| Πεδίο | Τιμή |
|---|---|
| Repository | `Angel2222522/Personalfolder` - `https://github.com/Angel2222522/Personalfolder.git` |
| Branch | `codex/personal-folder-remediation` |
| Ακριβές commit που ελέγχθηκε | `ec297f2f0b17b3b273edc60bfbc18907435e86b9` |
| Git tree | `6a4b9269f615c25b7ffbfd81d786a378f3d74c3d` |
| Μήνυμα commit | `test: require absent expiry to remain absent` |
| Ημερομηνία commit | 13 Αυγούστου 2026, 01:30:05 (Europe/Athens) |
| Ημερομηνία ελέγχου | 13 Αυγούστου 2026 (Europe/Athens) |
| Application ID | `com.angel.personalfolder` |
| Ορατή έκδοση | `2.0.1` |
| Εσωτερικός αριθμός κατασκευής | `versionCode 3` |
| Room schema | έκδοση `4` |
| Κατάσταση worktree πριν την αναφορά | καθαρό, σε ταύτιση με το παραπάνω commit |

Η αναφορά αφορά αποκλειστικά την παραπάνω κατάσταση. Η προσθήκη του ίδιου του `CODE_AUDIT_REPORT.md` δεν αποτελεί μέρος του ελεγχόμενου προϊόντος και δεν μεταβάλλει την ταυτότητα του κώδικα που αξιολογήθηκε.

## 2. Εκτελεστική σύνοψη

### Συνολική διάγνωση

Το έργο δεν είναι πρόχειρο πρωτότυπο. Διαθέτει πραγματική τοπική κρυπτογράφηση αρχείων, ρητές Room migrations, portable κρυπτογραφημένο backup, offline OCR, PDF rendering, FTS αναζήτηση, WorkManager, recovery journals και ουσιαστική αυτοματοποιημένη κάλυψη. Στο ακριβές commit του ελέγχου, το CI πέρασε unit tests, lint, compilation, debug build, instrumentation compilation, 17/17 δοκιμές emulator και unsigned release compilation.

Παρά τα παραπάνω, το σύστημα **δεν πρέπει ακόμη να χαρακτηριστεί production-ready ή release-ready**. Υπάρχουν δύο επιβεβαιωμένοι μηχανισμοί που μπορούν να ακυρώσουν την υπόσχεση διατήρησης δεδομένων: το backup μπορεί νόμιμα να δημιουργήσει αρχείο που ο ίδιος κώδικας αρνείται να επαναφέρει, και η αποτυχημένη ανάκτηση μετά από διακοπή ανοίγει κανονικά την εφαρμογή χωρίς να έχει αποδειχθεί ότι Room και αρχεία ανήκουν στην ίδια γενιά. Επιπλέον, η συνέχεια υπογραφής τελικού APK δεν έχει αποδειχθεί και παραμένει ρητό release blocker.

Η εικόνα ανά διάσταση είναι:

| Διάσταση | Διάγνωση |
|---|---|
| Ορθότητα βασικών ροών | Λειτουργική σε αρκετές ιδανικές διαδρομές, αλλά με επιβεβαιωμένες ασυνέπειες σε backup, recovery και multi-source PDF. |
| Ακεραιότητα δεδομένων | Ισχυρές προθέσεις και αρκετές ασφαλιστικές δικλείδες, αλλά ανεπαρκής κοινή συναλλαγή μεταξύ Room, filesystem και WorkManager. |
| Ασφάλεια | Καλή βάση για app-private/offline χρήση· ο βιομετρικός φραγμός είναι κυρίως UI state και δεν περιφρουρεί όλες τις λειτουργίες. |
| Απόδοση/σταθερότητα | Εύθραυστη σε μεγάλες βιβλιοθήκες, μεγάλα OCR κείμενα, πολυσέλιδα PDF και εικόνες μεγάλης ανάλυσης. |
| Συντηρησιμότητα | Μέτρια. Υπάρχουν καθαρές υπηρεσίες/πολιτικές, αλλά και υπερβολικά πλατιά entities, παγκόσμιος mutex και μονολιθικό UI. |
| Δοκιμές | Αξιόλογη στοχευμένη βάση, όχι επαρκής για lifecycle, UI, fault injection, πραγματικό corpus και όρια κλίμακας. |
| Release | Μη έτοιμο: δεν υπάρχει επαληθευμένο, μόνιμα υπογεγραμμένο release artifact συμβατό με την ήδη εγκατεστημένη εφαρμογή. |

### Αριθμητική εικόνα ευρημάτων

Η αναφορά ενοποιεί συμπτώματα με κοινή ρίζα και δεν μετρά κάθε επιμέρους εκδήλωση ως νέο σφάλμα.

| Σοβαρότητα | Πλήθος | Χαρακτήρας |
|---|---:|---|
| Κρίσιμη | 3 | 2 επιβεβαιωμένα σφάλματα/κίνδυνοι ακεραιότητας, 1 επιβεβαιωμένο release blocker |
| Υψηλή | 7 | 4 επιβεβαιωμένα προβλήματα, 1 πολύ πιθανό security/lifecycle πρόβλημα, 2 αρχιτεκτονικές/δομικές αδυναμίες |
| Μεσαία | 8 | επιβεβαιωμένες τοπικές ασυνέπειες και κίνδυνοι που χρειάζονται στοχευμένη επιβεβαίωση |
| Χαμηλή | 2 | ποιότητα, τεκμηρίωση και μακροχρόνια συντήρηση |

### Πρώτη προτεραιότητα επόμενης φάσης

1. Να οριστεί ένα ενιαίο, συμμετρικό συμβόλαιο για το ποια ζωντανή κατάσταση μπορεί να γίνει backup και να αποκατασταθεί χωρίς απώλεια.
2. Να μετατραπεί η startup recovery από «προσπάθησα και άνοιξα» σε αποδεδειγμένη κατάσταση `safe`, `recovered` ή `blocked`.
3. Να γίνει ο έλεγχος κλειδώματος πραγματικό authorization boundary για callbacks και λειτουργίες, όχι μόνο composable οθόνη.
4. Να αφαιρεθούν οι απεριόριστες πλήρεις υλοποιήσεις OCR/bitmap/JSON από memory-sensitive διαδρομές.
5. Να επιβεβαιωθεί το μόνιμο signing certificate και η πραγματική in-place αναβάθμιση πριν παραδοθεί release APK.

## 3. Πεδίο και μέθοδος ελέγχου

Ο έλεγχος κάλυψε και συσχέτισε:

- 83 tracked αρχεία του repository,
- 37 κύρια Kotlin αρχεία, περίπου 6.018 γραμμές,
- 20 αρχεία unit/instrumentation tests, περίπου 1.280 γραμμές,
- Gradle/Android/CI/signing ρυθμίσεις,
- manifest, permissions, FileProvider και Android activity lifecycle,
- Room entities, DAOs, migrations, FTS και recovery callbacks,
- εισαγωγή PDF/εικόνων, κρυπτογράφηση, αποκρυπτογράφηση και διαγραφή,
- OCR, Tesseract models, PDF rendering και scanner preprocessing,
- metadata extraction/application και reminder semantics,
- backup creation, archive validation, restore, journal recovery και generation fingerprints,
- ZIP/PDF export, viewer και share/open flows,
- Compose state, ViewModel concurrency και user-visible error paths,
- build reproducibility, dependency provenance, testing evidence και release continuity.

Χρησιμοποιήθηκαν μόνο μη καταστροφικοί έλεγχοι. Δεν εφαρμόστηκε διόρθωση, refactor, dependency update, schema change ή αλλαγή συμπεριφοράς.

### Επαληθεύσεις που πράγματι εκτελέστηκαν ή ανακτήθηκαν

Στο ακριβές commit `ec297f2...`, το GitHub Actions run **#97**, id `31647212991`, ολοκληρώθηκε επιτυχώς. Επιβεβαιώθηκαν τα παρακάτω βήματα:

- `gradle testDebugUnitTest`: επιτυχία,
- `gradle lintDebug`: επιτυχία,
- `gradle assembleDebugAndroidTest`: επιτυχία,
- `gradle assembleDebug`: επιτυχία,
- emulator `connectedDebugAndroidTest`: **17 tests, 0 failures, 0 errors, 0 skipped**,
- pull-request `gradle assembleRelease`: επιτυχία ως unsigned compilation,
- Room schema artifact upload: επιτυχία.

Το source tree περιέχει 43 μεθόδους unit test και 17 μεθόδους instrumentation test. Το instrumentation XML του run #97 επιβεβαιώνει το πραγματικό αποτέλεσμα 17/17. Τα signed release βήματα παραλείφθηκαν επειδή το run ήταν pull request, όπως προβλέπει το workflow.

### Περιορισμοί της διάγνωσης

- Το repository δεν περιέχει Gradle wrapper και το περιβάλλον ελέγχου δεν διαθέτει εγκατεστημένο `gradle`. Δεν έγινε δεύτερη τοπική εκτέλεση build· χρησιμοποιήθηκε το επαληθεύσιμο CI του ίδιου commit.
- Δεν εκτελέστηκε φυσική συσκευή. Camera, βιομετρικά callbacks, OEM SAF providers, reboot/timezone και πραγματικές συνθήκες battery management παραμένουν μη επαληθευμένες σε συσκευή.
- Δεν υπήρχε διαθέσιμο resolved dependency lockfile/SBOM ούτε τοπικό εργαλείο CVE scan. Η αναφορά δεν ισχυρίζεται ότι οι εξαρτήσεις είναι απαλλαγμένες από γνωστές ευπάθειες.
- Ο εξειδικευμένος αυτοματοποιημένος Deep Security Scan δεν μπόρεσε να ξεκινήσει, επειδή το περιβάλλον δεν μπορούσε να διατηρήσει με ασφάλεια τους filesystem denials στον read-only worker. Έγινε δεύτερη χειροκίνητη security διέλευση, αλλά δεν παρουσιάζεται ως προϊόν του συγκεκριμένου αυτοματοποιημένου σαρωτή.
- Δεν έγινε destructive fault injection με kill process/power loss κατά τη διάρκεια πραγματικού restore ή delete. Όπου το συμπέρασμα στηρίζεται σε state-machine ανάλυση και όχι σε runtime fault injection, αυτό δηλώνεται.

## 4. Αρχιτεκτονικός χάρτης

Η εφαρμογή είναι single-activity Android app με Jetpack Compose. Τα κύρια όρια είναι:

| Επίπεδο | Κύρια στοιχεία | Ευθύνη |
|---|---|---|
| UI/lifecycle | `MainActivity`, `FolderApp`, `FolderViewModel`, dialogs/viewer/scanner | permissions, biometric prompt, pickers, Compose state, user actions |
| Domain/data orchestration | `FolderRepository`, `BackupService`, `ExportService`, `ReminderScheduler` | επιχειρησιακές ροές και συντονισμός υποσυστημάτων |
| Persistence | `AppDatabase`, entities, DAOs, migrations, FTS triggers | Room state και αναζήτηση |
| File/security | `FileCrypto`, `BackupCrypto`, `PendingActivityStateStore`, `TempFileCleaner` | AES-GCM, Keystore, προσωρινά αρχεία |
| Processing | `DocumentProcessor`, `TesseractOcrEngine`, `MetadataExtractor`, `ScannerImageProcessor` | OCR, rendering, heuristics και metadata |
| Background | `OcrWorker`, `ReminderWorker` | WorkManager execution |
| Cross-system gate | `DataOperationCoordinator` | process-wide serialization και startup recovery gate |

Η κρίσιμη πραγματικότητα είναι ότι η λογική βιβλιοθήκη δεν βρίσκεται σε ένα μόνο σύστημα. Διαμοιράζεται σε Room, κρυπτογραφημένα αρχεία, WorkManager work records, cache plaintext και UI session state. Τα σοβαρότερα ευρήματα προκύπτουν εκεί όπου μία τοπικά σωστή ενέργεια θεωρείται συνολικά ολοκληρωμένη χωρίς κοινό commit/rollback ή αποδεικτικό generation state.

## 5. Ιεράρχηση ευρημάτων

| ID | Σοβαρότητα | Κατηγορία | Σύντομος τίτλος | Βεβαιότητα |
|---|---|---|---|---|
| PF-AUD-001 | Κρίσιμη | Επιβεβαιωμένο σφάλμα | Η εφαρμογή μπορεί να δημιουργήσει backup που η ίδια δεν μπορεί να επαναφέρει | Πολύ υψηλή |
| PF-AUD-002 | Κρίσιμη | Επιβεβαιωμένο σφάλμα ακεραιότητας | Η αποτυχημένη startup recovery ανοίγει κανονικά τη λειτουργία | Πολύ υψηλή |
| PF-AUD-003 | Κρίσιμη | Επιβεβαιωμένο release blocker | Δεν έχει αποδειχθεί συμβατή, μόνιμα υπογεγραμμένη έκδοση | Απόλυτη |
| PF-AUD-004 | Υψηλή | Επιβεβαιωμένο λειτουργικό σφάλμα | Multi-source έγγραφο χάνει πηγές όταν η πρώτη πηγή είναι PDF | Πολύ υψηλή |
| PF-AUD-005 | Υψηλή | Πολύ πιθανό security/lifecycle πρόβλημα | Picker callbacks εκτελούν ευαίσθητες πράξεις μετά το κλείδωμα | Υψηλή |
| PF-AUD-006 | Υψηλή | Επιβεβαιωμένη δομική αδυναμία | Όρια πόρων εφαρμόζονται μετά από πλήρη materialization | Πολύ υψηλή |
| PF-AUD-007 | Υψηλή | Επιβεβαιωμένο σφάλμα lifecycle | OCR μπορεί να μείνει μόνιμα `PROCESSING` ή με μερική κατάσταση | Πολύ υψηλή |
| PF-AUD-008 | Υψηλή | Αρχιτεκτονική/απόδοση | Ο παγκόσμιος mutex κρατιέται σε OCR, backup και export μεγάλης διάρκειας | Πολύ υψηλή |
| PF-AUD-009 | Υψηλή | Επιβεβαιωμένη ασυνέπεια | Reminders, Room και WorkManager δεν ενημερώνονται ατομικά | Πολύ υψηλή |
| PF-AUD-010 | Υψηλή | Επιβεβαιωμένο migration risk | Η migration 2→3 δηλώνει fail-closed αλλά μηδενίζει ορισμένους άκυρους δεσμούς | Υψηλή |
| PF-AUD-011 | Μεσαία | Επιβεβαιωμένο λανθάνον σφάλμα | Import μπορεί να αποθηκευτεί αλλά να αναφερθεί ως αποτυχημένο | Υψηλή |
| PF-AUD-012 | Μεσαία | Επιβεβαιωμένο concurrency/lifecycle πρόβλημα | Το `busy` και τα cleanup callbacks δεν είναι ασφαλή σε επικάλυψη/ακύρωση | Πολύ υψηλή |
| PF-AUD-013 | Μεσαία | Επιβεβαιωμένη αδυναμία ανάκτησης | Το FTS repair ελέγχει IDs, όχι το πραγματικό περιεχόμενο | Απόλυτη |
| PF-AUD-014 | Μεσαία | Επιβεβαιωμένο λογικό σφάλμα | Η επιλογή φορέα αντιστρέφει tie-breakers και υποβαθμίζει σύνθετες γραμμές | Πολύ υψηλή |
| PF-AUD-015 | Μεσαία | Επιβεβαιωμένα UI state defects | Viewer, editor, case detail και restore dialog έχουν ασυνεπείς καταστάσεις | Πολύ υψηλή |
| PF-AUD-016 | Μεσαία | Δομική/εφοδιαστική αδυναμία | Το build και το schema history δεν είναι πλήρως αναπαραγώγιμα | Απόλυτη |
| PF-AUD-017 | Μεσαία | Κάλυψη/ποιότητα | Οι ισχυρισμοί ποιότητας υπερβαίνουν το πραγματικό test envelope | Πολύ υψηλή |
| PF-AUD-018 | Μεσαία | Απαιτεί επιβεβαίωση/προδιαγραφή | Reminder lifecycle, scanner crop και checklist UX παραμένουν ασαφή ή ελλιπή | Μεσαία-υψηλή |
| PF-AUD-019 | Χαμηλή | Privacy/cleanup risk | Wall-clock cleanup και orphan staging μπορούν να παρατείνουν plaintext/cache κατάλοιπα | Υψηλή |
| PF-AUD-020 | Χαμηλή | Ποιότητα/συντηρησιμότητα | Μονολιθικό UI, hardcoded κείμενα και παρωχημένα assets αυξάνουν παλινδρομήσεις | Απόλυτη |

## 6. Αναλυτικά ευρήματα

### PF-AUD-001 - Το backup δεν είναι κλειστό ως προς τις νόμιμες καταστάσεις της εφαρμογής

**Κατηγορία:** επιβεβαιωμένο σφάλμα συμβολαίου δεδομένων
**Σοβαρότητα:** κρίσιμη
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Ο κώδικας δημιουργίας backup επιτρέπει καταστάσεις τις οποίες ο κώδικας restore απορρίπτει. Δεν πρόκειται μόνο για κακόβουλο ή ξένο archive: μία κανονική βιβλιοθήκη που δημιουργήθηκε αποκλειστικά από την ίδια εφαρμογή μπορεί να παραγάγει backup που αργότερα δεν επαναφέρεται.

**Πού και ποια τμήματα σχετίζονται.** `BackupService.create/snapshot` (`app/src/main/java/com/angel/personalfolder/data/BackupService.kt:107-188`), `inspectArchive` (`575-596`), parsers/limits (`389-568`, `697-712`), `FolderRepository` limits (`app/src/main/java/com/angel/personalfolder/data/FolderRepository.kt:52-123`, `435-441`), `DocumentProcessor.MAX_DOCUMENT_OCR_CHARS` (`app/src/main/java/com/angel/personalfolder/processing/DocumentProcessor.kt:149-153`) και `ReminderScheduler` (`app/src/main/java/com/angel/personalfolder/data/ReminderScheduler.kt:14-55`).

**Συγκεκριμένες αντιφάσεις.**

- Η δημιουργία γράφει ολόκληρο το `DocumentEntity.ocrText` και ξανά τα page OCR texts σε ένα in-memory `JSONObject`, έπειτα σε ένα `ByteArray` (`BackupService.kt:167-188`, `654-662`). Δέχεται manifest ως μέρος του συνολικού ορίου 2 GiB (`114-121`). Το restore, όμως, αρνείται `backup.json` πάνω από 16 MiB (`587-588`, `703`). Με μέγιστο περίπου 2.000.000 χαρακτήρες στο document και συνολικά αντίστοιχο page OCR, πέντε έγγραφα κοντά στο νόμιμο ASCII όριο αρκούν για να ξεπεραστεί το restore manifest limit· με πολυ-byte ελληνικό UTF-8 χρειάζονται ακόμη λιγότερα.
- Το live σύστημα επιτρέπει έως 5.000 documents και έως 1.000 λογικές σελίδες **ανά εισαγόμενο document** (`FolderRepository.kt:87`, `97`, `439-440`). Το restore επιβάλλει 1.000 page descriptors και 1.000 λογικές σελίδες **σε ολόκληρο το backup** (`BackupService.kt:466-500`, `709`). Μία βιβλιοθήκη 1.001 μικρών μονόσελιδων documents είναι νόμιμη κατά τη χρήση αλλά μη επαναφέρσιμη.
- Κάθε confirmed ημερομηνία δημιουργεί τρεις reminders και δεν υπάρχει αντίστοιχο live global cap. Το restore δέχεται έως 5.000 reminders (`551`, `710`). Νόμιμη βιβλιοθήκη μπορεί να ξεπεράσει το όριο.
- Η εφαρμογή δέχεται οποιαδήποτε έγκυρη `LocalDate` χωρίς άνω όριο (`FolderRepository.kt:416-420`) και το instrumentation test αποδεικνύει κανονική ημερομηνία `2099-12-31` με reminders (`RepositoryImportDeleteExportTest.kt:89-119`). Το restore απορρίπτει `dueAt` μετά από περίπου 20 χρόνια από την τρέχουσα στιγμή (`BackupService.kt:557-561`, `711`).

**Γιατί είναι πρόβλημα.** Το backup είναι το δηλωμένο μέσο φορητής ανάκτησης. Αν το σύνολο καταστάσεων που μπορεί να παράγει η εφαρμογή δεν είναι υποσύνολο του συνόλου που μπορεί να επαναφέρει, η επιτυχής δημιουργία αρχείου δίνει ψευδή εγγύηση ασφάλειας.

**Πραγματικό αποτέλεσμα και συνθήκες.** Σε μεγάλη βιβλιοθήκη, πλούσιο OCR, πάνω από 1.000 συνολικές σελίδες/πηγές, πάνω από 5.000 reminders ή μακρινή νόμιμη ημερομηνία, ο χρήστης μπορεί να λάβει μήνυμα επιτυχούς backup και να ανακαλύψει την ασυμβατότητα μόνο μετά από απώλεια/αλλαγή συσκευής. Η δημιουργία μεγάλου manifest μπορεί επίσης να προκαλέσει OOM πριν καν εφαρμοστεί το byte limit, επειδή συνυπάρχουν entity strings, JSON graph, `String` και `ByteArray`.

**Στοιχεία και κάλυψη.** Τα υπάρχοντα tests χρησιμοποιούν μία μικρή fixture και δεν ασκούν συμμετρία στα παραπάνω όρια (`BackupRoundTripTest.kt:24-124`). Το `BackupSizePolicyTest` ελέγχει μόνο αριθμητικά archive/entry limits, όχι ότι κάθε create-accepted state είναι restore-accepted.

**Σχέσεις:** PF-AUD-006, PF-AUD-017.
**Βασική αιτία:** διαφορετικοί τοπικοί περιορισμοί create/restore χωρίς μία κοινή, εκτελέσιμη προδιαγραφή backup format και library cardinality.

### PF-AUD-002 - Η startup recovery αποτυγχάνει ανοικτά

**Κατηγορία:** επιβεβαιωμένο σφάλμα ακεραιότητας / state machine
**Σοβαρότητα:** κρίσιμη
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Η εφαρμογή ανοίγει το κανονικό operation gate ακόμη και αν η restore ή deletion recovery πέταξε εξαίρεση ή άφησε journal σε κατάσταση `PRESERVE_AND_RETRY`.

**Πού.** `PersonalFolderApp.onCreate` (`app/src/main/java/com/angel/personalfolder/PersonalFolderApp.kt:22-42`), `DataOperationCoordinator` (`app/src/main/java/com/angel/personalfolder/data/DataOperationCoordinator.kt:12-49`), `BackupService.recoverInterruptedRestore` (`BackupService.kt:30-105`) και `RestoreRecoveryPolicy` (`RestoreRecoveryPolicy.kt:31-63`).

**Μηχανισμός.** Το app καλεί `beginStartupRecovery`, καταπίνει οποιοδήποτε `Throwable` από τις δύο recoveries με ένα log που περιέχει μόνο το όνομα κλάσης, και στο `finally` καλεί πάντοτε `completeStartupRecovery`. Το `PRESERVE_AND_RETRY` συνειδητά αφήνει journal και πιθανώς αμφίσημη γενιά για επόμενη εκκίνηση, αλλά επιστρέφει κανονικά. Ο coordinator αναπαριστά μόνο «η recovery coroutine τελείωσε», όχι «η βιβλιοθήκη αποδείχθηκε συνεπής».

**Γιατί είναι πρόβλημα.** Μετά από interrupted restore, Room, live document root, previous root και staging root μπορεί να μην έχουν αποδειχθεί ως μία συνεπής γενιά. Νέα import, edit, OCR, delete, backup ή restore επιτρέπεται να μεταβάλει αυτή την αμφίσημη κατάσταση. Η επόμενη recovery πλέον εξετάζει state που δεν είναι το state αμέσως μετά τη διακοπή, άρα μπορεί να δυσκολέψει ή να καταστήσει αδύνατη την ασφαλή απόφαση.

**Πραγματικό αποτέλεσμα και συνθήκες.** Process death, I/O failure, ανεπαρκής χώρος, αποτυχία rename/delete, αλλοιωμένο journal ή fingerprint mismatch κατά restore/delete. Ο χρήστης δεν ενημερώνεται ότι το σύστημα βρίσκεται σε recovery-blocked κατάσταση και μπορεί να συνεχίσει να δουλεύει σε μικτή γενιά.

**Στοιχεία.** `PersonalFolderApp.kt:27-38` καταπίνει και απελευθερώνει. `RestoreRecoveryPolicy.kt:37-62` δείχνει ότι αρκετές αμφίσημες καταστάσεις καταλήγουν σκόπιμα σε `PRESERVE_AND_RETRY`. Δεν υπάρχει persistent `recoveryBlocked` state ούτε UI/operation check εκτός του ολοκληρωμένου deferred.

**Σχέσεις:** PF-AUD-009, PF-AUD-010.
**Βασική αιτία:** η ολοκλήρωση μιας προσπάθειας recovery εξισώνεται λανθασμένα με απόδειξη ασφαλούς λειτουργικής κατάστασης.

### PF-AUD-003 - Η συνέχεια τελικής υπογραφής δεν έχει αποδειχθεί

**Κατηγορία:** επιβεβαιωμένο release blocker, όχι runtime code defect
**Σοβαρότητα:** κρίσιμη σύμφωνα με την ίδια την πολιτική του έργου
**Βεβαιότητα:** απόλυτη

**Τι εντοπίστηκε.** Το source διατηρεί σωστά `applicationId com.angel.personalfolder`, `versionCode 3`, Room migrations και αναμενόμενο SHA-256 certificate fingerprint. Δεν υπάρχει, όμως, επιτυχές μόνιμα υπογεγραμμένο release artifact ή πραγματικό in-place upgrade test πάνω από την προηγούμενη εγκατεστημένη έκδοση.

**Πού.** `app/build.gradle.kts:83-156`, `.github/workflows/android.yml:99-149`, `scripts/prepare-signing.sh:1-32`, `docs/RELEASE_CONTINUITY.md:1-48`, `docs/TESTING_REPORT.md:52-60`.

**Στοιχεία.** Το ακριβές run #97 πέρασε unsigned `assembleRelease`, αλλά τα βήματα προετοιμασίας, build, signature verification και upload μόνιμου release ήταν `skipped`. Τα ίδια τα docs καταγράφουν ότι το main run #51 σταμάτησε επειδή έλειπαν και τα τέσσερα signing secrets και ότι δεν υπάρχει tag/release/signed artifact. Το debug variant έχει `applicationIdSuffix = ".ocrfix"` (`app/build.gradle.kts:152-155`) και δεν είναι update APK.

**Πραγματικό αποτέλεσμα.** Οποιοδήποτε τρέχον debug APK εγκαθίσταται ως ξεχωριστή εφαρμογή. Οποιοδήποτε release APK χωρίς το ίδιο certificate δεν μπορεί να εγκατασταθεί ως ενημέρωση και δεν αποδεικνύει διατήρηση της υπάρχουσας Keystore/Room/files κατάστασης.

**Συνθήκες.** Κάθε απόπειρα παράδοσης APK πριν αποκατασταθεί το μόνιμο signing material και περάσει πραγματική update verification.

**Σχέσεις:** PF-AUD-016, PF-AUD-017.
**Βασική αιτία:** κρίσιμη release identity εξαρτάται από μη διαθέσιμη εξωτερική κατάσταση, παρότι ο κώδικας σωστά αποτυγχάνει κλειστά.

### PF-AUD-004 - Multi-source document μπορεί να κοινοποιηθεί ελλιπές

**Κατηγορία:** επιβεβαιωμένο λειτουργικό σφάλμα
**Σοβαρότητα:** υψηλή
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Αν ένα logical document έχει περισσότερες από μία source rows και η πρώτη source/legacy metadata δηλώνει PDF, το `createSharePdf` αποκρυπτογραφεί μόνο την πρώτη πηγή και παραλείπει όλες τις υπόλοιπες.

**Πού.** `ExportService.createSharePdf` (`app/src/main/java/com/angel/personalfolder/data/ExportService.kt:94-130`) και import source representation (`FolderRepository.kt:52-115`).

**Μηχανισμός.** Το `singleOrNull()` αποτυγχάνει για multi-source list και ο κώδικας δημιουργεί fallback `DocumentPageEntity` από `DocumentEntity.encryptedPath`, που δείχνει στην πρώτη source. Αν αυτή είναι PDF, το branch `originalPdf != null` αντιγράφει byte-for-byte μόνο το συγκεκριμένο αρχείο. Το σχόλιο `mixed sources and multi-source documents need a derived PDF` (`ExportService.kt:120-122`) δεν συμφωνεί με την πραγματική επιλογή.

**Πραγματικό αποτέλεσμα.** Document από PDF+εικόνες, PDF+PDF ή πολλές πηγές με πρώτη PDF εμφανίζεται πλήρες στον εσωτερικό viewer, αλλά το εξωτερικό άνοιγμα/μοίρασμα περιέχει μόνο το πρώτο PDF. Η αποτυχία είναι σιωπηλή και μπορεί να οδηγήσει σε αποστολή ελλιπούς διοικητικού φακέλου.

**Συνθήκες.** Τουλάχιστον δύο source rows και πρώτη πηγή που αναγνωρίζεται ως PDF.

**Στοιχεία/κάλυψη.** Το `PdfOriginalPreservationTest` καλύπτει single-source PDF και ένα πολυσέλιδο PDF, όχι multi-source document με PDF πρώτο. Το README υπόσχεται ότι mixed documents παράγουν derived PDF (`README.md:20-30`).

**Σχέσεις:** PF-AUD-017.
**Βασική αιτία:** σύγχυση μεταξύ «ένα source PDF με πολλές εσωτερικές σελίδες» και «ένα logical document με πολλές source rows».

### PF-AUD-005 - Το βιομετρικό κλείδωμα δεν περιφρουρεί όλες τις ευαίσθητες λειτουργίες

**Κατηγορία:** πολύ πιθανό security/lifecycle πρόβλημα
**Σοβαρότητα:** υψηλή
**Βεβαιότητα:** υψηλή· χρειάζεται activity-lifecycle test σε συσκευή για πλήρη runtime απόδειξη

**Τι εντοπίστηκε.** Η οθόνη κλειδώνει στο `onStop`, αλλά αρκετά activity-result callbacks ξεκινούν import, backup, restore ή export χωρίς νέο έλεγχο `sessionUnlocked`.

**Πού.** `MainActivity.kt:36-106`, `160-173`, `331-358`. Αντίθετα, `openDocument/shareDocument` έχουν checks πριν και μετά το file creation (`239-278`).

**Μηχανισμός.** Το άνοιγμα SAF picker ή camera μετακινεί την activity εκτός foreground και μπορεί να ενεργοποιήσει `onStop`, που μηδενίζει `sessionUnlocked`. Όταν επιστρέψει αποτέλεσμα, οι callbacks στα `54-106` καλούν απευθείας το ViewModel. Δεν υπάρχει authorization check στο ViewModel, repository ή services. Το Compose `LockedScreen` εμποδίζει μόνο κανονική αλληλεπίδραση οθόνης, όχι ήδη οπλισμένες callbacks.

**Πραγματικό αποτέλεσμα.** Με ενεργό lock, μία ήδη ανοιγμένη διαδικασία export/backup/restore/import μπορεί να ολοκληρωθεί αφού η session έχει κλειδώσει. Σε σενάριο φυσικής πρόσβασης ενώ ο system picker είναι ανοιχτός, τρίτος μπορεί ενδεχομένως να επιλέξει προορισμό και να εξαγάγει επιλεγμένα προσωπικά έγγραφα χωρίς νέα ταυτοποίηση.

**Συνθήκες.** Ενεργό lock, προετοιμασμένη picker ενέργεια, app background/stop και επιστροφή activity result πριν ή παράλληλα με την επανεμφάνιση biometric prompt. Η ακριβής callback/onResume σειρά πρέπει να επιβεβαιωθεί σε υποστηριζόμενες Android/OEM εκδόσεις.

**Πρόσθετο όριο.** Τα Keystore document και pending-state keys έχουν `setUserAuthenticationRequired(false)` (`FileCrypto.kt:24-39`, `PendingActivityStateStore.kt:126-140`). Αυτό δεν είναι από μόνο του λάθος, αλλά αποδεικνύει ότι το lock είναι application-session policy, όχι cryptographic key gate. Άρα κάθε ευαίσθητη operation entry point πρέπει να εφαρμόζει την policy.

**Σχέσεις:** PF-AUD-012, PF-AUD-017.
**Βασική αιτία:** το authentication state ζει μόνο στη `MainActivity` και δεν αποτελεί capability που απαιτούν οι πραγματικές λειτουργίες.

### PF-AUD-006 - Οι προστασίες πόρων εφαρμόζονται αργά ή σε λάθος επίπεδο

**Κατηγορία:** επιβεβαιωμένη δομική αδυναμία διαθεσιμότητας
**Σοβαρότητα:** υψηλή
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Υπάρχουν αρκετά αριθμητικά limits, αλλά κρίσιμες διαδρομές φορτώνουν/παράγουν ολόκληρο το αντικείμενο πριν ελέγξουν το πραγματικό κόστος.

**Εκδηλώσεις με κοινή ρίζα.**

1. `validateEncryptedImage` κάνει bounds decode και μετά πλήρες `BitmapFactory.decodeFile` χωρίς `inSampleSize`, pixel/dimension cap ή allocation guard (`FolderRepository.kt:388-397`). Μικρό συμπιεσμένο αρχείο με τεράστιες διαστάσεις μπορεί να προκαλέσει OOM πριν φτάσει στο bounded OCR decoder.
2. Σε κάθε database open, `createSearchIndex` επιλέγει **όλα** τα documents μαζί με ολόκληρο το `ocrText` σε `List<Array<...>>`, ακόμη και όταν δεν χρειάζεται rebuild (`AppDatabase.kt:287-341`). Με 5.000 documents και έως 2.000.000 OCR χαρακτήρες ανά document, το θεωρητικό memory footprint είναι πολλαπλάσια μεγαλύτερο από Android heap.
3. Οι list/search DAOs επιστρέφουν `DocumentEntity` με ολόκληρο `ocrText` (`Daos.kt:13-48`, `Entities.kt:13-47`), άρα οι Compose λίστες μεταφέρουν το βαρύ payload για κάθε κάρτα παρότι δεν το εμφανίζουν.
4. Το backup χτίζει ολόκληρο object graph/string/byte array στη μνήμη πριν το archive limit· αυτό συνδέεται άμεσα με PF-AUD-001.
5. Το ZIP export γράφει ολόκληρο προσωρινό archive και ελέγχει τα 512 MiB μόνο μετά το close (`ExportService.kt:26-76`). Μπορεί να εξαντλήσει cache/disk πριν επιστρέψει «πολύ μεγάλο».

**Πραγματικό αποτέλεσμα.** OOM/process death κατά import ή startup, αργό/αποτυχημένο άνοιγμα μεγάλης βιβλιοθήκης, disk exhaustion κατά export/backup και recovery residue. Το OOM στο OCR επηρεάζει επίσης PF-AUD-007.

**Συνθήκες.** Μεγάλη ανάλυση εικόνας, αρκετά/μεγάλα OCR texts, μεγάλη επιλογή export ή βιβλιοθήκη κοντά στα δηλωμένα όρια. Δεν απαιτείται κακόβουλο input· αρκεί πραγματικό scan υψηλής ανάλυσης ή πολυετής χρήση.

**Σχέσεις:** PF-AUD-001, PF-AUD-007, PF-AUD-008.
**Βασική αιτία:** τα limits περιγράφουν file bytes ή τοπικές πράξεις, όχι peak heap, aggregate DB payload και streaming behavior ολόκληρης της ροής.

### PF-AUD-007 - Το OCR lifecycle δεν είναι cancellation-safe ούτε ατομικό

**Κατηγορία:** επιβεβαιωμένο σφάλμα lifecycle/κατάστασης
**Σοβαρότητα:** υψηλή
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Το document τίθεται `PROCESSING` πριν την εργασία, αλλά `CancellationException` και `OutOfMemoryError` επανεκτοξεύονται χωρίς επαναφορά state. Δεν υπάρχει startup reconciliation για stranded `PROCESSING` rows. Παράλληλα, το page OCR γράφεται source-by-source, ενώ document OCR/metadata γράφονται μόνο στο τέλος.

**Πού.** `DocumentProcessor.kt:25-99`, `OcrWorker.kt:11-34`, UI status/filter (`FolderApp.kt:357`, `610`, `630`).

**Πραγματικό αποτέλεσμα.** Με cancellation, process stop ή OOM, document μπορεί να παραμείνει μόνιμα «Γίνεται επεξεργασία…». Το UI δίνει retry για failed state, όχι σαφή recovery για stranded processing. Αν αποτύχει στη μέση, ορισμένα `document_pages.ocrText` έχουν νέο OCR ενώ `documents.ocrText`/metadata κρατούν παλαιά ή κενή κατάσταση. Αν το final document update πετύχει αλλά το reminder scheduling αποτύχει, το catch χαρακτηρίζει το OCR `FAILED` παρότι OCR και metadata έχουν ήδη αποθηκευτεί.

**Γιατί είναι πρόβλημα.** Ένα ενιαίο user-visible processing state καλύπτει τρεις διαφορετικές δεσμεύσεις: OCR bytes, document metadata και reminders. Δεν υπάρχει transaction boundary ή explicit stage marker.

**Συνθήκες.** WorkManager cancellation/replace, restore cancellation, low-memory kill/OOM, I/O/WorkManager failure μετά από μερικές σελίδες ή μετά το document update.

**Στοιχεία.** `DocumentProcessor.kt:61` γράφει ανά source, `83` γράφει document, `85` schedules reminders, ενώ `87-95` χειρίζεται όλα τα ordinary failures ως OCR failure και `88` αφήνει cancellation/OOM χωρίς state transition.

**Σχέσεις:** PF-AUD-006, PF-AUD-009, PF-AUD-012.
**Βασική αιτία:** η κατάσταση `processingState` είναι πολύ χονδρική και δεν υπάρχει idempotent stage/reconciliation model.

### PF-AUD-008 - Ο παγκόσμιος αποκλεισμός καλύπτει εργασίες απεριόριστης διάρκειας

**Κατηγορία:** αρχιτεκτονική αδυναμία απόδοσης/επεκτασιμότητας
**Σοβαρότητα:** υψηλή
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Το `DataOperationCoordinator` κρατά έναν process-wide `Mutex` σε όλη τη διάρκεια suspendable blocks. Το OCR, backup, restore, PDF/ZIP export, imports, deletes, edits και reminders μοιράζονται το ίδιο lock.

**Πού.** `DataOperationCoordinator.kt:6-49`, `DocumentProcessor.kt:26-99`, `BackupService.kt:107-165`, `ExportService.kt:26-130`, `FolderRepository.kt:52-373`.

**Επιβαρυντικοί μηχανισμοί.**

- `TesseractOcrEngine.recognize` δημιουργεί, κάνει `init` και `recycle` νέο `TessBaseAPI` για κάθε bitmap (`TesseractOcrEngine.kt:12-25`). Ένα PDF έως 1.000 σελίδες επαναφορτώνει τα γλωσσικά models ανά σελίδα.
- `DocumentRenderService.logicalPages` αποκρυπτογραφεί κάθε source για page counting και κάθε `renderPage` αποκρυπτογραφεί ξανά την ίδια source (`DocumentRenderService.kt:26-64`). Ένα πολυσέλιδο PDF σε unified export έχει μία αποκρυπτογράφηση για enumeration και μία ανά rendered page.
- Το σχόλιο του `DocumentProcessor` ότι πρέπει να ξαναδιαβάσει row επειδή «ο χρήστης μπορεί να επεξεργάστηκε metadata ενώ έτρεχε OCR» (`65-67`) αντιφάσκει με το ίδιο global lock: το metadata update περιμένει πίσω από το OCR.

**Πραγματικό αποτέλεσμα.** Ένα μεγάλο OCR ή export μπορεί να μπλοκάρει για μεγάλο διάστημα αποθήκευση metadata, delete, import, backup και άλλες library mutations. Το UI μπορεί να φαίνεται ενεργό αλλά οι ενέργειες να περιμένουν χωρίς σαφή ουρά/ακύρωση/πρόοδο. Η αρχιτεκτονική δεν κλιμακώνεται στα δηλωμένα 1.000 pages.

**Σχέσεις:** PF-AUD-006, PF-AUD-007, PF-AUD-012.
**Βασική αιτία:** η ανάγκη ακεραιότητας filesystem/Room λύθηκε με έναν υπερβολικά πλατύ critical section αντί με μικρές idempotent φάσεις και immutable snapshots.

### PF-AUD-009 - Reminder state και WorkManager state αποκλίνουν

**Κατηγορία:** επιβεβαιωμένη cross-system ασυνέπεια
**Σοβαρότητα:** υψηλή
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Η αντικατάσταση reminders ακυρώνει παλιό work, διαγράφει rows και κατόπιν εισάγει/schedules τρία reminders ένα-ένα, χωρίς Room transaction, compensation ή αναμονή των asynchronous `WorkManager.Operation` αποτελεσμάτων.

**Πού.** `ReminderScheduler.kt:14-95`, metadata/case updates (`FolderRepository.kt:125-200`, `255-305`), delete (`210-249`), `ReminderWorker.kt:18-54`.

**Πραγματικό αποτέλεσμα.** Ενδιάμεση αποτυχία μπορεί να αφήσει 0, 1 ή 2 rows, rows χωρίς ενεργό work, ή work που δεν αντιστοιχεί πλέον στο DB. Στο delete document/case, reminders αφαιρούνται πριν αποδειχθεί ότι η κύρια διαγραφή πέτυχε (`FolderRepository.kt:213-225`, `302-305`). Αν το quarantine rename ή DB delete αποτύχει, το document/case παραμένει αλλά οι υπενθυμίσεις έχουν χαθεί.

**Πρόσθετη πιθανή ασυνέπεια.** Μετά από επιτυχή notification, ο worker επιστρέφει success χωρίς να μαρκάρει το row done (`ReminderWorker.kt:45-53`). Κάθε `onResume` και app startup εκτελεί `rescheduleAll`, ακυρώνει/re-enqueues όλα τα pending rows και δίνει μηδενικό delay σε παρελθοντική due date (`MainActivity.kt:160-167`, `PersonalFolderApp.kt:40-42`, `ReminderScheduler.kt:70-90`). Έτσι το ίδιο reminder μπορεί να ειδοποιείται ξανά σε κάθε επαναφορά μέχρι να πατηθεί χειροκίνητα «ολοκληρώθηκε». Αυτό μπορεί να είναι επιθυμητή επίμονη σημασιολογία, αλλά δεν υπάρχει ρητή προδιαγραφή ή test.

**Συνθήκες.** WorkManager/storage failure, cancellation race, delete rollback ή συχνά resumes με past pending reminder.

**Σχέσεις:** PF-AUD-002, PF-AUD-007, PF-AUD-018.
**Βασική αιτία:** δύο durable συστήματα αντιμετωπίζονται σαν μία συναλλαγή χωρίς durable outbox/reconciliation protocol.

### PF-AUD-010 - Η migration 2→3 δεν είναι πλήρως fail-closed

**Κατηγορία:** επιβεβαιωμένο migration/data-preservation risk
**Σοβαρότητα:** υψηλή υπό την προϋπόθεση legacy corruption
**Βεβαιότητα:** υψηλή

**Τι εντοπίστηκε.** Τα σχόλια δηλώνουν ότι οποιοδήποτε legacy orphan πρέπει να σταματά τη migration πριν αλλοιωθούν δεδομένα. Η preflight `requireNoLegacyOrphans`, όμως, δεν ελέγχει άκυρο `checklist_items.linkedDocumentId` ούτε orphan `reminders.documentId/caseId`. Η migration μετατρέπει αυτά τα references σε `NULL` και συνεχίζει.

**Πού.** `AppDatabase.kt:42-48`, checklist copy `153-175`, reminder copy `181-202`, orphan checks `344-359`.

**Γιατί είναι πρόβλημα.** Το `CASE WHEN EXISTS ... THEN id ELSE NULL` διορθώνει referential integrity καταστρέφοντας την πληροφορία για τον αρχικό δεσμό. Αυτό αντιφάσκει με την τεκμηριωμένη πολιτική «φιλτράρισμα θα κατέστρεφε σιωπηλά δεδομένα» και με την release continuity απαίτηση πλήρους διατήρησης σχέσεων.

**Πραγματικό αποτέλεσμα.** Σε V1/V2 βάση με legacy orphan link, η αναβάθμιση πετυχαίνει αλλά checklist item ή reminder αποσυνδέεται σιωπηλά. Ο χρήστης βλέπει διαφορετική πληροφορία χωρίς recovery/export evidence για το χαμένο reference.

**Συνθήκες.** Απαιτεί ήδη ασυνεπή legacy βάση, άρα δεν είναι γενικό failure κάθε migration. Η σημασία είναι ότι ο μηχανισμός που υποτίθεται πως εντοπίζει ακριβώς αυτό το ενδεχόμενο είναι ελλιπής.

**Στοιχεία/κάλυψη.** Το `AppDatabaseMigrationTest` ελέγχει orphan document page και κανονικές σχέσεις, όχι orphan linked document/reminder reference.

**Σχέσεις:** PF-AUD-002, PF-AUD-017.
**Βασική αιτία:** η λίστα legacy invariants είναι χειροκίνητη και δεν καλύπτει όλα τα νέα foreign keys που δημιουργούνται.

### PF-AUD-011 - Επιτυχημένο import μπορεί να παρουσιαστεί ως αποτυχία

**Κατηγορία:** επιβεβαιωμένο λανθάνον cross-system bug
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** υψηλή

**Τι εντοπίστηκε.** Το import κάνει Room transaction, θέτει `databaseCommitted = true` και μετά καλεί `enqueueOcr`. Αν η enqueue κλήση πετάξει εξαίρεση, το catch δεν αφαιρεί πλέον files/DB - σωστά για αποφυγή data loss - αλλά επανεκτοξεύει την εξαίρεση. Το ViewModel εμφανίζει «Δεν ήταν δυνατή η εισαγωγή».

**Πού.** `FolderRepository.kt:101-122`, `366-373`, `FolderViewModel.kt:64-71`.

**Πραγματικό αποτέλεσμα.** Το document υπάρχει κανονικά στη βιβλιοθήκη, αλλά ο χρήστης πληροφορείται ότι δεν εισήχθη και μπορεί να το ξαναεισάγει. Το OCR μπορεί να μην έχει προγραμματιστεί, αφήνοντας queued item χωρίς work.

**Συνθήκες.** WorkManager initialization/enqueue failure μετά το DB commit. Δεν είναι συχνή ideal-path αποτυχία, αλλά ο κώδικας έχει σαφή post-commit failure window.

**Σχέσεις:** PF-AUD-007, PF-AUD-009.
**Βασική αιτία:** η επιτυχία import ορίζεται ταυτόχρονα ως durable commit και ως best-effort side-effect enqueue.

### PF-AUD-012 - ViewModel busy state και cleanup δεν αντέχουν επικάλυψη ή ακύρωση

**Κατηγορία:** επιβεβαιωμένο concurrency/lifecycle πρόβλημα
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Κάθε import/backup/restore/export coroutine γράφει ανεξάρτητα `_busy = true` και κατόπιν `_busy = false`. Δύο επικαλυπτόμενες εργασίες δεν μετρώνται. Η πρώτη που τελειώνει κάνει το UI «μη απασχολημένο» ενώ η δεύτερη συνεχίζει/περιμένει στον global mutex.

**Πού.** `FolderViewModel.kt:53-71`, `172-202`, `MainActivity.kt:54-106`, `331-358`.

**Πρόσθετο lifecycle bug.** Τα γενικά `runCatching` πιάνουν και `CancellationException`/`OutOfMemoryError`. Το `onFinished` του import βρίσκεται μετά το `runCatching`, όχι σε `finally`. Αν το `viewModelScope` ακυρωθεί κατά import, η coroutine μπορεί να σταματήσει πριν απελευθερώσει persistable URI permissions ή διαγράψει scanner temporary files. Τα errors επίσης μετατρέπονται σε snackbar failure αντί να διατηρούν structured operation stage.

**Πραγματικό αποτέλεσμα.** Λανθασμένα enabled controls/progress, διπλές ενέργειες, άδειες URI ή cache files που παραμένουν, και παραπλανητική error state κατά configuration/lifecycle cancellation.

**Σχέσεις:** PF-AUD-005, PF-AUD-007, PF-AUD-008.
**Βασική αιτία:** boolean activity indicator και cleanup callbacks αντί για operation registry/structured concurrency με `try/finally`.

### PF-AUD-013 - Το FTS repair δεν ελέγχει την ακρίβεια του index

**Κατηγορία:** επιβεβαιωμένη αδυναμία recovery
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** απόλυτη

**Τι εντοπίστηκε.** Το `FtsRepairPolicy.requiresRebuild` συγκρίνει μόνο count και σύνολο document IDs. Αν ένα FTS row έχει σωστό ID αλλά παρωχημένο ή λάθος title/OCR/provider/category/tags/protocol, ο repair μηχανισμός θεωρεί τον mirror σωστό.

**Πού.** `FtsRepairPolicy.kt:1-10`, `AppDatabase.kt:311-341`, `FtsRepairPolicyTest`.

**Πραγματικό αποτέλεσμα.** Μετά από partial corruption, απενεργοποιημένο/χαμένο trigger ή παλαιό schema defect, η αναζήτηση μπορεί να κρύβει έγγραφα ή να επιστρέφει λάθος αποτελέσματα επ' αόριστον, παρότι το startup δηλώνεται ως complete FTS repair.

**Συνθήκες.** Content-only divergence με ίδια cardinality και IDs. Οι κανονικοί triggers μειώνουν την πιθανότητα, αλλά ο repair κώδικας υπάρχει ακριβώς για μη κανονικές καταστάσεις.

**Στοιχεία.** Η ίδια η policy είναι μία expression `documentCount != ftsCount || documentIds != ftsIds`. Τα docs την περιγράφουν ως «full FTS mirror mismatch repair» (`docs/TESTING_REPORT.md:12`, `docs/REMEDIATION_HANDOFF_2026-08-12.md:88-93`), ισχυρισμός πλατύτερος από την υλοποίηση.

**Σχέσεις:** PF-AUD-006, PF-AUD-017.
**Βασική αιτία:** repair validation βασισμένο στην ταυτότητα rows, όχι σε content fingerprint ή canonical rebuild.

### PF-AUD-014 - Η επιλογή φορέα έχει ανεστραμμένους tie-breakers

**Κατηγορία:** επιβεβαιωμένο λογικό σφάλμα metadata extraction
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** πολύ υψηλή

**Τι εντοπίστηκε.** Ο provider candidate επιλέγεται με `maxWith(compareBy(score).thenByDescending(length).thenBy(index))`. Με `maxWith`, το descending length κάνει τη **μικρότερη** γραμμή να κερδίζει σε ισοβαθμία και το ascending index κάνει τη **μεταγενέστερη** γραμμή να κερδίζει. Επιπλέον, το `providerScore` επιστρέφει αμέσως score 1 αν η ίδια γραμμή περιέχει «Ελληνική Δημοκρατία», πριν εξετάσει αν περιέχει επίσης «Υπουργείο» ή «Διεύθυνση».

**Πού.** `MetadataExtractor.kt:75-85`, `182-190`.

**Πραγματικό αποτέλεσμα.** Σε ισόβαθμες authority lines επιλέγεται συχνά πιο σύντομη/χαμηλότερη γραμμή αντί για συγκεκριμένο header. Μία ενιαία OCR γραμμή «Ελληνική Δημοκρατία - Υπουργείο Παιδείας» βαθμολογείται ως generic 1, άρα μπορεί να χάσει ή να αποθηκευτεί ως low-confidence generic provider.

**Συνθήκες.** OCR που συγχωνεύει headers στην ίδια γραμμή ή πολλαπλές issuer markers ίδιου score. Τα υπάρχοντα tests χρησιμοποιούν «Ελληνική Δημοκρατία» και «Υπουργείο Παιδείας» σε διαφορετικές γραμμές (`MetadataExtractorTest.kt:112-134`) και δεν αποκαλύπτουν τις δύο συνθήκες.

**Σχέσεις:** PF-AUD-017.
**Βασική αιτία:** comparator semantics και heuristic control flow δεν επαληθεύτηκαν με adversarial/tie cases.

### PF-AUD-015 - Το UI κρατά ή επαναφέρει λάθος κατάσταση σε κρίσιμες στιγμές

**Κατηγορία:** επιβεβαιωμένα τοπικά UI/state defects
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** πολύ υψηλή

**Ενοποιημένα συμπτώματα.**

- **Sticky viewer error:** μετά από αποτυχία rendering μιας σελίδας, το success path επόμενης σελίδας δεν μηδενίζει `error`. Το `when` προτιμά για πάντα την error οθόνη μέχρι κλείσιμο (`DocumentViewerScreen.kt:59-77`, `97-112`).
- **Απώλεια μη αποθηκευμένων edits:** τα fields του `DocumentEditDialog` είναι keyed με `document.updatedAt` (`FolderApp.kt:801-814`). Το OCR αλλάζει `updatedAt` στην έναρξη και στο τέλος. Recomposition ενώ ο διάλογος είναι ανοιχτός επαναφέρει τις τιμές από DB και μπορεί να σβήσει ό,τι πληκτρολογεί ο χρήστης.
- **Κενή case detail μετά delete:** η διαγραφή case δεν καθαρίζει `selectedCaseId`. Όταν το case εξαφανιστεί από Flow, το detail branch παραμένει ενεργό αλλά δεν αποδίδει περιεχόμενο (`FolderApp.kt:157-200`, `553-559`).
- **Restore ζητά άσκοπη διπλή πληκτρολόγηση:** το ίδιο `PasswordDialog` απαιτεί password/confirmation τόσο στο create όσο και στο restore (`FolderApp.kt:233-242`, `922-943`). Δεν προκαλεί data error, αλλά αυξάνει την πιθανότητα αποτυχίας πρόσβασης σε κρίσιμη ανάκτηση.

**Πραγματικό αποτέλεσμα.** Αδυναμία συνέχισης viewer μετά από τοπική page failure, απώλεια πληκτρολόγησης, blank screen μετά delete και περιττή τριβή στο restore.

**Σχέσεις:** PF-AUD-007, PF-AUD-012, PF-AUD-017.
**Βασική αιτία:** Compose state keys συνδέονται με mutable persistence version χωρίς ξεχωριστό draft/operation lifecycle.

### PF-AUD-016 - Build, dependency και schema provenance δεν είναι πλήρως αναπαραγώγιμα

**Κατηγορία:** δομική/εφοδιαστική αδυναμία
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** απόλυτη

**Τι εντοπίστηκε.**

- Δεν υπάρχουν tracked `gradlew`, wrapper JAR/properties. Το CI επιλέγει Gradle 8.11.1 εξωτερικά (`android.yml:25-29`). Ένας developer/μελλοντικός runner δεν έχει repository-pinned launcher.
- `exportSchema = true` και KAPT γράφει στο `app/schemas`, αλλά κανένα Room schema JSON δεν είναι tracked. Το schema υπάρχει μόνο ως προσωρινό CI artifact (`AppDatabase.kt:10-23`, `app/build.gradle.kts:189-194`, workflow `151-157`). Η migration history δεν είναι durable/reviewable μαζί με τον κώδικα.
- Clean build χρειάζεται δίκτυο για να κατεβάσει Tesseract models από GitHub (`app/build.gradle.kts:32-80`). Το commit και Git blob checksum είναι σωστά pinned, αλλά offline/restricted build αποτυγχάνει. Τα tracked παλιά model files αγνοούνται από το source set (`118-123`).
- Δεν υπάρχουν dependency lockfiles, Gradle verification metadata ή SBOM. Τα GitHub Actions χρησιμοποιούν mutable major tags (`actions/*@v4`, emulator runner `@v2`) και όχι immutable action commit SHAs (`android.yml:16-31`, `48-56`, `81-95`, `143-157`).
- Τα docs δηλώνουν debug suffix `.debug`, ενώ το build χρησιμοποιεί `.ocrfix` (`README.md:57`, `app/build.gradle.kts:152-155`).

**Πραγματικό αποτέλεσμα.** Δυσκολότερη πιστή αναπαραγωγή, migration review και supply-chain investigation. Η επιτυχία του σημερινού CI δεν εγγυάται ότι το ίδιο source θα επιλυθεί ακριβώς ίδιο στο μέλλον.

**Σχέσεις:** PF-AUD-003, PF-AUD-017, PF-AUD-020.
**Βασική αιτία:** κρίσιμα build/release artifacts και resolved provenance παραμένουν εκτός του versioned repository.

### PF-AUD-017 - Η κάλυψη είναι ουσιαστική αλλά στενότερη από τους ισχυρισμούς

**Κατηγορία:** test/verification gap
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** πολύ υψηλή

**Τι επιβεβαιώνεται.** Τα 43 unit tests και 17 instrumentation tests είναι πραγματικά και το run #97 περνά. Καλύπτουν metadata policies, recovery decisions, migration happy path/orphan page, μικρό backup round trip/corruption, PDF bitmap, single-source PDF preservation, repository import/delete/export, reminders, pending picker state και synthetic Greek/English OCR.

**Τι δεν καλύπτεται.**

- κανένα Compose interaction/activity lifecycle test,
- καμία callback-after-lock ή biometric/device-credential ακολουθία,
- κανένα multi-source document με πρώτη πηγή PDF,
- κανένα create→restore boundary test για 16 MiB manifest, >1.000 total pages, >5.000 reminders ή ημερομηνία >20 ετών,
- κανένα cancellation/OOM/stranded `PROCESSING` reconciliation test,
- κανένα fault injection με process kill ανά restore/delete phase,
- κανένα content-only FTS corruption test,
- κανένα πραγματικό corpus από σαρωμένες ελληνικές διοικητικές πράξεις, λοξές/σκοτεινές φωτογραφίες, μεγάλα ή malformed PDFs,
- καμία φυσική συσκευή, OEM SAF provider, reboot/timezone/battery behavior ή accessibility tree.

Το current OCR instrumentation test αποδεικνύει ότι τα models φορτώνονται και αναγνωρίζουν μεγάλο synthetic rendered ελληνικό/αγγλικό κείμενο. Δεν αποδεικνύει αξιόπιστη αναγνώριση πραγματικών εγγράφων.

**Τεκμηριωτική ασυνέπεια.** `docs/TESTING_REPORT.md` και `PF_VERIFICATION_MATRIX.md` συνεχίζουν να χαρακτηρίζουν ως «latest/final» το run #89 στο commit `8fdca9...` με 16 tests (`TESTING_REPORT.md:34-46`, matrix `42-53`). Το audited head έχει run #97 και 17 tests. Τα docs επίσης αποκαλούν το FTS repair «complete/full», κάτι που διαψεύδει PF-AUD-013.

**Πραγματικό αποτέλεσμα.** Τα green gates αποδεικνύουν compilation και ένα σημαντικό regression subset, όχι συνολική production correctness. Τα σοβαρότερα ευρήματα επιβίωσαν ακριβώς επειδή βρίσκονται μεταξύ των τοπικών test boundaries.

**Σχέσεις:** όλα τα κρίσιμα/υψηλά ευρήματα.
**Βασική αιτία:** tests οργανωμένα γύρω από ήδη γνωστές διορθώσεις και happy-path fixtures, όχι γύρω από συστηματικό invariant/fault/state-space matrix.

### PF-AUD-018 - Τρεις λειτουργικές περιοχές δεν έχουν επαρκώς καθορισμένη τελική σημασιολογία

**Κατηγορία:** απαιτεί περαιτέρω επιβεβαίωση ή product specification
**Σοβαρότητα:** μεσαία
**Βεβαιότητα:** μεσαία-υψηλή ανά υποπερίπτωση

**Reminder lifecycle.** Δεν έχει αποφασιστεί αν completed cases κρατούν reminders (`FolderRepository.kt:291-310`, `PF_VERIFICATION_MATRIX.md:26`, `55-59`) ούτε αν επιτυχώς εμφανισμένη notification πρέπει να επαναλαμβάνεται μέχρι manual completion. Η σημερινή συμπεριφορά δεν μπορεί να χαρακτηριστεί από μόνη της bug χωρίς εγκεκριμένη σημασιολογία.

**Scanner crop.** Το `findPaperBounds` αναζητά pixels με luminance ≥150 και παίρνει το bounding rectangle τους (`ScannerImageProcessor.kt:83-119`). Σε σκοτεινό έγγραφο, φωτογραφία χωρίς λευκό χαρτί ή σελίδα με φωτεινά εσωτερικά στοιχεία, μπορεί να περικόψει πραγματικό περιεχόμενο. Η επεξεργασμένη JPEG γίνεται η εισαγόμενη πηγή· δεν υπάρχει UI crop confirmation/undo ούτε corpus test. Πρόκειται για τεχνικά εύλογο κίνδυνο, όχι αποδεδειγμένη απώλεια σε συγκεκριμένο πραγματικό δείγμα.

**Checklist/reminder UX.** Υπάρχουν ViewModel/repository λειτουργίες για relink/delete checklist item, αλλά καμία UI κλήση (`FolderViewModel.kt:139-147`, `FolderRepository.kt:353-360`, `FolderApp.kt:529-533`, `673-681`). Οι reminders εμφανίζονται μόνο οι πρώτες τρεις και το section header έχει κενό callback (`FolderApp.kt:306-310`), άρα δεν υπάρχει πλήρης λίστα/διαχείριση όταν είναι περισσότερες. Η README περιγράφει checklist και reminders σαν ολοκληρωμένες δυνατότητες (`README.md:26-30`), αλλά η πραγματική διαχείριση είναι μερική.

**Πραγματικό αποτέλεσμα.** Απρόβλεπτη reminder εμπειρία, πιθανό destructive crop και αδυναμία διόρθωσης/διαγραφής checklist ή πρόσβασης σε όλες τις reminders.

**Σχέσεις:** PF-AUD-009, PF-AUD-015, PF-AUD-017.
**Βασική αιτία:** capability existence στο data layer αντιμετωπίζεται ως ολοκληρωμένο product behavior χωρίς πλήρη UI/lifecycle contract.

### PF-AUD-019 - Η ανάκτηση προσωρινών plaintext αρχείων έχει χαμηλής πιθανότητας κενά

**Κατηγορία:** privacy/cleanup risk
**Σοβαρότητα:** χαμηλή
**Βεβαιότητα:** υψηλή για τον μηχανισμό, χαμηλότερη για πρακτική εκμετάλλευση

**Τι εντοπίστηκε.** Το startup cleaner διαγράφει share plaintext μετά από 7 ημέρες και άλλα temp μετά από 15 λεπτά με τον όρο `now - lastModified > maxAge` (`TempFileCleaner.kt:8-36`). Αν το wall clock γυρίσει προς τα πίσω, η ηλικία γίνεται αρνητική και η διαγραφή αναβάλλεται. Restore staging directories εξαιρούνται σωστά ως journal-owned, αλλά staging που δημιουργήθηκε πριν γραφτεί journal και έμεινε από process death δεν έχει άλλο orphan cleanup (`BackupService.kt:202-216`, `277-286`, `382-386`).

**Πραγματικό αποτέλεσμα.** App-private plaintext share/viewer/export cache ή orphan encrypted staging μπορεί να παραμείνει περισσότερο από το δηλωμένο. Απαιτεί clock anomaly ή process death σε στενό pre-journal window. Το Android sandbox περιορίζει τρίτα apps, αλλά privileged/forensic reader μπορεί να δει τα plaintext temp, όπως αναγνωρίζουν τα security docs για άλλα app-private δεδομένα.

**Σχέσεις:** PF-AUD-006.
**Βασική αιτία:** cleanup βασισμένο αποκλειστικά σε wall-clock age και journal ownership χωρίς monotonic/session inventory.

### PF-AUD-020 - Η δομή παρουσίασης και τα repository κατάλοιπα αυξάνουν το κόστος ασφαλών αλλαγών

**Κατηγορία:** ποιότητα/συντηρησιμότητα
**Σοβαρότητα:** χαμηλή
**Βεβαιότητα:** απόλυτη

**Τι εντοπίστηκε.** Το `FolderApp.kt` είναι 944 γραμμές και συγκεντρώνει navigation, screens, dialogs, business presentation rules και πολλά μονογραμμικά composables. Μεγάλο μέρος του ελληνικού UI είναι hardcoded αντί για resources, παρότι υπάρχει `strings.xml`. Υπάρχουν διπλές overloads/αδρανείς ViewModel operations και δύο tracked παλιά `.traineddata` αρχεία που το build σχολιάζει ρητά ως broken και αγνοεί (`app/build.gradle.kts:118-123`). Τα docs testing/handoff έχουν παρωχημένη «τελική» κατάσταση.

**Πραγματικό αποτέλεσμα.** Μεγαλύτερο blast radius σε Compose αλλαγές, δύσκολη localization/accessibility συνέπεια, σύγχυση για το πραγματικό OCR asset source και αυξημένη πιθανότητα να ενημερωθεί μία από πολλές επαναλαμβανόμενες UI συμβάσεις αλλά όχι οι υπόλοιπες.

**Συνθήκες.** Κυρίως μελλοντικές αλλαγές, onboarding νέου συντηρητή και debugging UI regressions.

**Σχέσεις:** PF-AUD-015, PF-AUD-016, PF-AUD-017.
**Βασική αιτία:** γρήγορη συσσώρευση λειτουργιών στο ίδιο presentation file και ατελής αφαίρεση παλιών implementation artifacts.

## 7. Βασικές αιτίες και αλληλεξαρτήσεις

| Ρίζα | Τι επηρεάζει | Γιατί πρέπει να αντιμετωπιστεί νωρίς |
|---|---|---|
| R1 - Δεν υπάρχει ένα ενιαίο library-generation contract | PF-AUD-001, 002, 007, 009, 010, 011 | Room, files και WorkManager ολοκληρώνουν διαφορετικές στιγμές. Τοπικά rollback δεν αποδεικνύει συνολική συνέπεια. |
| R2 - Create/restore και live/backup limits ορίζονται χωριστά | PF-AUD-001, 006 | Όσο παραμένουν διαφορετικές σταθερές/επίπεδα, νέες δυνατότητες μπορούν ξανά να δημιουργούν μη επαναφέρσιμη κατάσταση. |
| R3 - Authentication ως UI flag, όχι operation capability | PF-AUD-005, 012 | Κάθε νέο callback/service entry point μπορεί να παρακάμπτει άθελά του το lock. |
| R4 - Βαριά entities και καθυστερημένο streaming | PF-AUD-001, 006, 008 | Επιβαρύνει startup, lists, backup, export και OCR ταυτόχρονα· τοπικό optimization δεν λύνει το aggregate peak. |
| R5 - Ένας παγκόσμιος mutex αντί για explicit stages | PF-AUD-007, 008, 009, 012 | Προσφέρει απλή ακεραιότητα στον happy path αλλά δημιουργεί head-of-line blocking και ασαφή cancellation state. |
| R6 - Verification γύρω από γνωστά fixes, όχι invariants | PF-AUD-004, 005, 010, 013, 014, 015, 017, 018 | Τα tests περνούν ενώ cross-component αντιφάσεις παραμένουν. |
| R7 - Build/release provenance εκτός version control | PF-AUD-003, 016, 020 | Εμποδίζει repeatable build, schema audit και ασφαλή update handoff. |

Η σημαντικότερη αρχή για την επόμενη φάση είναι να μη διορθωθεί κάθε σύμπτωμα με ανεξάρτητο `if`. Η αποκατάσταση πρέπει να ξεκινήσει από R1/R2: τι θεωρείται μία δεσμευμένη γενιά βιβλιοθήκης και ποια ακριβώς κατάσταση είναι εγγυημένα exportable/restorable.

## 8. Σημεία που βρέθηκαν σωστά ή με καλή βάση

Για να μη δημιουργηθεί παραπλανητικά αρνητική εικόνα, τα παρακάτω στηρίζονται πραγματικά στον κώδικα:

- Τα document bytes κρυπτογραφούνται με Android Keystore AES-GCM, authenticated format header και atomic temporary write (`FileCrypto.kt`).
- Η απώλεια υπάρχοντος document key αποτυγχάνει κλειστά και δεν δημιουργεί αυθαίρετα νέο key πάνω από παλιό ciphertext (`FileCrypto.kt:49-60`).
- Δεν υπάρχει `INTERNET` permission, backend, analytics ή tracker στο manifest/source.
- Το FileProvider είναι `exported=false`, χρησιμοποιεί προσωρινά URI grants και το app ενεργοποιεί `FLAG_SECURE`.
- Οι notifications χρησιμοποιούν generic περιεχόμενο και private visibility, χωρίς τίτλο εγγράφου/υπόθεσης.
- Το backup χρησιμοποιεί AES-GCM και PBKDF2-HMAC-SHA256, καθαρίζει password char arrays και απορρίπτει traversal, duplicate entries/IDs, λανθασμένα references και oversized entries. Το PBKDF2 δεν είναι memory-hard, και τα docs σωστά δεν το ισχυρίζονται.
- Οι Room migrations είναι ρητές, χωρίς destructive fallback. Η γενική πρόθεση fail-closed και η χρήση migration tests είναι σωστή, παρότι το PF-AUD-010 δείχνει κενό.
- Ο restore σχεδιασμός με staging, previous generation, durable journal και Room/filesystem fingerprints είναι σοβαρή βάση. Το κρίσιμο κενό είναι η συμπεριφορά όταν η απόδειξη αποτυγχάνει, όχι η απουσία recovery model.
- Το PDF renderer παράγει λευκό opaque background και ο single-source external PDF path διατηρεί original bytes. Τα σχετικά tests πέρασαν.
- Τα Tesseract build models είναι pinned σε συγκεκριμένο upstream commit, μήκος και Git blob SHA-1· ο clean build δεν εμπιστεύεται απλώς ένα mutable URL.
- Η expiry extraction πλέον δεν κατασκευάζει αυθαίρετη λήξη από την τελευταία ημερομηνία. Confidence/provenance και manual ownership υπάρχουν πραγματικά και έχουν tests.
- Το signing workflow αποτυγχάνει κλειστά, ελέγχει συγκεκριμένο certificate fingerprint και δεν παρουσιάζει unsigned release ως τελικό. Το πρόβλημα είναι ότι δεν έχει ακόμη ολοκληρωθεί επιτυχώς.

## 9. Ασφάλεια και όρια απειλών

### Trust boundaries

1. **Εξωτερικά URI/PDF/εικόνες → app process.** MIME δεν θεωρείται πλήρης απόδειξη· γίνεται αποκρυπτογράφηση/decoder validation, αλλά το unbounded import bitmap παραμένει availability surface.
2. **App-private Room → app UI/search.** Το Room/FTS περιέχει plaintext OCR και metadata. Είναι ρητό, τεκμηριωμένο όριο και όχι κρυφή υπόσχεση πλήρους κρυπτογράφησης.
3. **Keystore/file tree → plaintext cache/export.** Οι exports είναι σκόπιμα plaintext και η UI το δηλώνει. Το share cache retention παραμένει bounded-by-policy, όχι άμεση ανάκληση grant/bytes.
4. **MainActivity authenticated session → services/workers.** Αυτό είναι το ασθενέστερο security boundary, επειδή services/workers δεν λαμβάνουν authentication capability.
5. **Backup password/archive → restore parser/filesystem swap.** Η cryptographic envelope είναι authenticated, αλλά μετά το decrypt το semantic contract έχει τις ασυμμετρίες του PF-AUD-001.
6. **Repository/CI → release APK.** Source identity και expected certificate είναι γνωστά, αλλά το private signing state και το resolved build environment βρίσκονται εκτός repository.

### Δεν χαρακτηρίζονται ως αποδεδειγμένα vulnerabilities

- Η plaintext Room/FTS αποθήκευση είναι γνωστό boundary. Απαιτεί app sandbox bypass, root/privileged forensic access ή compromised process.
- Το `safeJournalFile` επιτρέπει ως candidate και τον ίδιο τον `cacheDir` (`BackupService.kt:630-635`). Tampered app-private journal θα μπορούσε να διευρύνει cleanup, αλλά ο κανονικός writer δεν παράγει τέτοιο path. Καταγράφεται ως hardening candidate, όχι επιβεβαιωμένο exploit.
- Τα notification IDs βασίζονται σε `UUID.hashCode` και θεωρητικά συγκρούονται. Δεν υπάρχει ένδειξη πραγματικής σύγκρουσης στο ελεγχόμενο state· ο κίνδυνος είναι χαμηλός και δεν διογκώθηκε σε ξεχωριστό εύρημα.
- Δεν αποδόθηκε CVE σε dependency χωρίς resolved scan evidence.

## 10. Προτεραιοποιημένο σχέδιο για ξεχωριστή φάση διορθώσεων

Η παρακάτω σειρά είναι διάγνωση προτεραιότητας, όχι εφαρμογή αλλαγών.

### Κύμα 0 - Release/data freeze

- Μην παρουσιαστεί νέο APK ως συμβατή έκδοση μέχρι να λυθεί PF-AUD-003.
- Μην αντιμετωπίζεται υπάρχον backup ως επαρκώς επαληθευμένο μέσο ανάκτησης μέχρι να δοκιμαστεί το PF-AUD-001 με πραγματική βιβλιοθήκη.

### Κύμα 1 - Ακεραιότητα και ανάκτηση

- Κοινό create/restore format contract και συμμετρικά aggregate limits.
- Recovery-blocked durable state και απαγόρευση normal mutations όταν generation proof λείπει.
- Σαφή idempotent stages για import/OCR/delete/restore/reminders.
- Συμπλήρωση migration orphan invariants.

### Κύμα 2 - Security και lifecycle

- Κεντρικό authorization gate για κάθε ευαίσθητη operation/callback.
- Structured cancellation και cleanup σε ViewModel/activity result flows.
- Reminder outbox/reconciliation και ρητή product απόφαση για delivery/completion semantics.

### Κύμα 3 - Απόδοση και κλίμακα

- Lightweight projections για lists/FTS checks.
- Streaming manifest/export και preflight resource budgets.
- Reuse Tesseract session ανά document/job και decrypt/render ανά source αντί ανά page.
- Αντικατάσταση long global critical sections με μικρότερα snapshot/commit phases.

### Κύμα 4 - Verification και release engineering

- Boundary/fault/lifecycle test matrix για όλα τα ευρήματα.
- Πραγματικό ελληνικό document corpus με privacy-safe fixtures.
- Tracked Gradle wrapper, Room schemas, dependency verification/lock/SBOM.
- Physical-device update test με την προηγούμενη εγκατεστημένη έκδοση και το μόνιμο certificate.

## 11. Κριτήρια για να αλλάξει η τελική διάγνωση

Η εφαρμογή μπορεί να χαρακτηριστεί αξιόπιστα έτοιμη μόνο όταν υπάρχουν επαναλήψιμα στοιχεία ότι:

1. κάθε state που δέχεται το live app μπορεί να γίνει backup και restore, στα ίδια όρια,
2. process death σε κάθε restore/delete/OCR phase οδηγεί είτε σε αποδεδειγμένη ανάκτηση είτε σε user-visible blocked state χωρίς νέες mutations,
3. κανένα ευαίσθητο callback/export/restore δεν εκτελείται μετά το relock χωρίς νέα ταυτοποίηση,
4. βιβλιοθήκη κοντά στα υποστηριζόμενα όρια ανοίγει, αναζητά, εξάγει και κάνει backup χωρίς OOM/disk overrun,
5. οι ροές multi-source PDF, OCR cancellation και reminder failure έχουν end-to-end regression tests,
6. το ίδιο signed release APK εγκαθίσταται ως update πάνω από την προηγούμενη πραγματική εφαρμογή και διατηρεί Room, Keystore και αρχεία,
7. τα docs ταυτίζονται με το τρέχον commit/run και δεν αποκαλούν source audit ή happy-path test «πλήρη runtime επαλήθευση».

## 12. Τελικό συμπέρασμα

Το Personal Folder έχει εξελιχθεί σε πραγματικό προϊόν με αρκετές σωστές τεχνικές βάσεις. Η μεγαλύτερη παγίδα είναι ότι αυτές οι βάσεις δημιουργούν εύλογη αίσθηση ασφάλειας ενώ μερικά από τα πιο κρίσιμα συμβόλαια διασταυρώνονται μόνο μερικώς. Το σύστημα είναι ισχυρότερο τοπικά απ' ό,τι συνολικά: η κρυπτογράφηση, οι migrations, ο PDF renderer ή ένα passing test μπορεί να είναι σωστά, αλλά η αλληλουχία Room-filesystem-WorkManager-UI παραμένει ευάλωτη σε failure windows και διαφορετικά όρια.

Η σωστή επόμενη φάση δεν είναι γενικό «καθάρισμα κώδικα». Είναι ελεγχόμενη αποκατάσταση των invariants με πρώτη προτεραιότητα backup/recovery/release identity, έπειτα lifecycle/security και τέλος κλίμακα/συντηρησιμότητα. Μέχρι να κλείσουν τα τρία κρίσιμα ευρήματα, η εφαρμογή μπορεί να χρησιμοποιείται μόνο με επίγνωση ότι η επιτυχής καθημερινή λειτουργία και το πράσινο CI δεν αποτελούν ακόμη απόδειξη ασφαλούς ανάκτησης ή συμβατής τελικής έκδοσης.

---

**Καμία διόρθωση κώδικα δεν εφαρμόστηκε στο πλαίσιο αυτού του ελέγχου.**
