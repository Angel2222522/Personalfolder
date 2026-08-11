package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.processing.DocumentProcessor

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        val result = DocumentProcessor(applicationContext, AppDatabase.get(applicationContext)).process(id)
        return if (result.isSuccess) Result.success() else Result.failure()
    }

    companion object { const val KEY_DOCUMENT_ID = "document_id" }
}
