package com.angel.personalfolder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.BackupService
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.security.TempFileCleaner
import com.angel.personalfolder.workers.OcrRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PersonalFolderApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { BackupService(this@PersonalFolderApp).recoverInterruptedRestore() }
                .onFailure { error ->
                    android.util.Log.w(
                        "PersonalFolder",
                        "Restore recovery was not completed: ${error::class.java.simpleName}"
                    )
                }
            runCatching { TempFileCleaner.recover(this@PersonalFolderApp) }
                .onFailure { error ->
                    android.util.Log.w(
                        "PersonalFolder",
                        "Temporary-file recovery was not completed: ${error::class.java.simpleName}"
                    )
                }
            runCatching { OcrRecovery.recover(this@PersonalFolderApp) }
                .onFailure { error ->
                    android.util.Log.w(
                        "PersonalFolder",
                        "OCR recovery was not completed: ${error::class.java.simpleName}"
                    )
                }
            runCatching { ReminderScheduler.rescheduleAll(this@PersonalFolderApp) }
                .onFailure { error ->
                    android.util.Log.w(
                        "PersonalFolder",
                        "Reminder rescheduling was not completed: ${error::class.java.simpleName}"
                    )
                }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "document_reminders",
                getString(com.angel.personalfolder.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(com.angel.personalfolder.R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
