package com.angel.personalfolder.data

import android.content.Context
import androidx.room.withTransaction
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
        replace(
            context = context,
            documentId = documentId,
            caseId = null,
            title = "Λήξη: ${title.ifBlank { "Έγγραφο" }}",
            dateText = expiry
        )
    }

    suspend fun replaceForCase(context: Context, caseId: String, title: String, deadline: String?) {
        replace(
            context = context,
            documentId = null,
            caseId = caseId,
            title = "Προθεσμία υπόθεσης: ${title.ifBlank { "Υπόθεση" }}",
            dateText = deadline
        )
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

    private suspend fun replace(
        context: Context,
        documentId: String?,
        caseId: String?,
        title: String,
        dateText: String?
    ) {
        val database = AppDatabase.get(context)
        val dao = database.reminderDao()
        val existing = when {
            documentId != null -> dao.getForDocument(documentId)
            caseId != null -> dao.getForCase(caseId)
            else -> emptyList()
        }
        val date = dateText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val replacements = date?.let {
            leadDays.map { lead ->
                ReminderEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    dueAt = it.minusDays(lead.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    documentId = documentId,
                    caseId = caseId,
                    leadDays = lead
                )
            }
        }.orEmpty()

        database.withTransaction {
            when {
                documentId != null -> dao.deleteForDocument(documentId)
                caseId != null -> dao.deleteForCase(caseId)
            }
            dao.insertAll(replacements)
        }

        existing.forEach { cancel(context, it.id) }
        replacements.forEach { scheduleWork(context, it) }
    }

    private fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$id")
    }
}
