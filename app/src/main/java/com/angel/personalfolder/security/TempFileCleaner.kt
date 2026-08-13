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
                if (now - file.lastModified() > maxAge) FileCrypto.deleteRecursively(file)
            }
        }
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".tmp") || it.name.endsWith(".part") }
            .filter { now - it.lastModified() > MAX_TEMP_AGE_MS }
            .forEach(FileCrypto::deleteRecursively)
        // Restore and deletion recovery directories are journal-owned. They
        // must not be removed by a time-based cleaner while recovery is still
        // possible after process death.
    }

}
