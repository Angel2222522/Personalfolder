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
    }
}
