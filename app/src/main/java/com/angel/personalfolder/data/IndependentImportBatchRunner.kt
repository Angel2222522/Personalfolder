package com.angel.personalfolder.data

import kotlinx.coroutines.CancellationException

/**
 * Runs a multi-file picker selection as independent imports.
 *
 * The runner is intentionally sequential: importing one source may validate and
 * encrypt a large PDF, so bounded one-at-a-time admission avoids memory spikes.
 * A failed source is recorded and the remaining sources still run.
 */
object IndependentImportBatchRunner {
    suspend fun <T, R> run(
        items: List<T>,
        importOne: suspend (T) -> R
    ): IndependentImportBatchResult<R> {
        val successes = ArrayList<R>(items.size)
        var failures = 0
        for (item in items) {
            try {
                successes += importOne(item)
            } catch (error: Throwable) {
                if (error is CancellationException || error is OutOfMemoryError) throw error
                failures += 1
            }
        }
        return IndependentImportBatchResult(successes, failures, items.size)
    }
}

data class IndependentImportBatchResult<R>(
    val successes: List<R>,
    val failureCount: Int,
    val totalCount: Int
) {
    val successCount: Int get() = successes.size
}
