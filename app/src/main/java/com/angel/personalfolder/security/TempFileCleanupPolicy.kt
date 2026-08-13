package com.angel.personalfolder.security

/**
 * Clock-skew-safe age policy for startup cleanup. The cleaner runs while the
 * operation gate is closed, so a future timestamp is treated as orphaned
 * rather than allowing plaintext to survive indefinitely after a clock reset.
 */
object TempFileCleanupPolicy {
    fun isStale(
        modifiedAt: Long,
        now: Long,
        maxAgeMs: Long,
        clockSkewToleranceMs: Long
    ): Boolean {
        if (modifiedAt <= 0L) return true
        if (modifiedAt > now + clockSkewToleranceMs) return true
        return now >= modifiedAt && now - modifiedAt >= maxAgeMs
    }
}
