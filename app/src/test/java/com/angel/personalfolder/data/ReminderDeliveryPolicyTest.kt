package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderDeliveryPolicyTest {
    @Test
    fun pastReminderIsDeliveredImmediatelyAfterRecovery() {
        assertEquals(0L, ReminderDeliveryPolicy.initialDelayMs(100L, 200L))
    }

    @Test
    fun missingNotificationPermissionKeepsReminderPending() {
        assertTrue(ReminderDeliveryPolicy.shouldKeepPending(notificationPermissionGranted = false))
    }

    @Test
    fun dateConversionIsSharedByDueAndDeadline() {
        val date = LocalDate.of(2099, 12, 31)
        val deadline = ReminderDatePolicy.deadlineAt(date)
        assertTrue(ReminderDatePolicy.dueAt(date, 0) == deadline)
        assertTrue(ReminderDatePolicy.dueAt(date, 30) < deadline)
    }

    @Test
    fun delayDoesNotOverflowForLongFutureTimestamp() {
        assertTrue(ReminderDeliveryPolicy.initialDelayMs(Long.MAX_VALUE, 0L) > 0L)
        assertEquals(Long.MAX_VALUE, ReminderDeliveryPolicy.initialDelayMs(Long.MAX_VALUE, Long.MIN_VALUE))
        assertEquals(0L, ReminderDeliveryPolicy.initialDelayMs(Long.MIN_VALUE, 0L))
    }

    @Test
    fun completedAndArchivedCasesDoNotScheduleReminders() {
        assertFalse(ReminderScheduler.shouldScheduleForCase(CaseStatus.COMPLETED))
        assertFalse(ReminderScheduler.shouldScheduleForCase(CaseStatus.ARCHIVED))
        assertTrue(ReminderScheduler.shouldScheduleForCase(CaseStatus.NEW))
        assertTrue(ReminderScheduler.shouldScheduleForCase(CaseStatus.IN_PROGRESS))
        assertTrue(ReminderScheduler.shouldScheduleForCase(CaseStatus.WAITING))
        assertTrue(ReminderScheduler.shouldScheduleForCase(CaseStatus.ACTION))
    }
}
