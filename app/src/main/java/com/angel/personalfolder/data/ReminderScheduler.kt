package com.angel.personalfolder.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.angel.personalfolder.workers.ReminderWorker
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private val leadDays = listOf(30, 7, 0)
    const val WORK_TAG = "document-reminders"

    suspend fun replaceForDocument(context: Context, documentId: String, title: String, expiry: String?) {
        val date = expiry?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val database = AppDatabase.get(context)
        val replacements = date?.let { validDate -> leadDays.map { lead ->
            ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = "Λήξη: ${title.ifBlank { "Έγγραφο" }}",
                dueAt = ReminderDatePolicy.dueAt(validDate, lead),
                documentId = documentId,
                leadDays = lead,
                deadlineAt = ReminderDatePolicy.deadlineAt(validDate)
            )
        } } ?: emptyList()
        val old = withDatabaseCommit(database) {
            val dao = reminderDao()
            val old = dao.getForDocument(documentId)
            require(dao.count() - old.size + replacements.size <= LibraryLimits.MAX_REMINDERS) {
                "Η βιβλιοθήκη έχει φτάσει το όριο υπενθυμίσεων."
            }
            withTransaction {
                dao.deleteForDocument(documentId)
                dao.insertAll(replacements)
            }
            old
        }
        old.forEach { runCatching { cancel(context, it.id) } }
        replacements.forEach { runCatching { scheduleWork(context, it) } }
    }

    suspend fun replaceForCase(context: Context, caseId: String, title: String, deadline: String?) {
        val date = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val database = AppDatabase.get(context)
        val replacements = date?.let { validDate -> leadDays.map { lead ->
            ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = "Προθεσμία υπόθεσης: ${title.ifBlank { "Υπόθεση" }}",
                dueAt = ReminderDatePolicy.dueAt(validDate, lead),
                caseId = caseId,
                leadDays = lead,
                deadlineAt = ReminderDatePolicy.deadlineAt(validDate)
            )
        } } ?: emptyList()
        val old = withDatabaseCommit(database) {
            val dao = reminderDao()
            val old = dao.getForCase(caseId)
            require(dao.count() - old.size + replacements.size <= LibraryLimits.MAX_REMINDERS) {
                "Η βιβλιοθήκη έχει φτάσει το όριο υπενθυμίσεων."
            }
            withTransaction {
                dao.deleteForCase(caseId)
                dao.insertAll(replacements)
            }
            old
        }
        old.forEach { runCatching { cancel(context, it.id) } }
        replacements.forEach { runCatching { scheduleWork(context, it) } }
    }

    suspend fun removeForDocument(context: Context, documentId: String) {
        val database = AppDatabase.get(context)
        val old = withDatabaseCommit(database) {
            val dao = reminderDao()
            val old = dao.getForDocument(documentId)
            dao.deleteForDocument(documentId)
            old
        }
        old.forEach { runCatching { cancel(context, it.id) } }
    }

    suspend fun removeForCase(context: Context, caseId: String) {
        val database = AppDatabase.get(context)
        val old = withDatabaseCommit(database) {
            val dao = reminderDao()
            val old = dao.getForCase(caseId)
            dao.deleteForCase(caseId)
            old
        }
        old.forEach { runCatching { cancel(context, it.id) } }
    }

    suspend fun rescheduleAll(context: Context) {
        val database = AppDatabase.get(context)
        val reminders = withDatabaseCommit(database) {
            reminderDao().getAll().filterNot { it.isDone }
        }
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        reminders.forEach { runCatching { scheduleWork(context, it) } }
    }

    private suspend fun <T> withDatabaseCommit(database: AppDatabase, block: suspend AppDatabase.() -> T): T {
        return if (DataOperationCoordinator.recoveryState.value == DataOperationCoordinator.StartupRecoveryState.IN_PROGRESS) {
            DataOperationCoordinator.withExclusiveDuringStartup { database.block() }
        } else {
            DataOperationCoordinator.withExclusive { database.block() }
        }
    }

    private fun scheduleWork(context: Context, reminder: ReminderEntity) {
        if (reminder.isDone) return
        // A past trigger is still a pending reminder. Scheduling it with zero
        // delay lets WorkManager deliver it after a missed run or permission
        // recovery instead of silently dropping it.
        val delay = ReminderDeliveryPolicy.initialDelayMs(reminder.dueAt, System.currentTimeMillis())
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
