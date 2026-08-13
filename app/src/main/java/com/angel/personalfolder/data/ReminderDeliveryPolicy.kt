package com.angel.personalfolder.data

/** Keeps pending reminders recoverable when a trigger was missed or permission was absent. */
object ReminderDeliveryPolicy {
    fun initialDelayMs(dueAt: Long, now: Long): Long = (dueAt - now).coerceAtLeast(0L)

    fun shouldKeepPending(notificationPermissionGranted: Boolean): Boolean = !notificationPermissionGranted
}
