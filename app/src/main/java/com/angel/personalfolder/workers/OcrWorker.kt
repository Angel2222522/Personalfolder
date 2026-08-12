package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.processing.DocumentProcessor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return withProcessingLock {
            val id = inputData.getString(KEY_DOCUMENT_ID) ?: return@withProcessingLock Result.failure()
            val result = DocumentProcessor(applicationContext, AppDatabase.get(applicationContext)).process(id)
            when {
                result.isSuccess -> Result.success()
                result.exceptionOrNull() is IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        private val processingLock = Mutex()

        fun enqueue(context: Context, documentId: String) {
            val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<OcrWorker>()
                .setInputData(workDataOf(KEY_DOCUMENT_ID to documentId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag("document-processing")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ocr_$documentId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        suspend fun <T> withProcessingLock(block: suspend () -> T): T = processingLock.withLock { block() }
    }
}
