package com.angel.personalfolder.data

/**
 * Limits that describe the largest state this application is willing to
 * create and therefore the largest state its portable backup accepts.
 *
 * These values are deliberately shared by import, OCR, backup and restore.
 * Keeping a separate set of "live" and "restore" limits was the source of
 * a data-integrity bug: a successful backup could contain a state that the
 * same application later rejected.
 */
object LibraryLimits {
    const val MAX_DOCUMENTS = 5_000
    const val MAX_DOCUMENT_SOURCES = 100
    const val MAX_LOGICAL_PAGES_PER_DOCUMENT = 1_000
    const val MAX_TOTAL_LOGICAL_PAGES = 10_000

    const val MAX_CASES = 5_000
    const val MAX_TIMELINE_EVENTS = 20_000
    const val MAX_CHECKLIST_ITEMS = 20_000
    const val MAX_REMINDERS = 20_000

    const val MAX_DOCUMENT_OCR_CHARS = 2_000_000
    const val MAX_TOTAL_OCR_CHARS = 16_000_000
    const val MAX_METADATA_JSON_CHARS = 200_000
    const val MAX_TOTAL_METADATA_JSON_CHARS = 4_000_000

    // These limits are shared by live edits and the backup parser. A backup
    // must never shorten a value that the application itself can persist.
    const val MAX_DOCUMENT_TITLE_CHARS = 200
    const val MAX_DOCUMENT_FILE_NAME_CHARS = 200
    const val MAX_MIME_TYPE_CHARS = 120
    const val MAX_DOCUMENT_CATEGORY_CHARS = 120
    const val MAX_DOCUMENT_TAGS_CHARS = 500
    const val MAX_DOCUMENT_PROVIDER_CHARS = 200
    const val MAX_PROTOCOL_NUMBER_CHARS = 200
    const val MAX_PROCESSING_ERROR_CHARS = 300
    const val MAX_CONFIDENCE_CHARS = 20
    const val MAX_CASE_TITLE_CHARS = 200
    const val MAX_CASE_DESCRIPTION_CHARS = 2_000
    const val MAX_CASE_STATUS_CHARS = 80
    const val MAX_CASE_NEXT_STEP_CHARS = 500
    const val MAX_CASE_NOTES_CHARS = 5_000
    const val MAX_EVENT_TITLE_CHARS = 300
    const val MAX_EVENT_NOTE_CHARS = 5_000
    const val MAX_EVENT_TYPE_CHARS = 50
    const val MAX_DATE_CHARS = 10
    const val MAX_CHECKLIST_TITLE_CHARS = 500
    const val MAX_REMINDER_TITLE_CHARS = 500

    /** The manifest is bounded before it is parsed into an in-memory JSON tree. */
    const val MAX_BACKUP_MANIFEST_BYTES = 64L * 1024 * 1024
    const val MAX_BACKUP_ENTRY_COUNT = 100_000

    fun requireDocumentCount(count: Int) {
        require(count in 0..MAX_DOCUMENTS) { "Η βιβλιοθήκη περιέχει υπερβολικά πολλά έγγραφα." }
    }

    fun requireSourceCount(count: Int) {
        require(count in 0..MAX_DOCUMENT_SOURCES) { "Το έγγραφο περιέχει υπερβολικά πολλές πηγές." }
    }

    fun requireLogicalPagesPerDocument(count: Int) {
        require(count in 0..MAX_LOGICAL_PAGES_PER_DOCUMENT) {
            "Το έγγραφο περιέχει υπερβολικά πολλές λογικές σελίδες."
        }
    }

    fun requireTotalLogicalPages(count: Long) {
        require(count in 0L..MAX_TOTAL_LOGICAL_PAGES.toLong()) {
            "Η βιβλιοθήκη περιέχει υπερβολικά πολλές λογικές σελίδες."
        }
    }

    fun requireTableCounts(
        documents: Int,
        pages: Int,
        cases: Int,
        events: Int,
        checklist: Int,
        reminders: Int
    ) {
        requireDocumentCount(documents)
        require(pages in 0..MAX_TOTAL_LOGICAL_PAGES) { "Το αντίγραφο περιέχει υπερβολικά πολλές πηγές." }
        require(cases in 0..MAX_CASES) { "Υπάρχουν υπερβολικά πολλές υποθέσεις." }
        require(events in 0..MAX_TIMELINE_EVENTS) { "Υπάρχουν υπερβολικά πολλά γεγονότα." }
        require(checklist in 0..MAX_CHECKLIST_ITEMS) { "Υπάρχουν υπερβολικά πολλά στοιχεία λίστας." }
        require(reminders in 0..MAX_REMINDERS) { "Υπάρχουν υπερβολικές υπενθυμίσεις." }
    }

    fun requireTotalOcrChars(count: Long) {
        require(count in 0L..MAX_TOTAL_OCR_CHARS.toLong()) {
            "Το συνολικό αναγνωρισμένο κείμενο είναι υπερβολικά μεγάλο."
        }
    }

    fun requireTotalMetadataJsonChars(count: Long) {
        require(count in 0L..MAX_TOTAL_METADATA_JSON_CHARS.toLong()) {
            "Τα συνολικά μεταδεδομένα είναι υπερβολικά μεγάλα."
        }
    }

    fun requireText(value: String?, max: Int, message: String): String {
        val checked = value.orEmpty()
        require(checked.length <= max) { message }
        return checked
    }
}
