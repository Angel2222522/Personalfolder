package com.angel.personalfolder.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempFileCleanupPolicyTest {
    private val age = 15 * 60 * 1000L
    private val skew = 5 * 60 * 1000L

    @Test
    fun removesUnknownAndOldTimestamps() {
        assertTrue(TempFileCleanupPolicy.isStale(0L, 1_000_000L, age, skew))
        assertTrue(TempFileCleanupPolicy.isStale(1_000_000L - age, 1_000_000L, age, skew))
    }

    @Test
    fun keepsRecentAndSmallBackwardClockChanges() {
        assertFalse(TempFileCleanupPolicy.isStale(1_000_000L - age + 1, 1_000_000L, age, skew))
        assertFalse(TempFileCleanupPolicy.isStale(1_000_000L + skew, 1_000_000L, age, skew))
    }

    @Test
    fun removesFarFutureTimestampAfterClockJump() {
        assertTrue(TempFileCleanupPolicy.isStale(1_000_000L + skew + 1, 1_000_000L, age, skew))
    }
}
