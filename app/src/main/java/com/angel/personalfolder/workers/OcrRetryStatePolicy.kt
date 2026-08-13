package com.angel.personalfolder.workers

import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.ProcessingState

/**
 * Keeps a retrying OCR document visibly in the queue instead of presenting a
 * transient I/O retry as a final failure while WorkManager is backing off.
 */
internal object OcrRetryStatePolicy {
    fun queued(document: DocumentEntity, now: Long): DocumentEntity = document.copy(
        processingState = ProcessingState.QUEUED,
        processingError = null,
        updatedAt = now
    )
}
