package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.LibraryOperationCoordinator
import com.angel.personalfolder.data.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Requeues OCR rows left without an active worker after process death or stop. */
object OcrRecovery {
    suspend fun recover(context: Context) = withContext(Dispatchers.IO) {
        LibraryOperationCoordinator.withExclusive {
            val database = AppDatabase.get(context)
            val workManager = WorkManager.getInstance(context)
            val candidates = database.documentDao().getByProcessingState(ProcessingState.PROCESSING) +
                database.documentDao().getByProcessingState(ProcessingState.QUEUED)

            candidates.forEach { document ->
                val workInfos = runCatching {
                    workManager.getWorkInfosForUniqueWork("ocr_${document.id}")
                        .get(5, TimeUnit.SECONDS)
                }.getOrElse { error ->
                    android.util.Log.w(
                        "PersonalFolder",
                        "Δεν ελέγχθηκε η κατάσταση OCR: ${error::class.java.simpleName}"
                    )
                    return@forEach
                }
                val active = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
                }
                if (active) return@forEach

                if (document.processingState == ProcessingState.PROCESSING) {
                    val requeued = database.documentDao().updateProcessingStateIfCurrent(
                        id = document.id,
                        expectedState = ProcessingState.PROCESSING,
                        state = ProcessingState.QUEUED,
                        error = "Η προηγούμενη επεξεργασία διακόπηκε και προγραμματίστηκε ξανά.",
                        updatedAt = System.currentTimeMillis()
                    )
                    if (requeued != 1) return@forEach
                }
                OcrWorker.enqueue(context, document.id)
            }
        }
    }
}
