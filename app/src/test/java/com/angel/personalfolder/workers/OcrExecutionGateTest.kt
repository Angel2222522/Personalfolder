package com.angel.personalfolder.workers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OcrExecutionGateTest {
    @Test
    fun tenQueuedJobsNeverRunHeavySectionConcurrentlyAndFailureReleasesGate() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        val results = coroutineScope {
            (1..10).map { index ->
                async {
                    runCatching {
                        OcrExecutionGate.runSerial {
                            val nowActive = active.incrementAndGet()
                            maxActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                            try {
                                yield()
                                if (index == 4) error("synthetic OCR failure")
                                index
                            } finally {
                                active.decrementAndGet()
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, maxActive.get())
        assertEquals(9, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals((1..10).filter { it != 4 }.toSet(), results.mapNotNull { it.getOrNull() }.toSet())
    }
}
