package com.angel.personalfolder.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates startup recovery and short database/filesystem commit phases.
 *
 * Long OCR, rendering and export work must not hold the process-wide mutex.
 * Per-document work uses [withDocumentExclusive] so an individual document
 * cannot be deleted while its encrypted source is being read, while unrelated
 * documents remain available.
 */
object DataOperationCoordinator {
    private val mutex = Mutex()
    private val documentMutexes = ConcurrentHashMap<String, Mutex>()
    @Volatile private var startupRecoveryGate: CompletableDeferred<Unit>? = null
    @Volatile private var userSessionRequired = false
    @Volatile private var userSessionUnlocked = true

    private val _recoveryState = MutableStateFlow(StartupRecoveryState.SAFE)
    val recoveryState: StateFlow<StartupRecoveryState> = _recoveryState.asStateFlow()

    enum class StartupRecoveryState { IN_PROGRESS, SAFE, BLOCKED }

    class RecoveryBlockedException(message: String) : IllegalStateException(message)

    /** Called synchronously by Application before any UI operation can run. */
    fun beginStartupRecovery() {
        synchronized(this) {
            if (startupRecoveryGate?.isCompleted != false) {
                startupRecoveryGate = CompletableDeferred()
            }
            _recoveryState.value = StartupRecoveryState.IN_PROGRESS
        }
    }

    /** Opens normal operations only after all recovery evidence is safe. */
    fun completeStartupRecovery(success: Boolean, message: String? = null) {
        _recoveryState.value = if (success) StartupRecoveryState.SAFE else StartupRecoveryState.BLOCKED
        startupRecoveryGate?.complete(Unit)
        if (!success) {
            blockedMessage = message?.take(500) ?: "Η ανάκτηση της βιβλιοθήκης δεν ολοκληρώθηκε με ασφάλεια."
        }
    }

    @Volatile private var blockedMessage: String = "Η ανάκτηση της βιβλιοθήκης δεν ολοκληρώθηκε με ασφάλεια."

    fun recoveryMessage(): String = blockedMessage

    fun setUserSessionState(lockEnabled: Boolean, unlocked: Boolean) {
        userSessionRequired = lockEnabled
        userSessionUnlocked = !lockEnabled || unlocked
    }

    fun requireUserSessionUnlocked() {
        if (userSessionRequired && !userSessionUnlocked) {
            throw RecoveryBlockedException("Η συνεδρία κλειδώθηκε. Ταυτοποιήσου ξανά για να συνεχίσεις.")
        }
    }

    fun requireRecoverySafe() {
        if (_recoveryState.value != StartupRecoveryState.SAFE) {
            throw RecoveryBlockedException(blockedMessage)
        }
    }

    suspend fun <T> withExclusive(block: suspend () -> T): T {
        startupRecoveryGate?.await()
        requireRecoverySafe()
        return withLockHeld(block)
    }

    /** Used only by startup recovery itself, before the normal gate opens. */
    suspend fun <T> withExclusiveDuringStartup(block: suspend () -> T): T = withLockHeld(block)

    /**
     * Serializes long work for one document without blocking all library
     * operations. This is also used around the final state transition.
     */
    suspend fun <T> withDocumentExclusive(documentId: String, block: suspend () -> T): T {
        startupRecoveryGate?.await()
        requireRecoverySafe()
        val lock = documentMutexes.computeIfAbsent(documentId) { Mutex() }
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
            if (!lock.isLocked) documentMutexes.remove(documentId, lock)
        }
    }

    private suspend fun <T> withLockHeld(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
