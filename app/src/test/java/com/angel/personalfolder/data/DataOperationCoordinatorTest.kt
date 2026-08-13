package com.angel.personalfolder.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class DataOperationCoordinatorTest {
    @Test
    fun filesystemAndDatabaseGenerationOperationsDoNotOverlap() = runTest {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val first = async {
            DataOperationCoordinator.withExclusive {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(20)
                active.decrementAndGet()
            }
        }
        val second = async {
            DataOperationCoordinator.withExclusive {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(20)
                active.decrementAndGet()
            }
        }
        first.await()
        second.await()
        assertEquals(1, peak.get())
    }

    @Test
    fun normalOperationsWaitForStartupRecovery() = runTest {
        DataOperationCoordinator.beginStartupRecovery()
        try {
            val entered = CompletableDeferred<Unit>()
            val operation = async {
                DataOperationCoordinator.withExclusive {
                    entered.complete(Unit)
                }
            }
            delay(20)
            assertFalse(entered.isCompleted)
            DataOperationCoordinator.completeStartupRecovery()
            operation.await()
        } finally {
            DataOperationCoordinator.completeStartupRecovery()
        }
    }
}
