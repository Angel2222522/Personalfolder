package com.angel.personalfolder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.BackupService
import com.angel.personalfolder.data.DataOperationCoordinator
import com.angel.personalfolder.data.DocumentDeletionRecovery
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.security.TempFileCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PersonalFolderApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        DataOperationCoordinator.beginStartupRecovery()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                try {
                    BackupService(this@PersonalFolderApp).recoverInterruptedRestore()
                } catch (error: Throwable) {
                    android.util.Log.w("PersonalFolder", "Restore recovery was not completed: ${error::class.java.simpleName}")
                }
                try {
                    DocumentDeletionRecovery.recover(this@PersonalFolderApp, database)
                } catch (error: Throwable) {
                    android.util.Log.w("PersonalFolder", "Document deletion recovery was not completed: ${error::class.java.simpleName}")
                }
            } finally {
                DataOperationCoordinator.completeStartupRecovery()
            }
            TempFileCleaner.recover(this@PersonalFolderApp)
            ReminderScheduler.rescheduleAll(this@PersonalFolderApp)
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
