package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.processing.DocumentProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        activeWorkers.incrementAndGet()
        idle.value = false
        return try {
            val id = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
            val result = DocumentProcessor(applicationContext, AppDatabase.get(applicationContext)).process(id)
            when {
                result.isSuccess -> Result.success()
                result.exceptionOrNull() is IOException -> Result.retry()
                else -> Result.failure()
            }
        } finally {
            if (activeWorkers.decrementAndGet() == 0) idle.value = true
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        private val activeWorkers = AtomicInteger(0)
        private val idle = MutableStateFlow(true)

        suspend fun awaitIdle() = idle.first { it }
    }
}
