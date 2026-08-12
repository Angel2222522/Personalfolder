package com.angel.personalfolder.data

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOperationCoordinatorTest {
    @Test
    fun doesNotRunLibraryOperationsConcurrently() = runBlocking {
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>(null)

        val jobs = List(8) {
            launch {
                try {
                    LibraryOperationCoordinator.withExclusive {
                        val current = active.incrementAndGet()
                        maximumActive.updateAndGet { previous -> maxOf(previous, current) }
                        delay(5)
                        active.decrementAndGet()
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
        }
        jobs.forEach { it.join() }

        assertEquals(null, failure.get())
        assertEquals(1, maximumActive.get())
        assertEquals(0, active.get())
    }
}
