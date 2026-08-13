package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.processing.DocumentProcessor
import java.io.IOException

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = OcrExecutionGate.runSerial {
        val id = inputData.getString(KEY_DOCUMENT_ID) ?: return@runSerial Result.failure()
        val result = DocumentProcessor(applicationContext, AppDatabase.get(applicationContext)).process(id)
        when {
            result.isSuccess -> Result.success()
            result.exceptionOrNull() is IOException -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        suspend fun awaitIdle() = OcrExecutionGate.awaitIdle()
    }
}
