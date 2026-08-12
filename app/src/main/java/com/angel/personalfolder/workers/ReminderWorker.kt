package com.angel.personalfolder.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.personalfolder.MainActivity
import com.angel.personalfolder.R
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.ReminderDeliveryPolicy

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val reminder = AppDatabase.get(applicationContext).reminderDao().getById(id)
            ?: return Result.success()
        if (reminder.isDone) return Result.success()
        val notificationPermissionGranted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (ReminderDeliveryPolicy.shouldKeepPending(notificationPermissionGranted)) {
            // Keep the row pending. WorkManager will retry with backoff and
            // the app also reschedules all pending rows when permission is
            // granted or the activity resumes.
            return Result.retry()
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, id.hashCode(), Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, "document_reminders")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            // Reminder titles may contain names of documents, authorities or cases.
            // Keep notification surfaces generic even when the app is currently unlocked.
            .setContentText("Έχεις μια υπενθύμιση για έγγραφο ή υπόθεση.")
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        return Result.success()
    }

    companion object { const val KEY_REMINDER_ID = "reminder_id" }
}
