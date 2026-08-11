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
    const val WORK_TAG = "document-reminders"

    suspend fun replaceForDocument(context: Context, documentId: String, title: String, expiry: String?) {
        val dao = AppDatabase.get(context).reminderDao()
        dao.getForDocument(documentId).forEach { cancel(context, it.id) }
        dao.deleteForDocument(documentId)
        val date = expiry?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        leadDays.forEach { lead ->
            val reminder = ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = "Λήξη: ${title.ifBlank { "Έγγραφο" }}",
                dueAt = date.minusDays(lead.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                documentId = documentId,
                leadDays = lead
            )
            dao.insert(reminder)
            scheduleWork(context, reminder)
        }
    }

    suspend fun replaceForCase(context: Context, caseId: String, title: String, deadline: String?) {
        val dao = AppDatabase.get(context).reminderDao()
        dao.getForCase(caseId).forEach { cancel(context, it.id) }
        dao.deleteForCase(caseId)
        val date = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        leadDays.forEach { lead ->
            val reminder = ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = "Προθεσμία υπόθεσης: ${title.ifBlank { "Υπόθεση" }}",
                dueAt = date.minusDays(lead.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                caseId = caseId,
                leadDays = lead
            )
            dao.insert(reminder)
            scheduleWork(context, reminder)
        }
    }

    suspend fun removeForDocument(context: Context, documentId: String) {
        val dao = AppDatabase.get(context).reminderDao()
        dao.getForDocument(documentId).forEach { cancel(context, it.id) }
        dao.deleteForDocument(documentId)
    }

    suspend fun removeForCase(context: Context, caseId: String) {
        val dao = AppDatabase.get(context).reminderDao()
        dao.getForCase(caseId).forEach { cancel(context, it.id) }
        dao.deleteForCase(caseId)
    }

    suspend fun rescheduleAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(WORK_TAG)
        AppDatabase.get(context).reminderDao().getAll()
            .filterNot { it.isDone }
            .forEach { scheduleWork(context, it) }
    }

    private fun scheduleWork(context: Context, reminder: ReminderEntity) {
        if (reminder.isDone || reminder.dueAt <= System.currentTimeMillis()) return
        val delay = reminder.dueAt - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminder.id))
            .addTag(WORK_TAG)
            .addTag("$WORK_TAG:${reminder.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder_${reminder.id}", ExistingWorkPolicy.REPLACE, request)
    }

    private fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$id")
    }
}
