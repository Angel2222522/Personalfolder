package com.angel.personalfolder.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.angel.personalfolder.security.FileCrypto
import com.angel.personalfolder.workers.OcrWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

class FolderRepository(private val context: Context) {
    private val database = AppDatabase.get(context)

    fun documents(query: String): kotlinx.coroutines.flow.Flow<List<DocumentEntity>> =
        documents(query, "", "", null, false)

    fun documents(
        query: String,
        category: String,
        processingState: String,
        caseId: String?,
        expiringSoon: Boolean
    ) = database.documentDao().search(
        ftsQuery = toFtsQuery(query),
        category = category,
        processingState = processingState,
        caseId = caseId,
        today = LocalDate.now().toString(),
        expiryBefore = if (expiringSoon) LocalDate.now().plusDays(31).toString() else null
    )

    fun cases() = database.caseDao().observeAll()
    fun pendingReminders() = database.reminderDao().observePending()
    fun allDocuments() = database.documentDao().observeAll()
    suspend fun document(id: String) = database.documentDao().getById(id)
    fun timeline(caseId: String) = database.timelineDao().observeForCase(caseId)
    fun checklist(caseId: String) = database.checklistDao().observeForCase(caseId)
    fun caseDocuments(caseId: String) = database.caseDocumentDao().observeDocumentsForCase(caseId)

