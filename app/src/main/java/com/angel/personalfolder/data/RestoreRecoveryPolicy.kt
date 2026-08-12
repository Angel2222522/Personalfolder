package com.angel.personalfolder.data

import java.security.MessageDigest
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/** The only safe actions available while completing an interrupted restore. */
enum class RestoreRecoveryAction {
    FINALIZE_NEW_GENERATION,
    ROLLBACK_TO_PREVIOUS_GENERATION,
    PRESERVE_AND_RETRY
}

data class RestoreRecoveryState(
    val phase: String,
    val currentDocumentIds: Set<String>,
    val expectedDocumentIds: Set<String>,
    val rootExists: Boolean,
    val previousRootExists: Boolean,
    val stagingRootExists: Boolean,
    val rootMatchesExpected: Boolean,
    /** True only when the complete Room generation matches the journal. */
    val databaseGenerationMatches: Boolean = false
)

/**
 * Decides recovery without ever assuming that a missing previous directory
 * is permission to delete the current root.
 */
object RestoreRecoveryPolicy {
    fun decide(state: RestoreRecoveryState): RestoreRecoveryAction {
        val newGenerationIsValid = state.databaseGenerationMatches &&
            state.rootExists &&
            state.rootMatchesExpected

        return when (state.phase) {
            "database_committed" -> if (newGenerationIsValid) {
                RestoreRecoveryAction.FINALIZE_NEW_GENERATION
            } else {
                RestoreRecoveryAction.PRESERVE_AND_RETRY
            }

            "files_installed" -> when {
                newGenerationIsValid -> RestoreRecoveryAction.FINALIZE_NEW_GENERATION
                state.previousRootExists -> RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION
                else -> RestoreRecoveryAction.PRESERVE_AND_RETRY
            }

            "prepared" -> when {
                // The transaction has not started at this phase. If a
                // process stopped after the directory swap but before the
                // journal advanced to files_installed, the old root must be
                // restored even when the replacement has the same document
                // IDs and the same database fingerprint.
                state.previousRootExists -> RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION
                // The old root may still be the only valid library. Preserve it.
                else -> RestoreRecoveryAction.PRESERVE_AND_RETRY
            }

            else -> RestoreRecoveryAction.PRESERVE_AND_RETRY
        }
    }
}

/**
 * Stable, content-based identity for the Room side of a library generation.
 * Document IDs alone are insufficient: a restore can replace metadata or
 * page paths while keeping every ID unchanged.
 */
object RestoreGenerationFingerprint {
    fun of(
        documents: List<DocumentEntity>,
        pages: List<DocumentPageEntity>,
        cases: List<CaseEntity>,
        relations: List<CaseDocumentCrossRef>,
        events: List<TimelineEventEntity>,
        checklist: List<ChecklistItemEntity>,
        reminders: List<ReminderEntity>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")

        fun update(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val length = bytes.size.toString().toByteArray(Charsets.US_ASCII)
            digest.update(length)
            digest.update(':'.code.toByte())
            digest.update(bytes)
        }

        fun updateRows(label: String, rows: Iterable<Any>) {
            val materialized = rows.toList()
            update(label)
            update(materialized.size.toString())
            materialized.forEach { update(it.toString()) }
        }

        update("personal-folder-room-generation-v1")
        updateRows("documents", documents.sortedBy { it.id })
        updateRows("pages", pages.sortedWith(compareBy<DocumentPageEntity> { it.documentId }.thenBy { it.pageIndex }))
        updateRows("cases", cases.sortedBy { it.id })
        updateRows("relations", relations.sortedWith(compareBy<CaseDocumentCrossRef> { it.caseId }.thenBy { it.documentId }))
        updateRows("events", events.sortedBy { it.id })
        updateRows("checklist", checklist.sortedBy { it.id })
        updateRows("reminders", reminders.sortedBy { it.id })

        return digest.digest().joinToString("") { byte ->
            String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
        }
    }

    /**
     * Content-based identity for an installed encrypted document tree. The
     * root's absolute path is deliberately excluded so the same generation
     * can be compared before and after a directory swap.
     */
    fun filesystemOf(root: File): String? {
        if (!root.isDirectory) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val files = mutableListOf<File>()

        fun collect(directory: File) {
            directory.listFiles().orEmpty().forEach { child ->
                val canonical = runCatching { child.canonicalFile }.getOrNull() ?: return@forEach
                if (!canonical.toPath().startsWith(canonicalRoot.toPath())) return@forEach
                if (child.isDirectory) collect(child) else if (child.isFile) files += child
            }
        }
        collect(canonicalRoot)

        val digest = MessageDigest.getInstance("SHA-256")
        fun update(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(bytes)
        }

        update("personal-folder-filesystem-generation-v1")
        files.sortedBy { it.relativeTo(canonicalRoot).invariantSeparatorsPath }.forEach { file ->
            val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
            update(relative)
            update(file.length().toString())
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
        }
    }
}
