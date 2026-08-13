package com.angel.personalfolder.data

/** Shared size contract for backup creation and restore validation. */
object BackupSizePolicy {
    const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
    const val MAX_PAYLOAD_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = MAX_PAYLOAD_BYTES + 32L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = LibraryLimits.MAX_BACKUP_MANIFEST_BYTES
    const val MAX_ARCHIVE_ENTRIES = LibraryLimits.MAX_BACKUP_ENTRY_COUNT

    fun requireEntrySize(size: Long) {
        require(size in 0L..MAX_ENTRY_BYTES) { "Ένα αρχείο του αντιγράφου είναι υπερβολικά μεγάλο." }
    }

    fun requirePayloadSize(size: Long) {
        require(size in 0L..MAX_PAYLOAD_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
    }

    fun requireArchiveSize(size: Long) {
        require(size in 1L..MAX_ARCHIVE_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
    }

    fun requireManifestSize(size: Long) {
        require(size in 1L..MAX_MANIFEST_BYTES) { "Το ευρετήριο του αντιγράφου είναι υπερβολικά μεγάλο." }
    }

    fun requireLibraryState(
        documents: Int,
        pages: Int,
        cases: Int,
        events: Int,
        checklist: Int,
        reminders: Int,
        totalOcrChars: Long,
        totalMetadataJsonChars: Long = 0L
    ) {
        LibraryLimits.requireTableCounts(documents, pages, cases, events, checklist, reminders)
        LibraryLimits.requireTotalOcrChars(totalOcrChars)
        LibraryLimits.requireTotalMetadataJsonChars(totalMetadataJsonChars)
    }

    fun requireDocumentShapes(documents: List<DocumentEntity>, pages: List<DocumentPageEntity>) {
        val pagesByDocument = pages.groupingBy { it.documentId }.eachCount()
        val documentIds = documents.mapTo(hashSetOf()) { it.id }
        documents.forEach { document ->
            LibraryLimits.requireLogicalPagesPerDocument(document.pageCount)
            LibraryLimits.requireSourceCount(pagesByDocument[document.id] ?: 0)
            require(document.ocrText.length <= LibraryLimits.MAX_DOCUMENT_OCR_CHARS) {
                "Το OCR ενός εγγράφου είναι υπερβολικά μεγάλο."
            }
            require(document.extractedMetadataJson.length <= LibraryLimits.MAX_METADATA_JSON_CHARS) {
                "Τα μεταδεδομένα ενός εγγράφου είναι υπερβολικά μεγάλα."
            }
        }
        require(pages.all { it.documentId in documentIds }) {
            "Το αντίγραφο περιέχει σελίδα άγνωστου εγγράφου."
        }
        pages.forEach { page ->
            require(page.ocrText.length <= LibraryLimits.MAX_DOCUMENT_OCR_CHARS) {
                "Το OCR μιας πηγής είναι υπερβολικά μεγάλο."
            }
        }
    }

    fun requireTextShapes(
        documents: List<DocumentEntity>,
        pages: List<DocumentPageEntity>,
        cases: List<CaseEntity>,
        events: List<TimelineEventEntity>,
        checklist: List<ChecklistItemEntity>,
        reminders: List<ReminderEntity>
    ) {
        documents.forEach { document ->
            LibraryLimits.requireText(document.title, LibraryLimits.MAX_DOCUMENT_TITLE_CHARS, "Ο τίτλος εγγράφου είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(document.originalFileName, LibraryLimits.MAX_DOCUMENT_FILE_NAME_CHARS, "Το όνομα αρχείου είναι υπερβολικά μεγάλο.")
            LibraryLimits.requireText(document.mimeType, LibraryLimits.MAX_MIME_TYPE_CHARS, "Ο τύπος αρχείου είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(document.category, LibraryLimits.MAX_DOCUMENT_CATEGORY_CHARS, "Η κατηγορία είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(document.tags, LibraryLimits.MAX_DOCUMENT_TAGS_CHARS, "Οι ετικέτες είναι υπερβολικά μεγάλες.")
            LibraryLimits.requireText(document.provider, LibraryLimits.MAX_DOCUMENT_PROVIDER_CHARS, "Ο φορέας είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(document.issuedDate, LibraryLimits.MAX_DATE_CHARS, "Η ημερομηνία έκδοσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(document.expiryDate, LibraryLimits.MAX_DATE_CHARS, "Η ημερομηνία λήξης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(document.protocolNumber, LibraryLimits.MAX_PROTOCOL_NUMBER_CHARS, "Ο αριθμός πρωτοκόλλου είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(document.processingError, LibraryLimits.MAX_PROCESSING_ERROR_CHARS, "Το σφάλμα επεξεργασίας είναι υπερβολικά μεγάλο.")
            LibraryLimits.requireText(document.expiryDateSuggestion, LibraryLimits.MAX_DATE_CHARS, "Η προτεινόμενη ημερομηνία είναι υπερβολικά μεγάλη.")
            listOf(
                document.expiryDateSuggestionConfidence,
                document.titleConfidence,
                document.categoryConfidence,
                document.providerConfidence,
                document.issuedDateConfidence,
                document.expiryDateConfidence,
                document.protocolNumberConfidence
            ).forEach { LibraryLimits.requireText(it, LibraryLimits.MAX_CONFIDENCE_CHARS, "Η ένδειξη βεβαιότητας είναι υπερβολικά μεγάλη.") }
        }
        pages.forEach { page ->
            LibraryLimits.requireText(page.sourceFileName, LibraryLimits.MAX_DOCUMENT_FILE_NAME_CHARS, "Το όνομα πηγής είναι υπερβολικά μεγάλο.")
            LibraryLimits.requireText(page.mimeType, LibraryLimits.MAX_MIME_TYPE_CHARS, "Ο τύπος πηγής είναι υπερβολικά μεγάλος.")
        }
        cases.forEach { item ->
            LibraryLimits.requireText(item.title, LibraryLimits.MAX_CASE_TITLE_CHARS, "Ο τίτλος υπόθεσης είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(item.description, LibraryLimits.MAX_CASE_DESCRIPTION_CHARS, "Η περιγραφή υπόθεσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(item.status, LibraryLimits.MAX_CASE_STATUS_CHARS, "Η κατάσταση υπόθεσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(item.startDate, LibraryLimits.MAX_DATE_CHARS, "Η ημερομηνία έναρξης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(item.deadline, LibraryLimits.MAX_DATE_CHARS, "Η προθεσμία υπόθεσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(item.nextStep, LibraryLimits.MAX_CASE_NEXT_STEP_CHARS, "Το επόμενο βήμα είναι υπερβολικά μεγάλο.")
            LibraryLimits.requireText(item.notes, LibraryLimits.MAX_CASE_NOTES_CHARS, "Οι σημειώσεις υπόθεσης είναι υπερβολικά μεγάλες.")
        }
        events.forEach { item ->
            LibraryLimits.requireText(item.title, LibraryLimits.MAX_EVENT_TITLE_CHARS, "Ο τίτλος γεγονότος είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(item.note, LibraryLimits.MAX_EVENT_NOTE_CHARS, "Η σημείωση γεγονότος είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(item.eventType, LibraryLimits.MAX_EVENT_TYPE_CHARS, "Ο τύπος γεγονότος είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(item.eventDate, LibraryLimits.MAX_DATE_CHARS, "Η ημερομηνία γεγονότος είναι υπερβολικά μεγάλη.")
        }
        checklist.forEach { item ->
            LibraryLimits.requireText(item.title, LibraryLimits.MAX_CHECKLIST_TITLE_CHARS, "Ο τίτλος checklist είναι υπερβολικά μεγάλος.")
        }
        reminders.forEach { item ->
            LibraryLimits.requireText(item.title, LibraryLimits.MAX_REMINDER_TITLE_CHARS, "Ο τίτλος υπενθύμισης είναι υπερβολικά μεγάλος.")
        }
    }
}
