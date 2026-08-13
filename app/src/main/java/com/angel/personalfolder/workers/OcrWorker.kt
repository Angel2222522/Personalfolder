package com.angel.personalfolder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.DataOperationCoordinator
import com.angel.personalfolder.processing.DocumentProcessor
import java.io.IOException

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = OcrExecutionGate.runSerial {
        val id = inputData.getString(KEY_DOCUMENT_ID) ?: return@runSerial Result.failure()
        val database = AppDatabase.get(applicationContext)
        val result = DocumentProcessor(applicationContext, database).process(id)
        when {
            result.isSuccess -> Result.success()
            result.exceptionOrNull() is IOException -> {
                DataOperationCoordinator.withExclusive {
                    database.documentDao().getById(id)?.let { current ->
                        database.documentDao().update(
                            OcrRetryStatePolicy.queued(current, System.currentTimeMillis())
                        )
                    }
                }
                Result.retry()
            }
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        suspend fun awaitIdle() = OcrExecutionGate.awaitIdle()
    }
}
