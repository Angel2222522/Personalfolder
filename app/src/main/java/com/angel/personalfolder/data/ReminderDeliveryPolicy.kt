package com.angel.personalfolder.data

/** Keeps pending reminders recoverable when a trigger was missed or permission was absent. */
object ReminderDeliveryPolicy {
    fun initialDelayMs(dueAt: Long, now: Long): Long = if (dueAt <= now) {
        0L
    } else {
        // Long subtraction can wrap for a far-future due date and a very old
        // clock value. WorkManager must receive a non-negative delay.
        (dueAt - now).takeIf { it >= 0L } ?: Long.MAX_VALUE
    }

    fun shouldKeepPending(notificationPermissionGranted: Boolean): Boolean = !notificationPermissionGranted
}
