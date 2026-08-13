package com.angel.personalfolder.data

/** Shared size contract for backup creation and restore validation. */
object BackupSizePolicy {
    const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
    const val MAX_PAYLOAD_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = MAX_PAYLOAD_BYTES + 32L * 1024 * 1024

    fun requireEntrySize(size: Long) {
        require(size in 0L..MAX_ENTRY_BYTES) { "Ένα αρχείο του αντιγράφου είναι υπερβολικά μεγάλο." }
    }

    fun requirePayloadSize(size: Long) {
        require(size in 0L..MAX_PAYLOAD_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
    }

    fun requireArchiveSize(size: Long) {
        require(size in 1L..MAX_ARCHIVE_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
    }
}
