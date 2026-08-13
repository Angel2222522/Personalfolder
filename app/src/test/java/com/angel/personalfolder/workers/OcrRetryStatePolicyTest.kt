package com.angel.personalfolder.workers

import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.ProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrRetryStatePolicyTest {
    @Test
    fun retryReturnsFailedDocumentToQueuedWithoutTouchingItsMetadata() {
        val original = DocumentEntity(
            id = "synthetic-document",
            title = "Synthetic title",
            originalFileName = "synthetic.pdf",
            mimeType = "application/pdf",
            encryptedPath = "/private/synthetic.pf",
            pageCount = 1,
            category = "Άλλα",
            provider = "Synthetic provider",
            protocolNumber = "SYN-123",
            processingState = ProcessingState.FAILED,
            processingError = "temporary I/O failure",
            createdAt = 10L,
            updatedAt = 20L
        )

        val queued = OcrRetryStatePolicy.queued(original, 30L)

        assertEquals(ProcessingState.QUEUED, queued.processingState)
        assertNull(queued.processingError)
        assertEquals(30L, queued.updatedAt)
        assertEquals(original.title, queued.title)
        assertEquals(original.category, queued.category)
        assertEquals(original.provider, queued.provider)
        assertEquals(original.protocolNumber, queued.protocolNumber)
        assertEquals(original.ocrText, queued.ocrText)
    }
}
