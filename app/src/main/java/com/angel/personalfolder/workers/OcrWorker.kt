package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.processing.DocumentProcessor
import kotlinx.coroutines.sync.Mutex
import java.io.IOException

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        processingLock.lock()
        return try {
            val id = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
            val result = DocumentProcessor(applicationContext, AppDatabase.get(applicationContext)).process(id)
            when {
                result.isSuccess -> Result.success()
                result.exceptionOrNull() is IOException -> Result.retry()
                else -> Result.failure()
            }
        } finally {
            processingLock.unlock()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        private val processingLock = Mutex()
        suspend fun awaitIdle() {
            processingLock.lock()
            processingLock.unlock()
        }
    }
}
