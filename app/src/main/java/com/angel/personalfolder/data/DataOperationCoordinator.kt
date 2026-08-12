package com.angel.personalfolder.data

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes every operation that can change the logical library generation.
 *
 * Room transactions alone cannot protect the encrypted page tree. Restore,
 * backup, import, deletion and OCR therefore share this process-wide gate.
 */
object DataOperationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withExclusive(block: suspend () -> T): T {
        // Mutex.withLock exposes a non-suspending callback.  The library
        // operations below legitimately suspend while Room, WorkManager and
        // the filesystem are active, so acquire/release explicitly and keep
        // the lock across the entire suspendable critical section.
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
