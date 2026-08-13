package com.angel.personalfolder.data

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class IndependentImportBatchRunnerTest {
    @Test
    fun tenIndependentPdfsRemainSequentialAndOneFailureDoesNotCancelTheRest() = runTest {
        val syntheticSources = (1..10).map { "synthetic-document-$it.pdf" }
        val attempted = mutableListOf<String>()
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        val result = IndependentImportBatchRunner.run(syntheticSources) { source ->
            attempted += source
            val nowActive = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, nowActive) }
            try {
                yield()
                if (source == "synthetic-document-5.pdf") error("synthetic import failure")
                "document-id-${source.substringAfterLast('-').substringBefore('.')}"
            } finally {
                active.decrementAndGet()
            }
        }

        assertEquals(10, attempted.size)
        assertEquals(syntheticSources, attempted)
        assertEquals(10, result.totalCount)
        assertEquals(9, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(9, result.successes.toSet().size)
        assertEquals(1, maxActive.get())
        assertTrue(attempted.last() == "synthetic-document-10.pdf")
    }
}
