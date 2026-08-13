package com.angel.personalfolder.data

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun fatalErrorsAreNotSilentlyCountedAsOneBadDocument() = runTest {
        var attempted = 0
        try {
            IndependentImportBatchRunner.run(listOf(1, 2, 3)) { item ->
                attempted += 1
                if (item == 1) throw SyntheticFatalError()
                item
            }
            fail("A fatal error must escape the batch runner.")
        } catch (_: SyntheticFatalError) {
            // Expected: the runner isolates ordinary source failures, not fatal VM-style errors.
        }

        assertEquals(1, attempted)
    }

    private class SyntheticFatalError : Error("synthetic fatal import error")
}
