package com.angel.personalfolder.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises operations that replace or rebuild the local document library.
 *
 * Room transactions protect database statements, but they cannot make a
 * database transaction and a filesystem swap atomic together. This process
 * local gate prevents imports/deletes/backups/restores from interleaving while
 * one of those operations is moving files or taking a snapshot.
 */
object LibraryOperationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withExclusive(block: suspend () -> T): T = mutex.withLock(block)
}
