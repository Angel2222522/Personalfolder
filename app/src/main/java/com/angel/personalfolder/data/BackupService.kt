package com.angel.personalfolder.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.angel.personalfolder.security.BackupCrypto
import com.angel.personalfolder.security.DocumentStorage
import com.angel.personalfolder.security.FileCrypto
import com.angel.personalfolder.workers.OcrWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        LibraryOperationCoordinator.withExclusive {
            val journalFile = context.filesDir.resolve(RESTORE_JOURNAL)
            if (!journalFile.isFile) return@withExclusive
            val journal = runCatching { JSONObject(journalFile.readText(Charsets.UTF_8)) }.getOrNull()
                ?: return@withExclusive
            val root = safeJournalFile(journal.optString("root"), context.filesDir)
            val previous = safeJournalFile(journal.optString("previousRoot"), context.cacheDir)
            val staging = safeJournalFile(journal.optString("stagingRoot"), context.cacheDir)
            if (root == null || previous == null || staging == null) return@withExclusive
            val expectedIds = buildSet {
                val ids = journal.optJSONArray("documentIds") ?: return@buildSet
                for (index in 0 until ids.length()) add(ids.optString(index))
            }
            val currentIds = database.documentDao().getAll().map { it.id }.toSet()
            val filesMatchDatabase = currentIds == expectedIds && expectedIds.all { id ->
                root.resolve(id).isDirectory && root.resolve(id).listFiles().orEmpty().any { it.isFile }
            }
            val phase = journal.optString("phase")
            if (phase == "database_committed" || (phase != "prepared" && filesMatchDatabase)) {
                if (previous.exists()) FileCrypto.deleteRecursively(previous)
                if (staging.exists()) FileCrypto.deleteRecursively(staging)
            } else {
                if (root.exists()) FileCrypto.deleteRecursively(root)
                if (!root.exists() && previous.exists()) previous.renameTo(root)
                if (staging.exists()) FileCrypto.deleteRecursively(staging)
            }
            journalFile.delete()
        }
    }

    suspend fun create(destination: Uri, password: String) = withContext(Dispatchers.IO) {
        LibraryOperationCoordinator.withExclusive {
            require(password.length >= MIN_NEW_BACKUP_PASSWORD_LENGTH) { "Ο νέος κωδικός backup πρέπει να έχει τουλάχιστον $MIN_NEW_BACKUP_PASSWORD_LENGTH χαρακτήρες." }
            val zip = context.cacheDir.resolve("backup/personal_folder_${System.currentTimeMillis()}.zip").apply {
                parentFile?.mkdirs()
            }
            try {
                val payload = snapshot()
                ZipOutputStream(FileOutputStream(zip)).use { output ->
                    val manifestBytes = payload.toString().toByteArray(Charsets.UTF_8)
                    require(manifestBytes.size.toLong() <= MAX_BACKUP_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
                    output.putNextEntry(ZipEntry("backup.json"))
                    output.write(manifestBytes)
                    output.closeEntry()
                    var archiveBytes = manifestBytes.size.toLong()
                    val pages = database.documentPageDao().getAll().distinctBy { it.documentId to it.pageIndex }
                    pages.forEach { page ->
                        val source = File(page.encryptedPath)
                        require(FileCrypto.isPrivateDocumentFile(context, source)) { "Η σελίδα βρίσκεται εκτός του ιδιωτικού χώρου." }
                        require(source.isFile) { "Λείπει σελίδα από το έγγραφο ${page.documentId}." }
                        val plain = context.cacheDir.resolve("backup/plain_${UUID.randomUUID()}.tmp").apply { parentFile?.mkdirs() }
                        try {
                            FileCrypto.decryptToTemp(source, plain)
                            output.putNextEntry(ZipEntry("files/${page.documentId}/${page.pageIndex}.pf"))
                            val copied = FileInputStream(plain).use {
                                it.copyLimitedTo(output, MAX_BACKUP_BYTES - archiveBytes)
                            }
                            archiveBytes += copied
                            output.closeEntry()
                        } finally {
                            plain.delete()
                        }
                    }
                }
                BackupCrypto.encryptFile(zip, context, destination, password.toCharArray())
                true
            } finally {
                zip.delete()
            }
        }
    }

    suspend fun restore(source: Uri, password: String) = withContext(Dispatchers.IO) {
        require(password.length >= MIN_RESTORE_PASSWORD_LENGTH) { "Ο κωδικός επαναφοράς πρέπει να έχει τουλάχιστον $MIN_RESTORE_PASSWORD_LENGTH χαρακτήρες." }
        restoreMutex.withLock {
            OcrWorker.withProcessingLock {
                WorkManager.getInstance(context).cancelAllWorkByTag("document-processing")
                LibraryOperationCoordinator.withExclusive {
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
            }
        }
    }

    private suspend fun snapshot(): JSONObject {
        return database.withTransaction {
            val documents = database.documentDao().getAll()
            val pages = database.documentPageDao().getAll()
            val cases = database.caseDao().getAll()
            val relations = database.caseDocumentDao().getAll()
            val events = database.timelineDao().getAll()
            val checklist = database.checklistDao().getAll()
            val reminders = database.reminderDao().getAll()
            JSONObject().apply {
                put("formatVersion", 3)
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
        require(formatVersion in 1..3) { "Η έκδοση του αντιγράφου δεν υποστηρίζεται." }
        val portableFiles = formatVersion >= 2
        val pageDescriptors = parsePageDescriptors(manifest.optJSONArray("pages"))
        val expectedFiles = pageDescriptors.map { it.entryName }.toSet()
        // Do not generate a replacement key after a Keystore loss when the
        // current device still has encrypted library files. A new device with
        // an empty library may create its first key for portable restore.
        FileCrypto.ensureKeyAvailableForNewDocument(context)
        val stagingRoot = context.cacheDir.resolve("restore_documents_${UUID.randomUUID()}").apply { mkdirs() }
        val root = DocumentStorage.root(context)
        val previousRoot = context.cacheDir.resolve("previous_documents_${UUID.randomUUID()}")
        val stagedFiles = mutableMapOf<String, File>()
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
                        if (portableFiles) FileCrypto.encrypt(input, staged, MAX_BACKUP_BYTES)
                        else staged.outputStream().use { input.copyLimitedTo(it, MAX_BACKUP_BYTES) }
                        stagedFiles[name] = staged
                    } else if (!entry.isDirectory) {
                        input.copyLimitedTo(NullOutputStream, MAX_BACKUP_BYTES)
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
            val reminders = parseReminders(manifest.optJSONArray("reminders"), caseIds, documentIds)

            writeRestoreJournal(
                phase = "prepared",
                root = root,
                previousRoot = previousRoot,
                stagingRoot = stagingRoot,
                documentIds = documentIds
            )

            var previousMoved = false
            try {
                previousMoved = root.exists() && root.renameTo(previousRoot)
                require(!root.exists()) { "Δεν ήταν δυνατή η προετοιμασία της επαναφοράς." }
                require(stagingRoot.renameTo(root)) { "Δεν ήταν δυνατή η εγκατάσταση των αρχείων επαναφοράς." }
                writeRestoreJournal("files_installed", root, previousRoot, stagingRoot, documentIds)
                try {
                    database.withTransaction {
                        database.caseDocumentDao().deleteAll()
                        database.timelineDao().deleteAll()
                        database.checklistDao().deleteAll()
                        database.reminderDao().deleteAll()
                        database.documentPageDao().deleteAll()
                        database.documentDao().deleteAll()
                        database.caseDao().deleteAll()
                        database.documentDao().insertAll(documents)
                        database.documentPageDao().insertAll(documents.flatMap { document ->
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
                        })
                        database.caseDao().insertAll(cases)
                        relations.forEach { database.caseDocumentDao().insert(it) }
                        events.forEach { database.timelineDao().insert(it) }
                        checklist.forEach { database.checklistDao().insert(it) }
                        reminders.forEach { database.reminderDao().insert(it) }
                    }
                    writeRestoreJournal("database_committed", root, previousRoot, stagingRoot, documentIds)
                } catch (error: Throwable) {
                    FileCrypto.deleteRecursively(root)
                    if (previousMoved) previousRoot.renameTo(root)
                    throw error
                }
                if (previousMoved) FileCrypto.deleteRecursively(previousRoot)
                ReminderScheduler.rescheduleAllUnlocked(context)
                clearRestoreJournal()
            } finally {
                if (stagingRoot.exists()) FileCrypto.deleteRecursively(stagingRoot)
                if (previousMoved && previousRoot.exists() && !root.exists()) previousRoot.renameTo(root)
                if (root.exists() && !previousRoot.exists()) clearRestoreJournal()
            }
        } finally {
            if (stagingRoot.exists()) FileCrypto.deleteRecursively(stagingRoot)
        }
    }

    private fun parseDocuments(
        array: JSONArray?,
        pages: List<PageDescriptor>,
        stagedFiles: Map<String, File>,
        root: File
    ): List<DocumentEntity> {
        val result = mutableListOf<DocumentEntity>()
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val item = array!!.getJSONObject(i)
            val id = requireSafeId(item.getString("id"))
            require(seen.add(id)) { "Το αντίγραφο περιέχει διπλό έγγραφο." }
            val documentPages = pages.filter { it.documentId == id }
            require(documentPages.isNotEmpty()) { "Το έγγραφο δεν έχει σελίδες." }
            require(documentPages.all { it.entryName in stagedFiles }) { "Λείπει αρχείο από το έγγραφο." }
            val pageCount = item.optInt("pageCount", documentPages.size)
            require(pageCount in documentPages.size..MAX_RESTORED_PAGES) {
                "Το αντίγραφο περιέχει μη έγκυρο αριθμό σελίδων."
            }
            result += DocumentEntity(
                id = id,
                title = item.getString("title").take(200),
                originalFileName = item.getString("originalFileName").take(200),
                mimeType = item.getString("mimeType").take(120),
                encryptedPath = root.resolve("$id/page_${documentPages.minOf { it.pageIndex }}.pf").absolutePath,
                pageCount = pageCount,
                category = item.optString("category", "Άλλα").take(120),
                tags = item.optString("tags").take(500),
                provider = item.optString("provider").take(200),
                issuedDate = item.optNullableString("issuedDate"),
                expiryDate = item.optNullableString("expiryDate"),
                protocolNumber = item.optNullableString("protocolNumber"),
                ocrText = item.optString("ocrText").take(MAX_OCR_TEXT),
                extractedMetadataJson = item.optString("extractedMetadataJson").take(MAX_METADATA_JSON),
                processingState = safeProcessingState(item.optString("processingState", ProcessingState.PROCESSED)),
                processingError = item.optNullableString("processingError") ?: item.optString("processingState").let {
                    if (it == ProcessingState.QUEUED || it == ProcessingState.PROCESSING) "Η επεξεργασία δεν συνεχίστηκε μετά την επαναφορά. Επίλεξε επανάληψη OCR." else null
                },
                metadataManuallyEdited = item.optBoolean("metadataManuallyEdited", false),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return result
    }

    private fun validateStagedPageCounts(stagedFiles: Map<String, File>, descriptors: List<PageDescriptor>) {
        var logicalPages = 0
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
            require(count <= MAX_RESTORED_PAGES && logicalPages + count <= MAX_RESTORED_PAGES) {
                "Το αντίγραφο περιέχει υπερβολικά πολλές λογικές σελίδες."
            }
            logicalPages += count
        }
    }

    private fun parsePageDescriptors(array: JSONArray?): List<PageDescriptor> = buildList {
        require(checkedLength(array) <= MAX_RESTORED_PAGES) { "Το αντίγραφο περιέχει υπερβολικά πολλές σελίδες." }
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
        require(checkedLength(array) <= MAX_BACKUP_ENTRIES)
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
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i); val caseId = requireSafeId(x.getString("caseId"))
            require(caseId in cases) { "Το αντίγραφο περιέχει γεγονός άγνωστης υπόθεσης." }
            val id = requireSafeId(x.getString("id")); require(seen.add(id)) { "Το αντίγραφο περιέχει διπλό γεγονός." }
            add(TimelineEventEntity(id, caseId, x.getString("title").take(300), x.optString("note").take(5000), x.optString("eventType", "manual").take(50), x.getString("eventDate"), x.optLong("createdAt", System.currentTimeMillis())))
        }
    }

    private fun parseChecklist(array: JSONArray?, cases: Set<String>, documents: Set<String>): List<ChecklistItemEntity> = buildList {
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i); val caseId = requireSafeId(x.getString("caseId")); val linked = x.optNullableString("linkedDocumentId")?.let(::requireSafeId)
            require(caseId in cases && (linked == null || linked in documents)) { "Το αντίγραφο περιέχει άκυρο δικαιολογητικό." }
            val id = requireSafeId(x.getString("id")); require(seen.add(id)) { "Το αντίγραφο περιέχει διπλό δικαιολογητικό." }
            add(ChecklistItemEntity(id, caseId, x.getString("title").take(500), x.optBoolean("isComplete"), linked, x.optLong("createdAt", System.currentTimeMillis())))
        }
    }

    private fun parseReminders(array: JSONArray?, cases: Set<String>, documents: Set<String>): List<ReminderEntity> = buildList {
        require(checkedLength(array) <= MAX_RESTORED_REMINDERS) { "Το αντίγραφο περιέχει υπερβολικά πολλές υπενθυμίσεις." }
        val seen = mutableSetOf<String>()
        for (i in 0 until checkedLength(array)) {
            val x = array!!.getJSONObject(i); val documentId = x.optNullableString("documentId")?.let(::requireSafeId); val caseId = x.optNullableString("caseId")?.let(::requireSafeId)
            require((documentId == null || documentId in documents) && (caseId == null || caseId in cases)) { "Το αντίγραφο περιέχει άκυρη υπενθύμιση." }
            val id = requireSafeId(x.getString("id")); require(seen.add(id)) { "Το αντίγραφο περιέχει διπλή υπενθύμιση." }
            val dueAt = x.getLong("dueAt")
            require(dueAt in 0L..MAX_DUE_AT) { "Η υπενθύμιση έχει μη έγκυρη ημερομηνία." }
            add(ReminderEntity(id, x.getString("title").take(500), dueAt, documentId, caseId, x.optInt("leadDays").coerceIn(0, 3650), x.optBoolean("isDone")))
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
                require(++count <= MAX_BACKUP_ENTRIES) { "Το αντίγραφο περιέχει υπερβολικά πολλά αρχεία." }
                val name = validateEntryName(entry.name)
                require(names.add(name)) { "Το αντίγραφο περιέχει διπλό αρχείο." }
                if (!entry.isDirectory) {
                    val bytesRead = if (name == "backup.json") {
                        val output = ByteArrayOutputStream()
                        val count = input.copyLimitedTo(output, MAX_MANIFEST_BYTES)
                        manifest = output.toByteArray().toString(Charsets.UTF_8)
                        count
                    } else {
                        input.copyLimitedTo(NullOutputStream, MAX_BACKUP_BYTES)
                    }
                    totalBytes += bytesRead
                    require(totalBytes <= MAX_BACKUP_BYTES) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
                }
                input.closeEntry(); entry = input.nextEntry
            }
        }
        return ArchiveInfo(manifest ?: error("Το αντίγραφο δεν περιέχει backup.json."))
    }

    private fun writeRestoreJournal(
        phase: String,
        root: File,
        previousRoot: File,
        stagingRoot: File,
        documentIds: Set<String>
    ) {
        val journal = JSONObject().apply {
            put("phase", phase)
            put("root", root.canonicalPath)
            put("previousRoot", previousRoot.canonicalPath)
            put("stagingRoot", stagingRoot.canonicalPath)
            put("documentIds", JSONArray().apply { documentIds.forEach(::put) })
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
        return if (candidate.parentFile == parent || candidate.toPath().startsWith(parent.toPath())) candidate else null
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
        require(length <= MAX_BACKUP_ENTRIES) { "Το αντίγραφο περιέχει υπερβολικά πολλά στοιχεία." }
        return length
    }

    private fun documentJson(item: DocumentEntity) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("originalFileName", item.originalFileName); put("mimeType", item.mimeType); put("pageCount", item.pageCount); put("category", item.category); put("tags", item.tags); put("provider", item.provider); putNullable("issuedDate", item.issuedDate); putNullable("expiryDate", item.expiryDate); putNullable("protocolNumber", item.protocolNumber); put("ocrText", item.ocrText); put("extractedMetadataJson", item.extractedMetadataJson); put("processingState", item.processingState); putNullable("processingError", item.processingError); put("metadataManuallyEdited", item.metadataManuallyEdited); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }

    private fun pageJson(item: DocumentPageEntity) = JSONObject().apply { put("documentId", item.documentId); put("pageIndex", item.pageIndex); put("ocrText", item.ocrText); put("sourceFileName", item.sourceFileName); put("mimeType", item.mimeType) }
    private fun caseJson(item: CaseEntity) = JSONObject().apply { put("id", item.id); put("title", item.title); put("description", item.description); put("status", item.status); putNullable("startDate", item.startDate); putNullable("deadline", item.deadline); put("nextStep", item.nextStep); put("notes", item.notes); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt) }
    private fun eventJson(item: TimelineEventEntity) = JSONObject().apply { put("id", item.id); put("caseId", item.caseId); put("title", item.title); put("note", item.note); put("eventType", item.eventType); put("eventDate", item.eventDate); put("createdAt", item.createdAt) }
    private fun checklistJson(item: ChecklistItemEntity) = JSONObject().apply { put("id", item.id); put("caseId", item.caseId); put("title", item.title); put("isComplete", item.isComplete); putNullable("linkedDocumentId", item.linkedDocumentId); put("createdAt", item.createdAt) }
    private fun reminderJson(item: ReminderEntity) = JSONObject().apply { put("id", item.id); put("title", item.title); put("dueAt", item.dueAt); putNullable("documentId", item.documentId); putNullable("caseId", item.caseId); put("leadDays", item.leadDays); put("isDone", item.isDone) }

    private fun JSONObject.putNullable(key: String, value: String?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private data class ArchiveInfo(val manifest: String)
    private data class PageDescriptor(val documentId: String, val pageIndex: Int, val entryName: String, val ocrText: String, val sourceFileName: String, val mimeType: String)

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
        val restoreMutex = Mutex()
        const val MAX_BACKUP_BYTES = 512L * 1024 * 1024
        const val MAX_BACKUP_ENTRIES = 10_000
        const val MAX_ENTRY_NAME = 300
        const val MAX_PAGE_INDEX = 100_000
        const val MAX_MANIFEST_BYTES = 16L * 1024 * 1024
        const val MAX_OCR_TEXT = 2_000_000
        const val MAX_METADATA_JSON = 200_000
        const val MIN_NEW_BACKUP_PASSWORD_LENGTH = 12
        const val MIN_RESTORE_PASSWORD_LENGTH = 8
        const val RESTORE_JOURNAL = "restore_journal.json"
        const val MAX_RESTORED_PAGES = 1_000
        const val MAX_RESTORED_REMINDERS = 5_000
        val MAX_DUE_AT = System.currentTimeMillis() + 20L * 365L * 24L * 60L * 60L * 1_000L
    }
}
