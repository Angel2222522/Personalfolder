package com.angel.personalfolder.data

import android.content.Context
import androidx.work.Data
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

    suspend fun scheduleForDocument(context: Context, documentId: String, title: String, expiry: String) {
        val date = runCatching { LocalDate.parse(expiry) }.getOrNull() ?: return
        val dao = AppDatabase.get(context).reminderDao()
        leadDays.forEach { lead ->
            val due = date.minusDays(lead.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val reminder = ReminderEntity(UUID.randomUUID().toString(), "Λήξη: $title", due, documentId, leadDays = lead)
            dao.insert(reminder)
            val delay = (due - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminder.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("reminder_${reminder.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
