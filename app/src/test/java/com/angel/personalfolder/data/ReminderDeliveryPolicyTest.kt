package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDeliveryPolicyTest {
    @Test
    fun pastReminderIsDeliveredImmediatelyAfterRecovery() {
        assertEquals(0L, ReminderDeliveryPolicy.initialDelayMs(100L, 200L))
    }

    @Test
    fun missingNotificationPermissionKeepsReminderPending() {
        assertTrue(ReminderDeliveryPolicy.shouldKeepPending(notificationPermissionGranted = false))
    }
}
