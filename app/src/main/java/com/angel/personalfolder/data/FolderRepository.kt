package com.angel.personalfolder.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.angel.personalfolder.security.FileCrypto
import com.angel.personalfolder.workers.OcrWorker
import java.io.File
import java.util.UUID

class FolderRepository(private val context: Context) {
    private val database = AppDatabase.get(context)

    fun documents(query: String) = if (query.isBlank()) database.documentDao().observeAll() else database.documentDao().search(query.trim())
    fun cases() = database.caseDao().observeAll()
    fun pendingReminders() = database.reminderDao().observePending()
    suspend fun document(id: String) = database.documentDao().getById(id)
    fun timeline(caseId: String) = database.timelineDao().observeForCase(caseId)
    fun checklist(caseId: String) = database.checklistDao().observeForCase(caseId)

    suspend fun importUris(uris: List<Uri>): String? {
        if (uris.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        val documentDirectory = File(context.filesDir, "documents/$id").apply { mkdirs() }
        val pages = mutableListOf<DocumentPageEntity>()
        var firstName = "Έγγραφο"
        var firstMime = "application/octet-stream"
        return try {
            uris.forEachIndexed { index, uri ->
                val name = displayName(uri) ?: "σελίδα_${index + 1}"
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (index == 0) {
                    firstName = name.substringBeforeLast('.', name)
                    firstMime = mime.ifBlank { guessMime(name) }
                }
                val target = File(documentDirectory, "page_$index.pf")
                FileCrypto.encryptUri(context, uri, target)
                pages += DocumentPageEntity(id, index, target.absolutePath)
            }
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.documentDao().insert(
                    DocumentEntity(
                        id = id,
                        title = firstName,
                        originalFileName = displayName(uris.first()) ?: firstName,
                        mimeType = firstMime,
                        encryptedPath = pages.first().encryptedPath,
                        pageCount = pages.size,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                database.documentPageDao().insertAll(pages)
            }
            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setInputData(workDataOf(OcrWorker.KEY_DOCUMENT_ID to id))
                .addTag("document-processing")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("ocr_$id", ExistingWorkPolicy.REPLACE, request)
            id
        } catch (error: Throwable) {
            FileCrypto.deleteRecursively(documentDirectory)
            throw error
        }
    }

    suspend fun updateDocumentBasics(id: String, title: String, category: String, expiryDate: String?) {
        database.documentDao().updateBasics(id, title.trim().ifBlank { "Έγγραφο" }, category, expiryDate, System.currentTimeMillis())
        if (expiryDate != null) ReminderScheduler.scheduleForDocument(context, id, title, expiryDate)
    }

    suspend fun deleteDocument(id: String) {
        val document = database.documentDao().getById(id)
        database.withTransaction {
            database.documentPageDao().deleteForDocument(id)
            database.documentDao().deleteById(id)
        }
        document?.let { FileCrypto.deleteRecursively(File(it.encryptedPath).parentFile ?: File(it.encryptedPath)) }
    }

    suspend fun createCase(title: String, description: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.caseDao().insert(CaseEntity(id, title.trim(), description.trim(), createdAt = now, updatedAt = now))
        database.timelineDao().insert(
            TimelineEventEntity(
                id = UUID.randomUUID().toString(),
                caseId = id,
                title = "Η υπόθεση δημιουργήθηκε",
                eventType = "system",
                eventDate = java.time.LocalDate.now().toString(),
                createdAt = now
            )
        )
        return id
    }

    suspend fun updateCaseStatus(id: String, status: String) {
        database.caseDao().updateStatus(id, status, System.currentTimeMillis())
        database.timelineDao().insert(
            TimelineEventEntity(
                id = UUID.randomUUID().toString(), caseId = id,
                title = "Η κατάσταση άλλαξε σε «$status»", eventType = "status",
                eventDate = java.time.LocalDate.now().toString(), createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addTimelineEvent(caseId: String, title: String, note: String) {
        database.timelineDao().insert(
            TimelineEventEntity(
                id = UUID.randomUUID().toString(), caseId = caseId, title = title.trim(), note = note.trim(),
                eventDate = java.time.LocalDate.now().toString(), createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addChecklistItem(caseId: String, title: String) {
        database.checklistDao().insert(ChecklistItemEntity(UUID.randomUUID().toString(), caseId, title.trim(), createdAt = System.currentTimeMillis()))
    }

    suspend fun setChecklistComplete(id: String, complete: Boolean) = database.checklistDao().setComplete(id, complete)

    private fun displayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
