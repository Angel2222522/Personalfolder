package com.angel.personalfolder.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Removes plaintext or camera artifacts left by an interrupted process. */
object TempFileCleaner {
    private const val MAX_TEMP_AGE_MS = 15 * 60 * 1000L

    suspend fun recover(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        listOf(
            context.cacheDir.resolve("share"),
            context.cacheDir.resolve("camera"),
            context.cacheDir.resolve("scanner"),
            context.cacheDir.resolve("ocr"),
            context.cacheDir.resolve("viewer"),
            context.cacheDir.resolve("backup"),
            context.cacheDir.resolve("export")
        ).forEach { directory ->
            directory.listFiles().orEmpty().forEach { file ->
                if (now - file.lastModified() > MAX_TEMP_AGE_MS) FileCrypto.deleteRecursively(file)
            }
        }
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".tmp") || it.name.endsWith(".part") }
            .filter { now - it.lastModified() > MAX_TEMP_AGE_MS }
            .forEach(FileCrypto::deleteRecursively)
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("restore_documents_") || it.name.startsWith("previous_documents_") }
            .filter { now - it.lastModified() > MAX_TEMP_AGE_MS }
            .forEach(FileCrypto::deleteRecursively)
    }

    fun scheduleDeletion(file: File, delayMs: Long = MAX_TEMP_AGE_MS) {
        file.parentFile?.mkdirs()
        val thread = Thread {
            try {
                Thread.sleep(delayMs)
                FileCrypto.deleteRecursively(file)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread.isDaemon = true
        thread.start()
    }
}
