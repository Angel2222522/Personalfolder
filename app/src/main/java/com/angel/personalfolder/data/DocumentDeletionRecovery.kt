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
            val journal = runCatching { JSONObject(journalFile.readText(Charsets.UTF_8)) }
                .getOrElse { throw IllegalStateException("Το ημερολόγιο διαγραφής δεν είναι έγκυρο.", it) }
            val documentId = journal.optString("documentId")
            val root = safePath(journal.optString("root"), context.filesDir.resolve("documents"))
            val quarantine = safePath(journal.optString("quarantine"), context.cacheDir)
            require(documentId.matches(Regex("[A-Za-z0-9_-]{1,100}"))) {
                "Το ημερολόγιο διαγραφής περιέχει μη έγκυρο έγγραφο."
            }
            val safeRoot = requireNotNull(root) { "Το ημερολόγιο διαγραφής περιέχει μη ασφαλή ρίζα." }
            val safeQuarantine = requireNotNull(quarantine) { "Το ημερολόγιο διαγραφής περιέχει μη ασφαλή quarantine." }
            val documentsRoot = context.filesDir.resolve("documents").canonicalFile
            require(safeRoot.parentFile == documentsRoot && safeRoot.name == documentId) {
                "Το ημερολόγιο διαγραφής δείχνει σε μη έγκυρο χώρο εγγράφου."
            }
            val quarantineRoot = context.cacheDir.resolve("deleted_documents").canonicalFile
            require(safeQuarantine.parentFile == quarantineRoot) {
                "Το ημερολόγιο διαγραφής δείχνει σε μη έγκυρο quarantine."
            }

            val stillInDatabase = database.documentDao().getById(documentId) != null
            val phase = journal.optString("phase")
            when {
                phase == "database_committed" || !stillInDatabase -> {
                    FileCrypto.deleteRecursivelyStrict(safeRoot)
                    FileCrypto.deleteRecursivelyStrict(safeQuarantine)
                    clearJournal(context)
                }

                stillInDatabase && !safeRoot.exists() && safeQuarantine.exists() -> {
                    require(safeQuarantine.renameTo(safeRoot)) { "Δεν ήταν δυνατή η ανάκτηση του εγγράφου." }
                    clearJournal(context)
                }

                stillInDatabase && safeRoot.exists() -> {
                    FileCrypto.deleteRecursivelyStrict(safeQuarantine)
                    clearJournal(context)
                }

                else -> throw IllegalStateException(
                    "Η διαγραφή άφησε αμφίσημη κατάσταση αρχείων. Η λειτουργία παραμένει κλειδωμένη."
                )
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
