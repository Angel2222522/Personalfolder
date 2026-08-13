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

    fun documents(query: String): kotlinx.coroutines.flow.Flow<List<DocumentSummary>> =
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
    fun documentFlow(id: String) = database.documentDao().observeById(id)
    fun timeline(caseId: String) = database.timelineDao().observeForCase(caseId)
    fun checklist(caseId: String) = database.checklistDao().observeForCase(caseId)
    fun caseDocuments(caseId: String) = database.caseDocumentDao().observeDocumentsForCase(caseId)

    suspend fun importUris(uris: List<Uri>): String? = withContext(Dispatchers.IO) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        val distinctUris = uris.distinct()
        if (distinctUris.isEmpty()) return@withContext null
        LibraryLimits.requireSourceCount(distinctUris.size)
        val id = UUID.randomUUID().toString()
        val documentDirectory = File(context.filesDir, "documents/$id").apply {
            require(mkdirs() || isDirectory) { "Δεν ήταν δυνατή η δημιουργία του ιδιωτικού χώρου εγγράφου." }
        }
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
                val mime = ImportTypePolicy.resolveMime(reportedMime, name)
                require(ImportTypePolicy.isSupported(mime)) {
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
                LibraryLimits.requireLogicalPagesPerDocument(sourceCounts.sum() + pageCount)
                pages += DocumentPageEntity(
                    documentId = id,
                    pageIndex = index,
                    encryptedPath = target.absolutePath,
                    sourceFileName = name,
                    mimeType = mime
                )
                sourceCounts += pageCount
            }
            val now = System.currentTimeMillis()
            val logicalPageCount = sourceCounts.sum().coerceAtLeast(pages.size)
            DataOperationCoordinator.withExclusive {
                require(database.documentDao().count() < LibraryLimits.MAX_DOCUMENTS) { "Η βιβλιοθήκη έχει φτάσει το όριο εγγράφων." }
                LibraryLimits.requireTotalLogicalPages(database.documentDao().totalLogicalPageCount() + logicalPageCount)
                require(currentStorageBytes() + totalBytes <= MAX_TOTAL_STORAGE_BYTES) { "Ο ιδιωτικός χώρος εγγράφων έχει φτάσει το όριό του." }
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
            }
            databaseCommitted = true
            if (!enqueueOcr(id)) {
                DataOperationCoordinator.withExclusive {
                    database.documentDao().getById(id)?.let { current ->
                        database.documentDao().update(
                            current.copy(processingError = "Η επεξεργασία OCR θα επαναληφθεί αυτόματα.")
                        )
                    }
                }
            }
            id
        } catch (error: Throwable) {
            if (!databaseCommitted) FileCrypto.deleteRecursively(documentDirectory)
            throw error
        }
    }

    suspend fun updateDocumentMetadata(
        id: String,
        title: String,
        category: String,
        tags: String,
        provider: String,
        issuedDate: String?,
        expiryDate: String?,
        protocolNumber: String?,
        confirmedFields: MetadataFieldConfirmations? = null
    ) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        val reminderData = DataOperationCoordinator.withDocumentExclusive(id) {
            DataOperationCoordinator.withExclusive {
            val cleanTitle = title.trim().ifBlank { "Έγγραφο" }
            val cleanCategory = category.trim().ifBlank { "Άλλα" }
            val cleanTags = tags.trim()
            val cleanProvider = provider.trim()
            val cleanExpiry = expiryDate.cleanDateOrNull("Η ημερομηνία λήξης δεν είναι έγκυρη.")
            val cleanIssued = issuedDate.cleanDateOrNull("Η ημερομηνία έκδοσης δεν είναι έγκυρη.")
            val cleanProtocol = protocolNumber?.trim()?.ifBlank { null }
            LibraryLimits.requireText(cleanTitle, LibraryLimits.MAX_DOCUMENT_TITLE_CHARS, "Ο τίτλος εγγράφου είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(cleanCategory, LibraryLimits.MAX_DOCUMENT_CATEGORY_CHARS, "Η κατηγορία είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(cleanTags, LibraryLimits.MAX_DOCUMENT_TAGS_CHARS, "Οι ετικέτες είναι υπερβολικά μεγάλες.")
            LibraryLimits.requireText(cleanProvider, LibraryLimits.MAX_DOCUMENT_PROVIDER_CHARS, "Ο φορέας είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(cleanProtocol, LibraryLimits.MAX_PROTOCOL_NUMBER_CHARS, "Ο αριθμός πρωτοκόλλου είναι υπερβολικά μεγάλος.")
            val current = database.documentDao().getById(id) ?: error("Το έγγραφο δεν βρέθηκε.")
            val titleChanged = current.title != cleanTitle
            val categoryChanged = current.category != cleanCategory
            val providerChanged = current.provider != cleanProvider
            val issuedChanged = current.issuedDate != cleanIssued
            val expiryChanged = current.expiryDate != cleanExpiry
            val protocolChanged = current.protocolNumber != cleanProtocol
            val confirmed = confirmedFields?.let { requested ->
                MetadataFieldChanges(
                    title = requested.title || titleChanged,
                    category = requested.category || categoryChanged,
                    provider = requested.provider || providerChanged,
                    issuedDate = requested.issuedDate || issuedChanged,
                    expiryDate = requested.expiryDate || expiryChanged,
                    protocolNumber = requested.protocolNumber || protocolChanged
                )
            } ?: MetadataFieldChanges(
                titleChanged,
                categoryChanged,
                providerChanged,
                issuedChanged,
                expiryChanged,
                protocolChanged
            )
            val ownership = MetadataOwnershipPolicy.merge(
                current,
                confirmed
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
                    expiryDateSuggestion = if (confirmed.expiryDate) null else current.expiryDateSuggestion,
                    expiryDateSuggestionConfidence = if (confirmed.expiryDate) MetadataConfidence.NONE else current.expiryDateSuggestionConfidence,
                    titleConfidence = if (confirmed.title) MetadataConfidence.MANUAL else current.titleConfidence,
                    categoryConfidence = if (confirmed.category) MetadataConfidence.MANUAL else current.categoryConfidence,
                    providerConfidence = if (confirmed.provider) MetadataConfidence.MANUAL else current.providerConfidence,
                    issuedDateConfidence = if (confirmed.issuedDate) MetadataConfidence.MANUAL else current.issuedDateConfidence,
                    expiryDateConfidence = if (confirmed.expiryDate) MetadataConfidence.MANUAL else current.expiryDateConfidence,
                    protocolNumberConfidence = if (confirmed.protocolNumber) MetadataConfidence.MANUAL else current.protocolNumberConfidence,
                    titleManuallyEdited = ownership.title,
                    categoryManuallyEdited = ownership.category,
                    providerManuallyEdited = ownership.provider,
                    issuedDateManuallyEdited = ownership.issuedDate,
                    expiryDateManuallyEdited = ownership.expiryDate,
                    protocolNumberManuallyEdited = ownership.protocolNumber,
                    updatedAt = System.currentTimeMillis()
                )
            )
            cleanTitle to cleanExpiry
            }
        }
        runCatching { ReminderScheduler.replaceForDocument(context, id, reminderData.first, reminderData.second) }
            .onFailure { android.util.Log.w("PersonalFolder", "Document reminder reconciliation deferred", it) }
    }

    suspend fun retryOcr(id: String) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        DataOperationCoordinator.withDocumentExclusive(id) {
            DataOperationCoordinator.withExclusive {
                require(database.documentDao().getById(id) != null) { "Το έγγραφο δεν βρέθηκε." }
                database.documentDao().getById(id)?.let { current ->
                    database.documentDao().update(
                        current.copy(processingState = ProcessingState.QUEUED, processingError = null, updatedAt = System.currentTimeMillis())
                    )
                }
            }
            require(enqueueOcr(id)) { "Δεν ήταν δυνατή η τοποθέτηση της επεξεργασίας σε ουρά." }
        }
    }

    suspend fun deleteDocument(id: String) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        DataOperationCoordinator.withDocumentExclusive(id) {
            require(database.documentDao().getById(id) != null) { "Το έγγραφο δεν βρέθηκε." }
            val reminderIds = database.reminderDao().getForDocument(id).map { it.id }
            WorkManager.getInstance(context).cancelUniqueWork("ocr_$id")
            val root = context.filesDir.resolve("documents/$id")
            val quarantine = context.cacheDir.resolve("deleted_documents/${id}_${UUID.randomUUID()}")
            var quarantined = false
            var databaseCommitted = false
            try {
                DataOperationCoordinator.withExclusive {
                    if (root.exists()) {
                        DocumentDeletionRecovery.writeJournal(context, id, root, quarantine, "prepared")
                        quarantine.parentFile?.mkdirs()
                        require(root.renameTo(quarantine)) { "Δεν ήταν δυνατή η προετοιμασία της διαγραφής." }
                        quarantined = true
                        DocumentDeletionRecovery.writeJournal(context, id, root, quarantine, "quarantined")
                    }
                    database.withTransaction {
                        database.documentDao().deleteById(id)
                        // The current schema cascades these rows. Explicitly
                        // cleaning them after the parent delete also repairs old
                        // databases that were created before FK enforcement.
                        database.caseDocumentDao().deleteForDocument(id)
                        database.reminderDao().deleteForDocument(id)
                        database.documentPageDao().deleteForDocument(id)
                    }
                    databaseCommitted = true
                }
                reminderIds.forEach { reminderId ->
                    runCatching { WorkManager.getInstance(context).cancelUniqueWork("reminder_$reminderId") }
                }
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
    ): String {
        DataOperationCoordinator.requireUserSessionUnlocked()
        val result: Triple<String, String, String?> = DataOperationCoordinator.withExclusive {
            val cleanTitle = title.trim().ifBlank { error("Η υπόθεση χρειάζεται τίτλο.") }
            val cleanStart = startDate.cleanDateOrNull("Η ημερομηνία έναρξης δεν είναι έγκυρη.")
            val cleanDeadline = deadline.cleanDateOrNull("Η προθεσμία δεν είναι έγκυρη.")
            LibraryLimits.requireText(cleanTitle, LibraryLimits.MAX_CASE_TITLE_CHARS, "Ο τίτλος υπόθεσης είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(description.trim(), LibraryLimits.MAX_CASE_DESCRIPTION_CHARS, "Η περιγραφή υπόθεσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(nextStep.trim(), LibraryLimits.MAX_CASE_NEXT_STEP_CHARS, "Το επόμενο βήμα είναι υπερβολικά μεγάλο.")
            LibraryLimits.requireText(notes.trim(), LibraryLimits.MAX_CASE_NOTES_CHARS, "Οι σημειώσεις υπόθεσης είναι υπερβολικά μεγάλες.")
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            require(database.caseDao().count() < LibraryLimits.MAX_CASES) { "Η βιβλιοθήκη έχει φτάσει το όριο υποθέσεων." }
            require(database.timelineDao().count() < LibraryLimits.MAX_TIMELINE_EVENTS) { "Η βιβλιοθήκη έχει φτάσει το όριο γεγονότων." }
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
            Triple(id, cleanTitle, cleanDeadline)
        }
        runCatching { ReminderScheduler.replaceForCase(context, result.first, result.second, result.third) }
            .onFailure { android.util.Log.w("PersonalFolder", "Case reminder reconciliation deferred", it) }
        return result.first
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
    ) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        // PF-018 remains a product decision: status changes do not silently
        // alter reminder semantics until the product specifies whether
        // COMPLETED cases should retain deadlines/reminders.
        val reminderData: Pair<String, String?> = DataOperationCoordinator.withExclusive {
            val cleanTitle = title.trim().ifBlank { error("Η υπόθεση χρειάζεται τίτλο.") }
            val cleanStart = startDate.cleanDateOrNull("Η ημερομηνία έναρξης δεν είναι έγκυρη.")
            val cleanDeadline = deadline.cleanDateOrNull("Η προθεσμία δεν είναι έγκυρη.")
            LibraryLimits.requireText(cleanTitle, LibraryLimits.MAX_CASE_TITLE_CHARS, "Ο τίτλος υπόθεσης είναι υπερβολικά μεγάλος.")
            LibraryLimits.requireText(description.trim(), LibraryLimits.MAX_CASE_DESCRIPTION_CHARS, "Η περιγραφή υπόθεσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(status, LibraryLimits.MAX_CASE_STATUS_CHARS, "Η κατάσταση υπόθεσης είναι υπερβολικά μεγάλη.")
            LibraryLimits.requireText(nextStep.trim(), LibraryLimits.MAX_CASE_NEXT_STEP_CHARS, "Το επόμενο βήμα είναι υπερβολικά μεγάλο.")
            LibraryLimits.requireText(notes.trim(), LibraryLimits.MAX_CASE_NOTES_CHARS, "Οι σημειώσεις υπόθεσης είναι υπερβολικά μεγάλες.")
            database.caseDao().update(id, cleanTitle, description.trim(), status, cleanStart, cleanDeadline, nextStep.trim(), notes.trim(), System.currentTimeMillis())
            cleanTitle to cleanDeadline
        }
        runCatching { ReminderScheduler.replaceForCase(context, id, reminderData.first, reminderData.second) }
            .onFailure { android.util.Log.w("PersonalFolder", "Case reminder reconciliation deferred", it) }
    }

    suspend fun deleteCase(id: String) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        val reminderIds = DataOperationCoordinator.withExclusive {
            val ids = database.reminderDao().getForCase(id).map { it.id }
            database.withTransaction {
                database.caseDao().deleteById(id)
                database.reminderDao().deleteForCase(id)
            }
            ids
        }
        reminderIds.forEach { reminderId ->
            runCatching { WorkManager.getInstance(context).cancelUniqueWork("reminder_$reminderId") }
        }
    }

    suspend fun updateCaseStatus(id: String, status: String) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        // Keep COMPLETED reminder behavior unchanged until PF-018 is decided;
        // ARCHIVED is only a display status at present.
        LibraryLimits.requireText(status, LibraryLimits.MAX_CASE_STATUS_CHARS, "Η κατάσταση υπόθεσης είναι υπερβολικά μεγάλη.")
        require(database.timelineDao().count() < LibraryLimits.MAX_TIMELINE_EVENTS) { "Η βιβλιοθήκη έχει φτάσει το όριο γεγονότων." }
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
        DataOperationCoordinator.requireUserSessionUnlocked()
        require(database.caseDao().getById(caseId) != null) { "Η υπόθεση δεν βρέθηκε." }
        require(database.documentDao().getById(documentId) != null) { "Το έγγραφο δεν βρέθηκε." }
        database.caseDocumentDao().insert(CaseDocumentCrossRef(caseId, documentId))
        database.caseDao().getById(caseId)?.let { caseEntity ->
            database.caseDao().updateStatus(caseId, caseEntity.status, System.currentTimeMillis())
        }
    }

    suspend fun detachDocumentFromCase(caseId: String, documentId: String) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        database.caseDocumentDao().delete(caseId, documentId)
    }

    suspend fun addTimelineEvent(caseId: String, title: String, note: String) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        require(database.caseDao().getById(caseId) != null) { "Η υπόθεση δεν βρέθηκε." }
        require(database.timelineDao().count() < LibraryLimits.MAX_TIMELINE_EVENTS) { "Η βιβλιοθήκη έχει φτάσει το όριο γεγονότων." }
        LibraryLimits.requireText(title.trim(), LibraryLimits.MAX_EVENT_TITLE_CHARS, "Ο τίτλος γεγονότος είναι υπερβολικά μεγάλος.")
        LibraryLimits.requireText(note.trim(), LibraryLimits.MAX_EVENT_NOTE_CHARS, "Η σημείωση γεγονότος είναι υπερβολικά μεγάλη.")
        database.timelineDao().insert(
            TimelineEventEntity(
                id = UUID.randomUUID().toString(), caseId = caseId, title = title.trim(), note = note.trim(),
                eventDate = LocalDate.now().toString(), createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addChecklistItem(caseId: String, title: String, linkedDocumentId: String? = null) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        require(database.caseDao().getById(caseId) != null) { "Η υπόθεση δεν βρέθηκε." }
        require(database.checklistDao().count() < LibraryLimits.MAX_CHECKLIST_ITEMS) { "Η βιβλιοθήκη έχει φτάσει το όριο λίστας." }
        LibraryLimits.requireText(title.trim(), LibraryLimits.MAX_CHECKLIST_TITLE_CHARS, "Ο τίτλος checklist είναι υπερβολικά μεγάλος.")
        if (linkedDocumentId != null) require(database.documentDao().getById(linkedDocumentId) != null) { "Το έγγραφο δεν βρέθηκε." }
        database.checklistDao().insert(ChecklistItemEntity(UUID.randomUUID().toString(), caseId, title.trim(), linkedDocumentId = linkedDocumentId, createdAt = System.currentTimeMillis()))
    }

    suspend fun setChecklistComplete(id: String, complete: Boolean) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        database.checklistDao().setComplete(id, complete)
    }

    suspend fun linkChecklistDocument(id: String, documentId: String?) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        if (documentId != null) require(database.documentDao().getById(documentId) != null) { "Το έγγραφο δεν βρέθηκε." }
        database.checklistDao().linkDocument(id, documentId)
    }

    suspend fun deleteChecklistItem(id: String) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        database.checklistDao().deleteById(id)
    }

    suspend fun markReminderDone(id: String) = DataOperationCoordinator.withExclusive {
        DataOperationCoordinator.requireUserSessionUnlocked()
        database.reminderDao().markDone(id)
    }

    suspend fun reconcileQueuedOcr() {
        DataOperationCoordinator.withExclusiveDuringStartup {
            database.documentDao().getByProcessingState(ProcessingState.QUEUED)
                .take(LibraryLimits.MAX_DOCUMENTS)
                .forEach { document ->
                    if (!enqueueOcr(document.id)) {
                        database.documentDao().update(
                            document.copy(processingError = "Η επεξεργασία OCR θα επαναληφθεί αυτόματα.")
                        )
                    }
                }
        }
    }

    private suspend fun enqueueOcr(id: String): Boolean = runCatching {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_DOCUMENT_ID to id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("document-processing")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("ocr_$id", ExistingWorkPolicy.REPLACE, request)
    }.isSuccess

    private fun countEncryptedSourcePages(encrypted: File, mime: String, name: String): Int {
        if (!mime.equals("application/pdf", true) && !name.endsWith(".pdf", true)) return 1
        val temp = File(context.cacheDir, "ocr/import_${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
        return try {
            FileCrypto.decryptToTemp(encrypted, temp)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount.coerceAtLeast(1).also { count -> LibraryLimits.requireLogicalPagesPerDocument(count) } }
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
            require(bounds.outWidth <= MAX_IMAGE_SIDE && bounds.outHeight <= MAX_IMAGE_SIDE) {
                "Η εικόνα «$name» έχει υπερβολική ανάλυση."
            }
            require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) {
                "Η εικόνα «$name» είναι υπερβολικά μεγάλη."
            }
            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > IMAGE_VALIDATION_SIDE) sample *= 2
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }) ?: error("Η εικόνα «$name» δεν αποκωδικοποιείται.")
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
            database.documentDao().getAllEncryptedPaths().forEach(::add)
            database.documentPageDao().getAllEncryptedPaths().forEach(::add)
        }
        return paths.sumOf { path -> File(path).takeIf { it.isFile }?.length() ?: 0L }
    }

    private companion object {
        const val MAX_DOCUMENT_BYTES = 512L * 1024 * 1024
        const val MAX_TOTAL_STORAGE_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_IMAGE_SIDE = 12_000
        const val MAX_IMAGE_PIXELS = 50_000_000L
        const val IMAGE_VALIDATION_SIDE = 3_200
    }
}
