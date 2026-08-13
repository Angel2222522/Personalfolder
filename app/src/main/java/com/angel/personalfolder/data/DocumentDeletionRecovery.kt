package com.angel.personalfolder.data

import android.content.Context
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Makes document deletion recoverable across the filesystem/Room boundary.
 * The directory is quarantined first; database deletion is committed second.
 */
object DocumentDeletionRecovery {
    private const val JOURNAL_FILE = "document_delete_journal.json"

    fun writeJournal(context: Context, documentId: String, root: File, quarantine: File, phase: String) {
        val journal = JSONObject()
            .put("documentId", documentId)
            .put("root", root.canonicalPath)
            .put("quarantine", quarantine.canonicalPath)
            .put("phase", phase)
        val target = context.filesDir.resolve(JOURNAL_FILE)
        val temporary = context.filesDir.resolve(".$JOURNAL_FILE.${System.nanoTime()}.part")
        try {
            temporary.outputStream().use { it.write(journal.toString().toByteArray(Charsets.UTF_8)) }
            require(temporary.renameTo(target)) { "Δεν ήταν δυνατή η καταγραφή της διαγραφής." }
        } finally {
            temporary.delete()
        }
    }

    fun clearJournal(context: Context) {
        context.filesDir.resolve(JOURNAL_FILE).delete()
    }

    suspend fun recover(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        DataOperationCoordinator.withExclusiveDuringStartup {
            val journalFile = context.filesDir.resolve(JOURNAL_FILE)
            if (!journalFile.isFile) return@withExclusiveDuringStartup
            val journal = runCatching { JSONObject(journalFile.readText(Charsets.UTF_8)) }.getOrNull()
                ?: return@withExclusiveDuringStartup
            val documentId = journal.optString("documentId")
            val root = safePath(journal.optString("root"), context.filesDir.resolve("documents"))
            val quarantine = safePath(journal.optString("quarantine"), context.cacheDir)
            if (documentId.isBlank() || root == null || quarantine == null) return@withExclusiveDuringStartup

            val stillInDatabase = database.documentDao().getById(documentId) != null
            val phase = journal.optString("phase")
            when {
                phase == "database_committed" || !stillInDatabase -> {
                    FileCrypto.deleteRecursivelyStrict(quarantine)
                    clearJournal(context)
                }

                stillInDatabase && !root.exists() && quarantine.exists() -> {
                    require(quarantine.renameTo(root)) { "Δεν ήταν δυνατή η ανάκτηση του εγγράφου." }
                    clearJournal(context)
                }

                stillInDatabase && root.exists() -> {
                    FileCrypto.deleteRecursivelyStrict(quarantine)
                    clearJournal(context)
                }
            }
        }
    }

    private fun safePath(path: String, parent: File): File? {
        if (path.isBlank()) return null
        val expected = runCatching { parent.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return if (candidate == expected || candidate.toPath().startsWith(expected.toPath())) candidate else null
    }
}
