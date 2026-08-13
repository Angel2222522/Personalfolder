package com.angel.personalfolder.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Removes plaintext or camera artifacts left by an interrupted process. */
object TempFileCleaner {
    private const val MAX_TEMP_AGE_MS = 15 * 60 * 1000L
    private const val MAX_SHARE_AGE_MS = 7 * 24 * 60 * 60 * 1000L

    suspend fun recover(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val directories = listOf(
            context.cacheDir.resolve("share") to MAX_SHARE_AGE_MS,
            context.cacheDir.resolve("camera") to MAX_TEMP_AGE_MS,
            context.cacheDir.resolve("scanner") to MAX_TEMP_AGE_MS,
            context.cacheDir.resolve("ocr") to MAX_TEMP_AGE_MS,
            context.cacheDir.resolve("viewer") to MAX_TEMP_AGE_MS,
            context.cacheDir.resolve("backup") to MAX_TEMP_AGE_MS,
            context.cacheDir.resolve("export") to MAX_TEMP_AGE_MS
        )
        directories.forEach { (directory, maxAge) ->
            directory.listFiles().orEmpty().forEach { file ->
                if (isStale(file, now, maxAge)) FileCrypto.deleteRecursively(file)
            }
        }
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".tmp") || it.name.endsWith(".part") }
            .filter { isStale(it, now, MAX_TEMP_AGE_MS) }
            .forEach(FileCrypto::deleteRecursively)
        // The journaled roots are handled by their recovery protocols. Once
        // those journals are absent, unjournaled staging trees are orphaned
        // and may be removed even when the device clock moved forward or
        // backward between process runs.
        val restoreJournal = context.filesDir.resolve("restore_journal.json")
        val deletionJournal = context.filesDir.resolve("document_delete_journal.json")
        if (!restoreJournal.isFile) {
            context.cacheDir.listFiles().orEmpty()
                .filter { it.name.startsWith("restore_documents_") || it.name.startsWith("previous_documents_") }
                .filter { isStale(it, now, MAX_TEMP_AGE_MS) }
                .forEach(FileCrypto::deleteRecursively)
        }
        if (!deletionJournal.isFile) {
            context.cacheDir.resolve("deleted_documents").listFiles().orEmpty()
                .filter { isStale(it, now, MAX_TEMP_AGE_MS) }
                .forEach(FileCrypto::deleteRecursively)
        }
    }

    private fun isStale(file: File, now: Long, maxAge: Long): Boolean {
        val modified = file.lastModified()
        if (modified <= 0L) return true
        // Startup cleanup runs while the normal operation gate is closed, so
        // a timestamp far in the future cannot belong to a live operation.
        // Treating it as stale prevents a backwards clock change from
        // keeping orphan plaintext/staging data indefinitely.
        if (modified > now + CLOCK_SKEW_TOLERANCE_MS) return true
        return now >= modified && now - modified >= maxAge
    }

    private const val CLOCK_SKEW_TOLERANCE_MS = 5 * 60 * 1000L
}
