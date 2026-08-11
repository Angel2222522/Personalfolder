package com.angel.personalfolder.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.angel.personalfolder.workers.ReminderWorker
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private val leadDays = listOf(30, 7, 0)

    suspend fun replaceForDocument(context: Context, documentId: String, title: String, expiry: String?) {
        val dao = AppDatabase.get(context).reminderDao()
        dao.deleteForDocument(documentId)
        val date = expiry?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        leadDays.forEach { lead -> schedule(context, ReminderEntity(UUID.randomUUID().toString(), "Λήξη: $title", date.minusDays(lead.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), documentId, leadDays = lead)) }
    }

    suspend fun rescheduleAll(context: Context) {
        AppDatabase.get(context).reminderDao().getAll().forEach { schedule(context, it) }
    }

    private suspend fun schedule(context: Context, reminder: ReminderEntity) {
        AppDatabase.get(context).reminderDao().insert(reminder)
        val delay = (reminder.dueAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminder.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder_${reminder.id}", ExistingWorkPolicy.REPLACE, request)
    }
}
