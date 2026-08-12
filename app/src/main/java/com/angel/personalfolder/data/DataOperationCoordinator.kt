package com.angel.personalfolder.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex

/**
 * Serializes every operation that can change the logical library generation.
 *
 * Room transactions alone cannot protect the encrypted page tree. Restore,
 * backup, import, deletion and OCR therefore share this process-wide gate.
 */
object DataOperationCoordinator {
    private val mutex = Mutex()
    @Volatile private var startupRecoveryGate: CompletableDeferred<Unit>? = null

    /** Called synchronously by Application before any UI operation can run. */
    fun beginStartupRecovery() {
        synchronized(this) {
            if (startupRecoveryGate?.isCompleted != false) {
                startupRecoveryGate = CompletableDeferred()
            }
        }
    }

    /** Releases operations waiting for startup recovery, including safe no-op recovery. */
    fun completeStartupRecovery() {
        startupRecoveryGate?.complete(Unit)
    }

    suspend fun <T> withExclusive(block: suspend () -> T): T {
        startupRecoveryGate?.await()
        return withLockHeld(block)
    }

    /** Used only by startup recovery itself, before the normal gate opens. */
    suspend fun <T> withExclusiveDuringStartup(block: suspend () -> T): T = withLockHeld(block)

    private suspend fun <T> withLockHeld(block: suspend () -> T): T {
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
