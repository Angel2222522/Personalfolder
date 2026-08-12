package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSizePolicyTest {
    @Test
    fun supportsTheDocumentLimitWithoutUsingDifferentRestoreLimit() {
        BackupSizePolicy.requireEntrySize(BackupSizePolicy.MAX_ENTRY_BYTES)
        BackupSizePolicy.requirePayloadSize(BackupSizePolicy.MAX_PAYLOAD_BYTES)
        assertEquals(BackupSizePolicy.MAX_PAYLOAD_BYTES + 32L * 1024 * 1024, BackupSizePolicy.MAX_ARCHIVE_BYTES)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEntryAboveLimit() {
        BackupSizePolicy.requireEntrySize(BackupSizePolicy.MAX_ENTRY_BYTES + 1)
    }
}
