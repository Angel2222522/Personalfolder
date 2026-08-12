package com.angel.personalfolder.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes every operation that can change the logical library generation.
 *
 * Room transactions alone cannot protect the encrypted page tree. Restore,
 * backup, import, deletion and OCR therefore share this process-wide gate.
 */
object DataOperationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withExclusive(block: suspend () -> T): T = mutex.withLock(action = block)
}
