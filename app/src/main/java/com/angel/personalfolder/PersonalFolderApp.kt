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
import com.angel.personalfolder.data.FolderRepository
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.processing.DocumentProcessor
import com.angel.personalfolder.security.StartupRecoveryStateStore
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
            var succeeded = false
            var failureMessage: String? = null
            try {
                StartupRecoveryStateStore.markInProgress(this@PersonalFolderApp)
                BackupService(this@PersonalFolderApp).recoverInterruptedRestore()
                DocumentDeletionRecovery.recover(this@PersonalFolderApp, database)
                DocumentProcessor.reconcileInterruptedProcessing(database)
                FolderRepository(this@PersonalFolderApp).reconcileQueuedOcr()
                runCatching { TempFileCleaner.recover(this@PersonalFolderApp) }
                    .onFailure { android.util.Log.w("PersonalFolder", "Temporary-file cleanup was deferred", it) }
                runCatching { ReminderScheduler.rescheduleAll(this@PersonalFolderApp) }
                    .onFailure { android.util.Log.w("PersonalFolder", "Reminder rescheduling was deferred", it) }
                StartupRecoveryStateStore.markSafe(this@PersonalFolderApp)
                succeeded = true
            } catch (error: Throwable) {
                failureMessage = error.message?.take(500) ?: "Η ανάκτηση της βιβλιοθήκης απέτυχε."
                android.util.Log.e("PersonalFolder", "Startup recovery blocked normal operations", error)
                runCatching { StartupRecoveryStateStore.markBlocked(this@PersonalFolderApp, failureMessage!!) }
            } finally {
                DataOperationCoordinator.completeStartupRecovery(succeeded, failureMessage)
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