    suspend fun importUris(uris: List<Uri>): String? = DataOperationCoordinator.withExclusive { withContext(Dispatchers.IO) {
        val distinctUris = uris.distinct()
        if (distinctUris.isEmpty()) return@withContext null
        require(distinctUris.size <= MAX_IMPORT_SOURCES) { "Μπορείς να εισαγάγεις έως $MAX_IMPORT_SOURCES σελίδες κάθε φορά." }
        val id = UUID.randomUUID().toString()
        val documentDirectory = File(context.filesDir, "documents/$id").apply { mkdirs() }
        val pages = mutableListOf<DocumentPageEntity>()
        val sourceCounts = mutableListOf<Int>()
        var firstName = "Έγγραφο"
        var firstMime = "application/octet-stream"
        var totalBytes = 0L
        var databaseCommitted = false
        return@withContext try {
            distinctUris.forEachIndexed { index, uri ->
                val name = safeDisplayName(uri) ?: "σελίδα_${index + 1}"
                val reportedMime = context.contentResolver.getType(uri).orEmpty()
                // Some document providers report every picked file as
                // application/octet-stream. Use the extension only as a
                // decoding hint; the PDF renderer/bitmap decoder below still
                // validates the actual bytes before the Room commit.
                val mime = reportedMime.takeUnless {
                    it.isBlank() || it.equals("application/octet-stream", ignoreCase = true)
                } ?: guessMime(name)
                require(mime == "application/pdf" || mime.startsWith("image/")) {
                    "Ο τύπος αρχείου «$name» δεν υποστηρίζεται."
                }
                if (index == 0) {
                    firstName = name.substringBeforeLast('.', name).ifBlank { "Έγγραφο" }
                    firstMime = mime
                }
                val knownSize = querySize(uri)
                require(knownSize == null || knownSize <= MAX_DOCUMENT_BYTES - totalBytes) { "Το έγγραφο είναι υπερβολικά μεγάλο συνολικά." }
                val target = File(documentDirectory, "page_$index.pf")
                totalBytes += FileCrypto.encryptUri(context, uri, target)
                require(totalBytes <= MAX_DOCUMENT_BYTES) { "Το έγγραφο είναι υπερβολικά μεγάλο συνολικά." }
                if (mime.startsWith("image/")) validateEncryptedImage(target, name)
                val pageCount = countEncryptedSourcePages(target, mime, name)
                require(sourceCounts.sum() + pageCount <= MAX_LOGICAL_PAGES) { "Το έγγραφο περιέχει υπερβολικά πολλές σελίδες." }
                pages += DocumentPageEntity(
                    documentId = id,
                    pageIndex = index,
                    encryptedPath = target.absolutePath,
                    sourceFileName = name,
                    mimeType = mime
                )
                sourceCounts += pageCount
            }
            require(database.documentDao().getAll().size < MAX_DOCUMENTS) { "Η βιβλιοθήκη έχει φτάσει το όριο εγγράφων." }
            require(currentStorageBytes() + totalBytes <= MAX_TOTAL_STORAGE_BYTES) { "Ο ιδιωτικός χώρος εγγράφων έχει φτάσει το όριό του." }
            val now = System.currentTimeMillis()
            val logicalPageCount = sourceCounts.sum().coerceAtLeast(pages.size)
            database.withTransaction {
                database.documentDao().insert(
                    DocumentEntity(
                        id = id,
                        title = firstName,
                        originalFileName = safeDisplayName(distinctUris.first()) ?: firstName,
                        mimeType = firstMime,
                        encryptedPath = pages.first().encryptedPath,
                        pageCount = logicalPageCount,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                database.documentPageDao().insertAll(pages)
            }
            databaseCommitted = true
            enqueueOcr(id)
            id
        } catch (error: Throwable) {
            if (!databaseCommitted) FileCrypto.deleteRecursively(documentDirectory)
            throw error
        }
    } }

    suspend fun updateDocumentMetadata(
        id: String,
        title: String,
        category: String,
        tags: String,
        provider: String,
        issuedDate: String?,
        expiryDate: String?,
        protocolNumber: String?
    ) {
        DataOperationCoordinator.withExclusive {
            val cleanTitle = title.trim().ifBlank { "Έγγραφο" }
            val cleanCategory = category.trim().ifBlank { "Άλλα" }
            val cleanTags = tags.trim()
            val cleanProvider = provider.trim()
            val cleanExpiry = expiryDate.cleanDateOrNull("Η ημερομηνία λήξης δεν είναι έγκυρη.")
            val cleanIssued = issuedDate.cleanDateOrNull("Η ημερομηνία έκδοσης δεν είναι έγκυρη.")
            val cleanProtocol = protocolNumber?.trim()?.ifBlank { null }
            val current = database.documentDao().getById(id) ?: error("Το έγγραφο δεν βρέθηκε.")
            val titleChanged = current.title != cleanTitle
            val categoryChanged = current.category != cleanCategory
            val providerChanged = current.provider != cleanProvider
            val issuedChanged = current.issuedDate != cleanIssued
            val expiryChanged = current.expiryDate != cleanExpiry
            val protocolChanged = current.protocolNumber != cleanProtocol
            val ownership = MetadataOwnershipPolicy.merge(
                current,
                MetadataFieldChanges(titleChanged, categoryChanged, providerChanged, issuedChanged, expiryChanged, protocolChanged)
            )
            database.documentDao().update(
                current.copy(
                    title = cleanTitle,
                    category = cleanCategory,
                    tags = cleanTags,
                    provider = cleanProvider,
                    issuedDate = cleanIssued,
                    expiryDate = cleanExpiry,
                    protocolNumber = cleanProtocol,
                    metadataManuallyEdited = current.metadataManuallyEdited || ownership.any,
                    expiryDateSuggestion = if (expiryChanged) null else current.expiryDateSuggestion,
                    expiryDateSuggestionConfidence = if (expiryChanged) MetadataConfidence.NONE else current.expiryDateSuggestionConfidence,
                    titleConfidence = if (titleChanged) MetadataConfidence.MANUAL else current.titleConfidence,
                    categoryConfidence = if (categoryChanged) MetadataConfidence.MANUAL else current.categoryConfidence,
                    providerConfidence = if (providerChanged) MetadataConfidence.MANUAL else current.providerConfidence,
                    issuedDateConfidence = if (issuedChanged) MetadataConfidence.MANUAL else current.issuedDateConfidence,
                    expiryDateConfidence = if (expiryChanged) MetadataConfidence.MANUAL else current.expiryDateConfidence,
                    protocolNumberConfidence = if (protocolChanged) MetadataConfidence.MANUAL else current.protocolNumberConfidence,
                    titleManuallyEdited = ownership.title,
                    categoryManuallyEdited = ownership.category,
                    providerManuallyEdited = ownership.provider,
                    issuedDateManuallyEdited = ownership.issuedDate,
                    expiryDateManuallyEdited = ownership.expiryDate,
                    protocolNumberManuallyEdited = ownership.protocolNumber,
                    updatedAt = System.currentTimeMillis()
                )
            )
            ReminderScheduler.replaceForDocument(context, id, cleanTitle, cleanExpiry)
        }
    }

    suspend fun retryOcr(id: String) {
        DataOperationCoordinator.withExclusive {
            require(database.documentDao().getById(id) != null) { "Το έγγραφο δεν βρέθηκε." }
            enqueueOcr(id)
        }
    }

    suspend fun deleteDocument(id: String) {
        DataOperationCoordinator.withExclusive {
            require(database.documentDao().getById(id) != null) { "Το έγγραφο δεν βρέθηκε." }
            WorkManager.getInstance(context).cancelUniqueWork("ocr_$id")
            ReminderScheduler.removeForDocument(context, id)
            val root = context.filesDir.resolve("documents/$id")
            val quarantine = context.cacheDir.resolve("deleted_documents/${id}_${UUID.randomUUID()}")
            var quarantined = false
            var databaseCommitted = false
            try {
                if (root.exists()) {
                    DocumentDeletionRecovery.writeJournal(context, id, root, quarantine, "prepared")
                    quarantine.parentFile?.mkdirs()
                    require(root.renameTo(quarantine)) { "Δεν ήταν δυνατή η προετοιμασία της διαγραφής." }
                    quarantined = true
                    DocumentDeletionRecovery.writeJournal(context, id, root, quarantine, "quarantined")
                }
                database.withTransaction {
                    database.caseDocumentDao().deleteForDocument(id)
                    database.reminderDao().deleteForDocument(id)
                    database.documentPageDao().deleteForDocument(id)
                    database.documentDao().deleteById(id)
                }
                databaseCommitted = true
                if (quarantined) {
                    DocumentDeletionRecovery.writeJournal(context, id, root, quarantine, "database_committed")
                    FileCrypto.deleteRecursivelyStrict(quarantine)
                    DocumentDeletionRecovery.clearJournal(context)
                }
            } catch (error: Throwable) {
                if (!databaseCommitted && quarantined) {
                    runCatching {
                        require(!root.exists()) { "Το πρωτότυπο αρχείο δεν βρίσκεται στη σωστή θέση." }
                        require(quarantine.renameTo(root)) { "Δεν ήταν δυνατή η ακύρωση της διαγραφής." }
                        DocumentDeletionRecovery.clearJournal(context)
                    }
                }
                throw error
            }
        }
    }

    suspend fun createCase(title: String, description: String): String =
        createCase(title, description, null, null, "", "")

    suspend fun createCase(
        title: String,
        description: String,
        startDate: String?,
        deadline: String?,
        nextStep: String,
        notes: String
    ): String = DataOperationCoordinator.withExclusive {
        val cleanTitle = title.trim().ifBlank { error("Η υπόθεση χρειάζεται τίτλο.") }
        val cleanStart = startDate.cleanDateOrNull("Η ημερομηνία έναρξης δεν είναι έγκυρη.")
        val cleanDeadline = deadline.cleanDateOrNull("Η προθεσμία δεν είναι έγκυρη.")
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.caseDao().insert(CaseEntity(id, cleanTitle, description.trim(), startDate = cleanStart, deadline = cleanDeadline, nextStep = nextStep.trim(), notes = notes.trim(), createdAt = now, updatedAt = now))
            database.timelineDao().insert(
                TimelineEventEntity(
                    id = UUID.randomUUID().toString(), caseId = id,
                    title = "Η υπόθεση δημιουργήθηκε", eventType = "system",
                    eventDate = LocalDate.now().toString(), createdAt = now
                )
            )
        }
        ReminderScheduler.replaceForCase(context, id, cleanTitle, cleanDeadline)
        id
    }

    suspend fun updateCase(
        id: String,
        title: String,
        description: String,
        status: String,
        startDate: String?,
        deadline: String?,
        nextStep: String,
        notes: String
    ) = DataOperationCoordinator.withExclusive {
        val cleanTitle = title.trim().ifBlank { error("Η υπόθεση χρειάζεται τίτλο.") }
        val cleanStart = startDate.cleanDateOrNull("Η ημερομηνία έναρξης δεν είναι έγκυρη.")
        val cleanDeadline = deadline.cleanDateOrNull("Η προθεσμία δεν είναι έγκυρη.")
        database.caseDao().update(id, cleanTitle, description.trim(), status, cleanStart, cleanDeadline, nextStep.trim(), notes.trim(), System.currentTimeMillis())
        ReminderScheduler.replaceForCase(context, id, cleanTitle, cleanDeadline)
    }

    suspend fun deleteCase(id: String) = DataOperationCoordinator.withExclusive {
        ReminderScheduler.removeForCase(context, id)
        database.caseDao().deleteById(id)
    }

    suspend fun updateCaseStatus(id: String, status: String) = DataOperationCoordinator.withExclusive {
        database.caseDao().updateStatus(id, status, System.currentTimeMillis())
        database.timelineDao().insert(
            TimelineEventEntity(
                id = UUID.randomUUID().toString(), caseId = id,
                title = "Η κατάσταση άλλαξε σε «$status»", eventType = "status",
                eventDate = LocalDate.now().toString(), createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun attachDocumentToCase(caseId: String, documentId: String) = DataOperationCoordinator.withExclusive {
        require(database.caseDao().getById(caseId) != null) { "Η υπόθεση δεν βρέθηκε." }
        require(database.documentDao().getById(documentId) != null) { "Το έγγραφο δεν βρέθηκε." }
        database.caseDocumentDao().insert(CaseDocumentCrossRef(caseId, documentId))
        database.caseDao().getById(caseId)?.let { caseEntity ->
            database.caseDao().updateStatus(caseId, caseEntity.status, System.currentTimeMillis())
        }
    }

    suspend fun detachDocumentFromCase(caseId: String, documentId: String) = DataOperationCoordinator.withExclusive {
        database.caseDocumentDao().delete(caseId, documentId)
    }

    suspend fun addTimelineEvent(caseId: String, title: String, note: String) = DataOperationCoordinator.withExclusive {
        require(database.caseDao().getById(caseId) != null) { "Η υπόθεση δεν βρέθηκε." }
        database.timelineDao().insert(
            TimelineEventEntity(
                id = UUID.randomUUID().toString(), caseId = caseId, title = title.trim(), note = note.trim(),
                eventDate = LocalDate.now().toString(), createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addChecklistItem(caseId: String, title: String, linkedDocumentId: String? = null) = DataOperationCoordinator.withExclusive {
        require(database.caseDao().getById(caseId) != null) { "Η υπόθεση δεν βρέθηκε." }
        if (linkedDocumentId != null) require(database.documentDao().getById(linkedDocumentId) != null) { "Το έγγραφο δεν βρέθηκε." }
        database.checklistDao().insert(ChecklistItemEntity(UUID.randomUUID().toString(), caseId, title.trim(), linkedDocumentId = linkedDocumentId, createdAt = System.currentTimeMillis()))
    }

    suspend fun setChecklistComplete(id: String, complete: Boolean) = DataOperationCoordinator.withExclusive {
        database.checklistDao().setComplete(id, complete)
    }

    suspend fun linkChecklistDocument(id: String, documentId: String?) = DataOperationCoordinator.withExclusive {
        if (documentId != null) require(database.documentDao().getById(documentId) != null) { "Το έγγραφο δεν βρέθηκε." }
        database.checklistDao().linkDocument(id, documentId)
    }

    suspend fun deleteChecklistItem(id: String) = DataOperationCoordinator.withExclusive {
        database.checklistDao().deleteById(id)
    }

    suspend fun markReminderDone(id: String) = DataOperationCoordinator.withExclusive {
        database.reminderDao().markDone(id)
    }

    private suspend fun enqueueOcr(id: String) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_DOCUMENT_ID to id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("document-processing")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("ocr_$id", ExistingWorkPolicy.REPLACE, request)
    }

    private fun countEncryptedSourcePages(encrypted: File, mime: String, name: String): Int {
        if (!mime.equals("application/pdf", true) && !name.endsWith(".pdf", true)) return 1
        val temp = File(context.cacheDir, "ocr/import_${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
        return try {
            FileCrypto.decryptToTemp(encrypted, temp)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount.coerceAtLeast(1).also { count -> require(count <= MAX_LOGICAL_PAGES) { "Το PDF περιέχει υπερβολικά πολλές σελίδες." } } }
            }
        } finally {
            temp.delete()
        }
    }

    private fun validateEncryptedImage(encrypted: File, name: String) {
        val temp = File(context.cacheDir, "ocr/import_${UUID.randomUUID()}.image.tmp").apply { parentFile?.mkdirs() }
        try {
            FileCrypto.decryptToTemp(encrypted, temp)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Η εικόνα «$name» δεν αποκωδικοποιείται." }
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath)
                ?: error("Η εικόνα «$name» δεν αποκωδικοποιείται.")
            bitmap.recycle()
        } finally {
            temp.delete()
        }
    }

    private fun safeDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.take(180)
            else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')?.take(180)
    }.getOrNull()

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "application/octet-stream"
    }

    private fun String?.cleanDateOrNull(error: String): String? {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) return null
        return runCatching { LocalDate.parse(value).toString() }.getOrElse { throw IllegalArgumentException(error) }
    }

    private fun toFtsQuery(value: String): String = value.trim().split(Regex("\\s+"))
        .map { it.replace(Regex("[^\\p{L}\\p{N}_-]"), "") }
        .filter { it.isNotBlank() }
        .joinToString(" AND ") { "\"$it\"*" }

    private suspend fun currentStorageBytes(): Long {
        val paths = buildSet {
            database.documentDao().getAll().forEach { add(it.encryptedPath) }
            database.documentPageDao().getAll().forEach { add(it.encryptedPath) }
        }
        return paths.sumOf { path -> File(path).takeIf { it.isFile }?.length() ?: 0L }
    }

    private companion object {
        const val MAX_IMPORT_SOURCES = 100
        const val MAX_DOCUMENT_BYTES = 512L * 1024 * 1024
        const val MAX_TOTAL_STORAGE_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_DOCUMENTS = 5_000
        const val MAX_LOGICAL_PAGES = 1_000
    }
}
