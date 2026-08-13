package com.angel.personalfolder.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.angel.personalfolder.security.BackupCrypto
import com.angel.personalfolder.security.FileCrypto
import com.angel.personalfolder.workers.OcrWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import android.os.ParcelFileDescriptor
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupService(private val context: Context) {
    private val database by lazy { AppDatabase.get(context) }

    /** Repairs a restore interrupted between the filesystem swap and the Room transaction. */
    suspend fun recoverInterruptedRestore() = withContext(Dispatchers.IO) {
        DataOperationCoordinator.withExclusiveDuringStartup {
            val journalFile = context.filesDir.resolve(RESTORE_JOURNAL)
            if (!journalFile.isFile) return@withExclusiveDuringStartup
            val journal = runCatching { JSONObject(journalFile.readText(Charsets.UTF_8)) }
                .getOrElse { throw IllegalStateException("Το ημερολόγιο επαναφοράς δεν είναι έγκυρο.", it) }
            val root = safeJournalFile(journal.optString("root"), context.filesDir.resolve("documents"))
            val previous = safeJournalFile(journal.optString("previousRoot"), context.cacheDir)
            val staging = safeJournalFile(journal.optString("stagingRoot"), context.cacheDir)
            require(root != null && previous != null && staging != null) {
                "Το ημερολόγιο επαναφοράς περιέχει μη ασφαλείς διαδρομές."
            }
            val expectedIds = buildSet {
                val ids = journal.optJSONArray("documentIds") ?: return@buildSet
                for (index in 0 until ids.length()) add(ids.optString(index))
            }
            val currentDocuments = database.documentDao().getAll()
            val currentPages = database.documentPageDao().getAll()
            val currentCases = database.caseDao().getAll()
            val currentRelations = database.caseDocumentDao().getAll()
            val currentEvents = database.timelineDao().getAll()
            val currentChecklist = database.checklistDao().getAll()
            val currentReminders = database.reminderDao().getAll()
            val expectedDatabaseFingerprint = journal.optString("databaseFingerprint")
            val expectedFilesystemFingerprint = journal.optString("filesystemFingerprint")
            val currentIds = currentDocuments.map { it.id }.toSet()
            val databaseGenerationMatches = expectedDatabaseFingerprint.isNotBlank() &&
                expectedDatabaseFingerprint == RestoreGenerationFingerprint.of(
                    currentDocuments,
                    currentPages,
                    currentCases,
                    currentRelations,
                    currentEvents,
                    currentChecklist,
                    currentReminders
                )
            val state = RestoreRecoveryState(
                phase = journal.optString("phase"),
                currentDocumentIds = currentIds,
                expectedDocumentIds = expectedIds,
                rootExists = root.isDirectory,
                previousRootExists = previous.isDirectory,
                stagingRootExists = staging.isDirectory,
                rootMatchesExpected = expectedFilesystemFingerprint.isNotBlank() &&
                    expectedFilesystemFingerprint == RestoreGenerationFingerprint.filesystemOf(root),
                databaseGenerationMatches = databaseGenerationMatches
            )
            when (RestoreRecoveryPolicy.decide(state)) {
                RestoreRecoveryAction.FINALIZE_NEW_GENERATION -> {
                    FileCrypto.deleteRecursivelyStrict(previous)
                    FileCrypto.deleteRecursivelyStrict(staging)
                    require(!previous.exists() && !staging.exists()) { "Δεν ολοκληρώθηκε ο καθαρισμός της επαναφοράς." }
                    journalFile.delete()
                }

                RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION -> {
                    require(previous.isDirectory) { "Δεν υπάρχει ασφαλές προηγούμενο αντίγραφο για επαναφορά." }
                    if (root.exists()) FileCrypto.deleteRecursivelyStrict(root)
                    require(previous.renameTo(root)) { "Δεν ήταν δυνατή η επαναφορά της προηγούμενης γενιάς." }
                    FileCrypto.deleteRecursivelyStrict(staging)
                    journalFile.delete()
                }

                RestoreRecoveryAction.PRESERVE_AND_RETRY -> {
                    // Never delete the current root when there is no proven
                    // replacement or previous generation. The journal remains
                    // so the next startup can make the same evidence-based decision.
                    // A prepared journal with the old root still live has not
                    // crossed the swap boundary; its staging tree is disposable.
                    if (journal.optString("phase") == "prepared" && root.isDirectory && !previous.isDirectory && staging.exists()) {
                        FileCrypto.deleteRecursivelyStrict(staging)
                        journalFile.delete()
                    } else {
                        throw IllegalStateException(
                            "Η επαναφορά άφησε αμφίσημη γενιά δεδομένων. Η λειτουργία παραμένει κλειδωμένη."
                        )
                    }
                }
            }
        }
    }

    suspend fun create(destination: Uri, password: String) = withContext(Dispatchers.IO) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        require(password.length >= MIN_NEW_BACKUP_PASSWORD_LENGTH) { "Ο νέος κωδικός backup πρέπει να έχει τουλάχιστον $MIN_NEW_BACKUP_PASSWORD_LENGTH χαρακτήρες." }
        val (payload, pages) = DataOperationCoordinator.withExclusive {
            snapshot() to database.documentPageDao().getAll()
        }
        val zip = context.cacheDir.resolve("backup/personal_folder_${System.currentTimeMillis()}.zip").apply {
            parentFile?.mkdirs()
        }
        try {
            var payloadBytes = 0L
            ZipOutputStream(FileOutputStream(zip)).use { output ->
                    output.putNextEntry(ZipEntry("backup.json"))
                    val manifestBytes = payload.toString().toByteArray(Charsets.UTF_8)
                    BackupSizePolicy.requireManifestSize(manifestBytes.size.toLong())
                    payloadBytes += manifestBytes.size
                    require(payloadBytes <= MAX_BACKUP_PAYLOAD_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
                    output.write(manifestBytes)
                    output.closeEntry()
                    pages.distinctBy { it.documentId to it.pageIndex }.forEach { page ->
                        DataOperationCoordinator.withDocumentExclusive(page.documentId) {
                        val source = File(page.encryptedPath)
                        require(FileCrypto.isPrivateDocumentFile(context, source)) { "Η σελίδα βρίσκεται εκτός του ιδιωτικού χώρου." }
                        require(source.isFile) { "Λείπει σελίδα από το έγγραφο ${page.documentId}." }
                        val plain = context.cacheDir.resolve("backup/plain_${UUID.randomUUID()}.tmp").apply { parentFile?.mkdirs() }
                        try {
                            FileCrypto.decryptToTemp(source, plain)
                            output.putNextEntry(ZipEntry("files/${page.documentId}/${page.pageIndex}.pf"))
                            val copied = FileInputStream(plain).use { it.copyLimitedTo(output, MAX_BACKUP_ENTRY_BYTES) }
                            payloadBytes += copied
                            require(payloadBytes <= MAX_BACKUP_PAYLOAD_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
                            output.closeEntry()
                        } finally {
                            plain.delete()
                        }
                        }
                    }
                }
            BackupSizePolicy.requireArchiveSize(zip.length())
            BackupCrypto.encryptFile(zip, context, destination, password.toCharArray())
            true
        } finally {
            zip.delete()
        }
    }

    suspend fun restore(source: Uri, password: String) = withContext(Dispatchers.IO) {
        DataOperationCoordinator.requireUserSessionUnlocked()
        require(password.length >= MIN_RESTORE_PASSWORD_LENGTH) { "Ο κωδικός επαναφοράς πρέπει να έχει τουλάχιστον $MIN_RESTORE_PASSWORD_LENGTH χαρακτήρες." }
        WorkManager.getInstance(context).cancelAllWorkByTag("document-processing")
        OcrWorker.awaitIdle()
        val zip = context.cacheDir.resolve("backup/restore_${System.currentTimeMillis()}.zip").apply {
            parentFile?.mkdirs()
        }
        try {
            BackupCrypto.decryptToFile(context, source, zip, password.toCharArray())
            restoreZip(zip)
        } finally {
            zip.delete()
        }
    }

    private suspend fun snapshot(): JSONObject {
        return database.withTransaction {
            // Check scalar counts and OCR volume before materialising any
            // entity collection or JSON tree. The old implementation first
            // loaded the entire library and only then discovered an invalid
            // or oversized backup state.
            val documentCount = database.documentDao().count()
            val pageCount = database.documentPageDao().count()
            val caseCount = database.caseDao().count()
            val eventCount = database.timelineDao().count()
            val checklistCount = database.checklistDao().count()
            val reminderCount = database.reminderDao().count()
            BackupSizePolicy.requireLibraryState(
                documents = documentCount,
                pages = pageCount,
                cases = caseCount,
                events = eventCount,
                checklist = checklistCount,
                reminders = reminderCount,
                totalOcrChars = database.documentDao().totalDocumentOcrChars() + database.documentPageDao().totalOcrChars()
            )
            val documents = database.documentDao().getAll()
            val pages = database.documentPageDao().getAll()
            val cases = database.caseDao().getAll()
            val relations = database.caseDocumentDao().getAll()
            val events = database.timelineDao().getAll()
            val checklist = database.checklistDao().getAll()
            val reminders = database.reminderDao().getAll()
            BackupSizePolicy.requireDocumentShapes(documents, pages)
            BackupSizePolicy.requireLibraryState(
                documents = documents.size,
                pages = pages.size,
                cases = cases.size,
                events = events.size,
                checklist = checklist.size,
                reminders = reminders.size,
                totalOcrChars = documents.sumOf { it.ocrText.length.toLong() } + pages.sumOf { it.ocrText.length.toLong() }
            )
            JSONObject().apply {
                put("formatVersion", 4)
                put("createdAt", System.currentTimeMillis())
                put("documents", JSONArray().apply { documents.forEach { put(documentJson(it)) } })
                put("pages", JSONArray().apply { pages.forEach { put(pageJson(it)) } })
                put("cases", JSONArray().apply { cases.forEach { put(caseJson(it)) } })
                put("relations", JSONArray().apply { relations.forEach { put(JSONObject().put("caseId", it.caseId).put("documentId", it.documentId)) } })
                put("events", JSONArray().apply { events.forEach { put(eventJson(it)) } })
                put("checklist", JSONArray().apply { checklist.forEach { put(checklistJson(it)) } })
                put("reminders", JSONArray().apply { reminders.forEach { put(reminderJson(it)) } })
            }
        }
    }

    private suspend fun restoreZip(zip: File) {
        val archive = inspectArchive(zip)
        val manifest = JSONObject(archive.manifest)
        val formatVersion = manifest.optInt("formatVersion", -1)
        require(formatVersion in 1..4) { "Η έκδοση του αντιγράφου δεν υποστηρίζεται." }
        val portableFiles = formatVersion >= 2
        val pageDescriptors = parsePageDescriptors(manifest.optJSONArray("pages"))
        val expectedFiles = pageDescriptors.map { it.entryName }.toSet()
        // Do not generate a replacement key after a Keystore loss when the
        // current device still has encrypted library files. A new device with
        // an empty library may create its first key for portable restore.
        FileCrypto.ensureKeyAvailableForNewDocument(context)
        val stagingRoot = context.cacheDir.resolve("restore_documents_${UUID.randomUUID()}").apply { mkdirs() }
        val root = context.filesDir.resolve("documents")
        val previousRoot = context.cacheDir.resolve("previous_documents_${UUID.randomUUID()}")
        val currentDocumentIds = database.documentDao().getAllIds().toSet()
        // A missing live root is safe to replace only for an empty current
        // database. If Room still contains documents, replacing the missing
        // tree would create an unrecoverable mixed generation.
        require(root.isDirectory || currentDocumentIds.isEmpty()) {
            "Δεν υπάρχει ασφαλής προηγούμενη γενιά εγγράφων για επαναφορά."
        }
        val stagedFiles = mutableMapOf<String, File>()
        var previousMoved = false
        var filesInstalled = false
        var databaseCommitted = false
        try {
            ZipInputStream(FileInputStream(zip)).use { input ->
                var entry = input.nextEntry
                val seen = mutableSetOf<String>()
                while (entry != null) {
                    val name = validateEntryName(entry.name)
                    require(seen.add(name)) { "Το αντίγραφο περιέχει διπλό αρχείο." }
                    if (!entry.isDirectory && name in expectedFiles) {
                        val descriptor = pageDescriptors.first { it.entryName == name }
                        val staged = stagingRoot.resolve("${descriptor.documentId}/page_${descriptor.pageIndex}.pf")
                        staged.parentFile?.mkdirs()
                        if (portableFiles) FileCrypto.encrypt(input, staged, MAX_BACKUP_ENTRY_BYTES)
                        else staged.outputStream().use { input.copyLimitedTo(it, MAX_BACKUP_ENTRY_BYTES) }
                        stagedFiles[name] = staged
                    } else if (!entry.isDirectory) {
                        input.discardLimited(MAX_BACKUP_ENTRY_BYTES)
                    }
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
            require(stagedFiles.keys == expectedFiles) { "Το αντίγραφο είναι ελλιπές ή δεν περιέχει όλες τις σελίδες." }
            validateStagedPageCounts(stagedFiles, pageDescriptors)

            val documents = parseDocuments(manifest.optJSONArray("documents"), pageDescriptors, stagedFiles, root)
            val documentIds = documents.map { it.id }.toSet()
            require(pageDescriptors.all { it.documentId in documentIds }) { "Το αντίγραφο περιέχει σελίδα άγνωστου εγγράφου." }
            val cases = parseCases(manifest.optJSONArray("cases"))
            val caseIds = cases.map { it.id }.toSet()
            val relations = parseRelations(manifest.optJSONArray("relations"), caseIds, documentIds)
            val events = parseEvents(manifest.optJSONArray("events"), caseIds)
            val checklist = parseChecklist(manifest.optJSONArray("checklist"), caseIds, documentIds)
            val confirmedExpiryDocumentIds = documents.asSequence()
                .filter { MetadataConfidence.isConfirmed(it.expiryDate, it.expiryDateConfidence, it.expiryDateManuallyEdited) }
                .map { it.id }
                .toSet()
            val reminders = parseReminders(manifest.optJSONArray("reminders"), caseIds, documentIds, confirmedExpiryDocumentIds)
            BackupSizePolicy.requireLibraryState(
                documents = documents.size,
                pages = pageDescriptors.size,
                cases = cases.size,
                events = events.size,
                checklist = checklist.size,
                reminders = reminders.size,
                totalOcrChars = documents.sumOf { it.ocrText.length.toLong() } +
                    pageDescriptors.sumOf { it.ocrText.length.toLong() }
            )
            val restoredPages = documents.flatMap { document ->
                pageDescriptors.filter { it.documentId == document.id }.map { descriptor ->
                    DocumentPageEntity(
                        documentId = descriptor.documentId,
                        pageIndex = descriptor.pageIndex,
                        encryptedPath = root.resolve("${descriptor.documentId}/page_${descriptor.pageIndex}.pf").absolutePath,
                        ocrText = descriptor.ocrText,
                        sourceFileName = descriptor.sourceFileName,
                        mimeType = descriptor.mimeType
                    )
                }
            }
            val expectedDatabaseFingerprint = RestoreGenerationFingerprint.of(
                documents,
                restoredPages,
                cases,
                relations,
                events,
                checklist,
                reminders
            )
            val expectedFilesystemFingerprint = RestoreGenerationFingerprint.filesystemOf(stagingRoot)
                ?: error("Δεν ήταν δυνατή η επαλήθευση των αρχείων επαναφοράς.")

            writeRestoreJournal(
                phase = "prepared",
                root = root,
                previousRoot = previousRoot,
                stagingRoot = stagingRoot,
                documentIds = documentIds,
                databaseFingerprint = expectedDatabaseFingerprint,
                filesystemFingerprint = expectedFilesystemFingerprint
            )

            // Parsing, decrypting and PDF validation above do not hold the
            // process-wide mutex. Only the filesystem swap and Room commit
            // are serialized as one short generation transition.
            DataOperationCoordinator.withExclusive {
                previousMoved = if (root.exists()) {
                    require(root.isDirectory) { "Ο χώρος εγγράφων δεν είναι έγκυρος κατάλογος." }
                    require(root.renameTo(previousRoot)) { "Δεν ήταν δυνατή η προετοιμασία της επαναφοράς." }
                    true
                } else {
                    require(previousRoot.mkdirs()) { "Δεν ήταν δυνατή η δημιουργία ασφαλούς προηγούμενης γενιάς." }
                    true
                }
                require(!root.exists()) { "Δεν ήταν δυνατή η προετοιμασία της επαναφοράς." }
                require(stagingRoot.renameTo(root)) { "Δεν ήταν δυνατή η εγκατάσταση των αρχείων επαναφοράς." }
                filesInstalled = true
                writeRestoreJournal(
                    phase = "files_installed",
                    root = root,
                    previousRoot = previousRoot,
                    stagingRoot = stagingRoot,
                    documentIds = documentIds,
                    databaseFingerprint = expectedDatabaseFingerprint,
                    filesystemFingerprint = expectedFilesystemFingerprint
                )

                database.withTransaction {
                    database.caseDocumentDao().deleteAll()
                    database.timelineDao().deleteAll()
                    database.checklistDao().deleteAll()
                    database.reminderDao().deleteAll()
                    database.documentPageDao().deleteAll()
                    database.documentDao().deleteAll()
                    database.caseDao().deleteAll()
                    database.documentDao().insertAll(documents)
                    database.documentPageDao().insertAll(restoredPages)
                    database.caseDao().insertAll(cases)
                    relations.forEach { database.caseDocumentDao().insert(it) }
                    events.forEach { database.timelineDao().insert(it) }
                    checklist.forEach { database.checklistDao().insert(it) }
                    reminders.forEach { database.reminderDao().insert(it) }
                }
                databaseCommitted = true
                // If this write fails, the new DB and files are deliberately
                // kept; startup recovery will finalize them instead of
                // rolling them back.
                writeRestoreJournal(
                    phase = "database_committed",
                    root = root,
                    previousRoot = previousRoot,
                    stagingRoot = stagingRoot,
                    documentIds = documentIds,
                    databaseFingerprint = expectedDatabaseFingerprint,
                    filesystemFingerprint = expectedFilesystemFingerprint
                )
            }
            ReminderScheduler.rescheduleAll(context)
            FileCrypto.deleteRecursivelyStrict(previousRoot)
            clearRestoreJournal()
        } catch (error: Throwable) {
            if (!databaseCommitted && filesInstalled) {
                if (previousMoved) {
                    val rollbackSucceeded = runCatching {
                        FileCrypto.deleteRecursivelyStrict(root)
                        require(previousRoot.renameTo(root)) { "Δεν ήταν δυνατή η επαναφορά της προηγούμενης γενιάς." }
                        clearRestoreJournal()
                    }.isSuccess
                    if (!rollbackSucceeded) {
                        // Keep the journal: deleting it here would make the
                        // only remaining recovery evidence disappear after a
                        // process death.
                    }
                } else {
                    // There was no proven previous filesystem generation.
                    // Preserve the installed replacement and its journal
                    // instead of deleting the only available copy.
                }
            } else if (!databaseCommitted && !filesInstalled) {
                if (previousMoved) {
                    // The process may fail after moving the old root but
                    // before installing the staged root. Restore that old
                    // generation before removing the prepared journal.
                    val restored = runCatching {
                        require(!root.exists()) { "Το παλιό αρχείο επαναφοράς δεν βρίσκεται στη σωστή θέση." }
                        require(previousRoot.renameTo(root)) { "Δεν ήταν δυνατή η επαναφορά της προηγούμενης γενιάς." }
                        clearRestoreJournal()
                    }.isSuccess
                    if (!restored) {
                        // Keep the journal so startup recovery can retry.
                    }
                } else {
                    // The live root was never moved or replaced, so clearing
                    // the prepared journal is safe and avoids pinning a
                    // disposable staging tree after a validation failure.
                    clearRestoreJournal()
                }
            }
            throw error
        } finally {
            // This is disposable staging only. The live root and previous root
            // are handled by the journal protocol above.
            if (stagingRoot.exists()) FileCrypto.deleteRecursively(stagingRoot)
        }
    }

    private fun parseDocuments(
        array: JSONArray?,
        pages: List<PageDescriptor>,
        stagedFiles: Map<String, File>,
        root: File
    ): List<DocumentEntity> {
        require(checkedLength(array) <= LibraryLimits.MAX_DOCUMENTS) { "Το αντίγραφο περιέχει υπερβολικά πολλά έγγραφα." }
        val result = mutableListOf<DocumentEntity>()
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val item = array!!.getJSONObject(i)
            val id = requireSafeId(item.getString("id"))
            require(seen.add(id)) { "Το αντίγραφο περιέχει διπλό έγγραφο." }
            val documentPages = pages.filter { it.documentId == id }
            require(documentPages.isNotEmpty()) { "Το έγγραφο δεν έχει σελίδες." }
            require(documentPages.size <= LibraryLimits.MAX_DOCUMENT_SOURCES) { "Το έγγραφο περιέχει υπερβολικά πολλές πηγές." }
            require(documentPages.all { it.entryName in stagedFiles }) { "Λείπει αρχείο από το έγγραφο." }
            val pageCount = item.optInt("pageCount", documentPages.size)
            LibraryLimits.requireLogicalPagesPerDocument(pageCount)
            val legacyGlobalManual = item.optBoolean("metadataManuallyEdited", false)
            val expiryManuallyEdited = item.optBoolean("expiryDateManuallyEdited", legacyGlobalManual)
            val rawExpiry = item.optNullableString("expiryDate")
            val rawExpiryConfidence = item.optString(
                "expiryDateConfidence",
                if (expiryManuallyEdited) MetadataConfidence.MANUAL else MetadataConfidence.UNKNOWN
            )
            val confirmedExpiry = rawExpiry?.takeIf {
                MetadataConfidence.isConfirmed(it, rawExpiryConfidence, expiryManuallyEdited)
            }
            val expirySuggestion = item.optNullableString("expiryDateSuggestion")
                ?: rawExpiry?.takeUnless { it == confirmedExpiry }
            val expirySuggestionConfidence = item.optString(
                "expiryDateSuggestionConfidence",
                if (expirySuggestion != null) MetadataConfidence.LOW else MetadataConfidence.NONE
            )
            val titleManuallyEdited = item.optBoolean("titleManuallyEdited", legacyGlobalManual)
            val categoryManuallyEdited = item.optBoolean("categoryManuallyEdited", legacyGlobalManual)
            val providerManuallyEdited = item.optBoolean("providerManuallyEdited", legacyGlobalManual)
            val issuedDateManuallyEdited = item.optBoolean("issuedDateManuallyEdited", legacyGlobalManual)
            val protocolNumberManuallyEdited = item.optBoolean("protocolNumberManuallyEdited", legacyGlobalManual)
            result += DocumentEntity(
                id = id,
                title = item.getString("title").take(200),
                originalFileName = item.getString("originalFileName").take(200),
                mimeType = item.getString("mimeType").take(120),
                encryptedPath = root.resolve("$id/page_${documentPages.minOf { it.pageIndex }}.pf").absolutePath,
                pageCount = pageCount.coerceAtLeast(documentPages.size),
                category = item.optString("category", "Άλλα").take(120),
                tags = item.optString("tags").take(500),
                provider = item.optString("provider").take(200),
                issuedDate = item.optNullableString("issuedDate"),
                expiryDate = confirmedExpiry,
                protocolNumber = item.optNullableString("protocolNumber"),
                ocrText = item.optString("ocrText").take(MAX_OCR_TEXT),
                extractedMetadataJson = item.optString("extractedMetadataJson").take(MAX_METADATA_JSON),
                processingState = safeProcessingState(item.optString("processingState", ProcessingState.PROCESSED)),
                processingError = item.optNullableString("processingError") ?: item.optString("processingState").let {
                    if (it == ProcessingState.QUEUED || it == ProcessingState.PROCESSING) "Η επεξεργασία δεν συνεχίστηκε μετά την επαναφορά. Επίλεξε επανάληψη OCR." else null
                },
                metadataManuallyEdited = legacyGlobalManual || titleManuallyEdited || categoryManuallyEdited || providerManuallyEdited || issuedDateManuallyEdited || expiryManuallyEdited || protocolNumberManuallyEdited,
                expiryDateSuggestion = expirySuggestion,
                expiryDateSuggestionConfidence = expirySuggestionConfidence,
                titleConfidence = item.optString("titleConfidence", MetadataConfidence.UNKNOWN),
                categoryConfidence = item.optString("categoryConfidence", MetadataConfidence.UNKNOWN),
                providerConfidence = item.optString("providerConfidence", MetadataConfidence.UNKNOWN),
                issuedDateConfidence = item.optString("issuedDateConfidence", MetadataConfidence.UNKNOWN),
                expiryDateConfidence = if (confirmedExpiry == null) MetadataConfidence.UNKNOWN else rawExpiryConfidence,
                protocolNumberConfidence = item.optString("protocolNumberConfidence", MetadataConfidence.UNKNOWN),
                titleManuallyEdited = titleManuallyEdited,
                categoryManuallyEdited = categoryManuallyEdited,
                providerManuallyEdited = providerManuallyEdited,
                issuedDateManuallyEdited = issuedDateManuallyEdited,
                expiryDateManuallyEdited = expiryManuallyEdited,
                protocolNumberManuallyEdited = protocolNumberManuallyEdited,
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return result
    }

    private fun validateStagedPageCounts(stagedFiles: Map<String, File>, descriptors: List<PageDescriptor>) {
        var logicalPages = 0
        val logicalPagesByDocument = mutableMapOf<String, Int>()
        descriptors.forEach { descriptor ->
            val isPdf = descriptor.mimeType.equals("application/pdf", true) || descriptor.sourceFileName.endsWith(".pdf", true)
            val count = if (!isPdf) {
                1
            } else {
                val plain = context.cacheDir.resolve("backup/validate_${UUID.randomUUID()}.tmp").apply { parentFile?.mkdirs() }
                try {
                    FileCrypto.decryptToTemp(stagedFiles.getValue(descriptor.entryName), plain)
                    ParcelFileDescriptor.open(plain, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { it.pageCount.coerceAtLeast(1) }
                    }
                } finally {
                    plain.delete()
                }
            }
            val documentTotal = (logicalPagesByDocument[descriptor.documentId] ?: 0) + count
            require(count <= LibraryLimits.MAX_LOGICAL_PAGES_PER_DOCUMENT &&
                documentTotal <= LibraryLimits.MAX_LOGICAL_PAGES_PER_DOCUMENT &&
                logicalPages + count <= LibraryLimits.MAX_TOTAL_LOGICAL_PAGES
            ) {
                "Το αντίγραφο περιέχει υπερβολικά πολλές λογικές σελίδες."
            }
            logicalPagesByDocument[descriptor.documentId] = documentTotal
            logicalPages += count
        }
    }

    private fun parsePageDescriptors(array: JSONArray?): List<PageDescriptor> = buildList {
        require(checkedLength(array) <= LibraryLimits.MAX_TOTAL_LOGICAL_PAGES) { "Το αντίγραφο περιέχει υπερβολικά πολλές πηγές." }
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i)
            val documentId = requireSafeId(x.getString("documentId"))
            val pageIndex = x.getInt("pageIndex").also { require(it in 0..MAX_PAGE_INDEX) }
            val entryName = "files/$documentId/$pageIndex.pf"
            require(seen.add(entryName)) { "Το αντίγραφο περιέχει διπλή σελίδα." }
            add(PageDescriptor(documentId, pageIndex, entryName, x.optString("ocrText").take(MAX_OCR_TEXT), x.optString("sourceFileName").take(200), x.optString("mimeType", "application/octet-stream").take(120)))
        }
    }

    private fun parseCases(array: JSONArray?): List<CaseEntity> = buildList {
        require(checkedLength(array) <= LibraryLimits.MAX_CASES)
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i)
            val id = requireSafeId(x.getString("id"))
            require(seen.add(id)) { "Το αντίγραφο περιέχει διπλή υπόθεση." }
            add(CaseEntity(id, x.getString("title").take(200), x.optString("description").take(2000), x.optString("status", CaseStatus.NEW).take(80), x.optNullableString("startDate"), x.optNullableString("deadline"), x.optString("nextStep").take(500), x.optString("notes").take(5000), x.optLong("createdAt", System.currentTimeMillis()), x.optLong("updatedAt", System.currentTimeMillis())))
        }
    }

    private fun parseRelations(array: JSONArray?, cases: Set<String>, documents: Set<String>): List<CaseDocumentCrossRef> = buildList {
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i)
            val caseId = requireSafeId(x.getString("caseId")); val documentId = requireSafeId(x.getString("documentId"))
            require(caseId in cases && documentId in documents) { "Το αντίγραφο περιέχει ασύνδετη σχέση." }
            require(seen.add("$caseId:$documentId")) { "Το αντίγραφο περιέχει διπλή σχέση." }
            add(CaseDocumentCrossRef(caseId, documentId))
        }
    }

    private fun parseEvents(array: JSONArray?, cases: Set<String>): List<TimelineEventEntity> = buildList {
        require(checkedLength(array) <= LibraryLimits.MAX_TIMELINE_EVENTS)
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i); val caseId = requireSafeId(x.getString("caseId"))
            require(caseId in cases) { "Το αντίγραφο περιέχει γεγονός άγνωστης υπόθεσης." }
            val id = requireSafeId(x.getString("id")); require(seen.add(id)) { "Το αντίγραφο περιέχει διπλό γεγονός." }
            add(TimelineEventEntity(id, caseId, x.getString("title").take(300), x.optString("note").take(5000), x.optString("eventType", "manual").take(50), x.getString("eventDate"), x.optLong("createdAt", System.currentTimeMillis())))
        }
    }

    private fun parseChecklist(array: JSONArray?, cases: Set<String>, documents: Set<String>): List<ChecklistItemEntity> = buildList {
        require(checkedLength(array) <= LibraryLimits.MAX_CHECKLIST_ITEMS)
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i); val caseId = requireSafeId(x.getString("caseId")); val linked = x.optNullableString("linkedDocumentId")?.let(::requireSafeId)
            require(caseId in cases && (linked == null || linked in documents)) { "Το αντίγραφο περιέχει άκυρο δικαιολογητικό." }
            val id = requireSafeId(x.getString("id")); require(seen.add(id)) { "Το αντίγραφο περιέχει διπλό δικαιολογητικό." }
            add(ChecklistItemEntity(id, caseId, x.getString("title").take(500), x.optBoolean("isComplete"), linked, x.optLong("createdAt", System.currentTimeMillis())))
        }
    }

    private fun parseReminders(
        array: JSONArray?,
        cases: Set<String>,
        documents: Set<String>,
        confirmedExpiryDocuments: Set<String>
    ): List<ReminderEntity> = buildList {
        require(checkedLength(array) <= LibraryLimits.MAX_REMINDERS) { "Το αντίγραφο περιέχει υπερβολικά πολλές υπενθυμίσεις." }
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i); val documentId = x.optNullableString("documentId")?.let(::requireSafeId); val caseId = x.optNullableString("caseId")?.let(::requireSafeId)
            require((documentId == null || documentId in documents) && (caseId == null || caseId in cases)) { "Το αντίγραφο περιέχει άκυρη υπενθύμιση." }
            val id = requireSafeId(x.getString("id")); require(seen.add(id)) { "Το αντίγραφο περιέχει διπλή υπενθύμιση." }
            val dueAt = x.getLong("dueAt")
            require(dueAt != Long.MIN_VALUE) { "Η υπενθύμιση έχει μη έγκυρη ημερομηνία." }
            val leadDays = x.optInt("leadDays").coerceIn(0, 3650)
            val deadlineAt = x.optLong("deadlineAt", dueAt.saturatingAdd(leadDays.toLong() * 86_400_000L))
            // Validate the complete legacy/current row first. Only then
            // discard a document reminder whose expiry is still a suggestion;
            // malformed backup data must never be hidden by that policy.
            if (documentId != null && documentId !in confirmedExpiryDocuments) continue
            add(ReminderEntity(id, x.getString("title").take(500), dueAt, documentId, caseId, leadDays, x.optBoolean("isDone"), deadlineAt))
        }
    }

    private fun safeProcessingState(value: String): String = when (value) {
        ProcessingState.PROCESSED, ProcessingState.FAILED -> value
        else -> ProcessingState.FAILED
    }

    private fun inspectArchive(zip: File): ArchiveInfo {
        var totalBytes = 0L
        var manifest: String? = null
        val names = mutableSetOf<String>()
        ZipInputStream(FileInputStream(zip)).use { input ->
            var entry = input.nextEntry
            var count = 0
            while (entry != null) {
                require(++count <= BackupSizePolicy.MAX_ARCHIVE_ENTRIES) { "Το αντίγραφο περιέχει υπερβολικά πολλά αρχεία." }
                val name = validateEntryName(entry.name)
                require(names.add(name)) { "Το αντίγραφο περιέχει διπλό αρχείο." }
                if (!entry.isDirectory) {
                    val data = if (name == "backup.json") input.readLimited(BackupSizePolicy.MAX_MANIFEST_BYTES) else null
                    if (data != null) manifest = data.toString(Charsets.UTF_8) else input.discardLimited(MAX_BACKUP_ENTRY_BYTES)
                    totalBytes += entryBytesRead
                    BackupSizePolicy.requirePayloadSize(totalBytes)
                }
                input.closeEntry(); entry = input.nextEntry
            }
        }
        return ArchiveInfo(manifest ?: error("Το αντίγραφο δεν περιέχει backup.json."), names)
    }

    private fun writeRestoreJournal(
        phase: String,
        root: File,
        previousRoot: File,
        stagingRoot: File,
        documentIds: Set<String>,
        databaseFingerprint: String,
        filesystemFingerprint: String
    ) {
        val journal = JSONObject().apply {
            put("phase", phase)
            put("root", root.canonicalPath)
            put("previousRoot", previousRoot.canonicalPath)
            put("stagingRoot", stagingRoot.canonicalPath)
            put("documentIds", JSONArray().apply { documentIds.forEach(::put) })
            put("databaseFingerprint", databaseFingerprint)
            put("filesystemFingerprint", filesystemFingerprint)
        }
        val journalFile = context.filesDir.resolve(RESTORE_JOURNAL)
        val temporary = context.filesDir.resolve(".$RESTORE_JOURNAL.${System.nanoTime()}.part")
        try {
            temporary.outputStream().use { it.write(journal.toString().toByteArray(Charsets.UTF_8)) }
            require(temporary.renameTo(journalFile)) { "Δεν ήταν δυνατή η καταγραφή της επαναφοράς." }
        } finally {
            temporary.delete()
        }
    }

    private fun clearRestoreJournal() {
        context.filesDir.resolve(RESTORE_JOURNAL).delete()
    }

    private fun safeJournalFile(path: String, expectedParent: File): File? {
        if (path.isBlank()) return null
        val parent = runCatching { expectedParent.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return if (candidate == parent || candidate.parentFile == parent || candidate.toPath().startsWith(parent.toPath())) candidate else null
    }

    private fun validateEntryName(value: String): String {
        require(value.length in 1..MAX_ENTRY_NAME && !value.contains('\\') && !value.startsWith('/')) { "Μη έγκυρο όνομα αρχείου στο αντίγραφο." }
        require(value.split('/').none { it == ".." || it.isBlank() }) { "Μη έγκυρο όνομα αρχείου στο αντίγραφο." }
        return value
    }

    private fun requireSafeId(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Μη έγκυρο αναγνωριστικό στο αντίγραφο." }
        return value
    }

    private fun checkedLength(array: JSONArray?): Int {
        val length = array?.length() ?: 0
        require(length <= BackupSizePolicy.MAX_ARCHIVE_ENTRIES) { "Το αντίγραφο περιέχει υπερβολικά πολλά στοιχεία." }
        return length
    }

    private fun documentJson(item: DocumentEntity) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("originalFileName", item.originalFileName); put("mimeType", item.mimeType); put("pageCount", item.pageCount); put("category", item.category); put("tags", item.tags); put("provider", item.provider); putNullable("issuedDate", item.issuedDate); putNullable("expiryDate", item.expiryDate); putNullable("protocolNumber", item.protocolNumber); put("ocrText", item.ocrText); put("extractedMetadataJson", item.extractedMetadataJson); put("processingState", item.processingState); putNullable("processingError", item.processingError); put("metadataManuallyEdited", item.metadataManuallyEdited); putNullable("expiryDateSuggestion", item.expiryDateSuggestion); put("expiryDateSuggestionConfidence", item.expiryDateSuggestionConfidence); put("titleConfidence", item.titleConfidence); put("categoryConfidence", item.categoryConfidence); put("providerConfidence", item.providerConfidence); put("issuedDateConfidence", item.issuedDateConfidence); put("expiryDateConfidence", item.expiryDateConfidence); put("protocolNumberConfidence", item.protocolNumberConfidence); put("titleManuallyEdited", item.titleManuallyEdited); put("categoryManuallyEdited", item.categoryManuallyEdited); put("providerManuallyEdited", item.providerManuallyEdited); put("issuedDateManuallyEdited", item.issuedDateManuallyEdited); put("expiryDateManuallyEdited", item.expiryDateManuallyEdited); put("protocolNumberManuallyEdited", item.protocolNumberManuallyEdited); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }

    private fun pageJson(item: DocumentPageEntity) = JSONObject().apply { put("documentId", item.documentId); put("pageIndex", item.pageIndex); put("ocrText", item.ocrText); put("sourceFileName", item.sourceFileName); put("mimeType", item.mimeType) }
    private fun caseJson(item: CaseEntity) = JSONObject().apply { put("id", item.id); put("title", item.title); put("description", item.description); put("status", item.status); putNullable("startDate", item.startDate); putNullable("deadline", item.deadline); put("nextStep", item.nextStep); put("notes", item.notes); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt) }
    private fun eventJson(item: TimelineEventEntity) = JSONObject().apply { put("id", item.id); put("caseId", item.caseId); put("title", item.title); put("note", item.note); put("eventType", item.eventType); put("eventDate", item.eventDate); put("createdAt", item.createdAt) }
    private fun checklistJson(item: ChecklistItemEntity) = JSONObject().apply { put("id", item.id); put("caseId", item.caseId); put("title", item.title); put("isComplete", item.isComplete); putNullable("linkedDocumentId", item.linkedDocumentId); put("createdAt", item.createdAt) }
    private fun reminderJson(item: ReminderEntity) = JSONObject().apply { put("id", item.id); put("title", item.title); put("dueAt", item.dueAt); putNullable("documentId", item.documentId); putNullable("caseId", item.caseId); put("leadDays", item.leadDays); put("isDone", item.isDone); put("deadlineAt", item.deadlineAt) }

    private fun JSONObject.putNullable(key: String, value: String?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private data class ArchiveInfo(val manifest: String, val names: Set<String>)
    private data class PageDescriptor(val documentId: String, val pageIndex: Int, val entryName: String, val ocrText: String, val sourceFileName: String, val mimeType: String)

    private var entryBytesRead = 0L

    private fun InputStream.readLimited(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        entryBytesRead = copyLimitedTo(output, limit)
        return output.toByteArray()
    }

    private fun InputStream.discardLimited(limit: Long) {
        entryBytesRead = copyLimitedTo(NullOutputStream, limit)
    }

    private fun InputStream.copyLimitedTo(output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Ένα αρχείο του αντιγράφου είναι υπερβολικά μεγάλο." }
            output.write(buffer, 0, read)
        }
        return total
    }

    private object NullOutputStream : OutputStream() { override fun write(b: Int) = Unit; override fun write(b: ByteArray, off: Int, len: Int) = Unit }

    private companion object {
        const val MAX_BACKUP_ENTRY_BYTES = BackupSizePolicy.MAX_ENTRY_BYTES
        const val MAX_BACKUP_PAYLOAD_BYTES = BackupSizePolicy.MAX_PAYLOAD_BYTES
        const val MAX_ENTRY_NAME = 300
        const val MAX_PAGE_INDEX = 100_000
        const val MAX_OCR_TEXT = LibraryLimits.MAX_DOCUMENT_OCR_CHARS
        const val MAX_METADATA_JSON = LibraryLimits.MAX_METADATA_JSON_CHARS
        const val MIN_NEW_BACKUP_PASSWORD_LENGTH = 12
        const val MIN_RESTORE_PASSWORD_LENGTH = 8
        const val RESTORE_JOURNAL = "restore_journal.json"
    }

    private fun Long.saturatingAdd(other: Long): Long = runCatching { Math.addExact(this, other) }
        .getOrElse { if (other >= 0L) Long.MAX_VALUE else Long.MIN_VALUE }
}
