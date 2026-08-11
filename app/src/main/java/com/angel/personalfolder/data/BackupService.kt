package com.angel.personalfolder.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.angel.personalfolder.security.BackupCrypto
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.UUID

class BackupService(private val context: Context) {
    private val database = AppDatabase.get(context)

    suspend fun create(destination: Uri, password: String) = withContext(Dispatchers.IO) {
        require(password.length >= 8) { "Ο κωδικός πρέπει να έχει τουλάχιστον 8 χαρακτήρες." }
        val zip = File(context.cacheDir, "personal_folder_${System.currentTimeMillis()}.zip")
        val encrypted = try {
            val payload = snapshot()
            ZipOutputStream(FileOutputStream(zip)).use { output ->
                output.putNextEntry(ZipEntry("backup.json"))
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
                output.closeEntry()
                val paths = database.documentPageDao().getAll().distinctBy { it.encryptedPath }
                paths.forEach { page ->
                    val source = File(page.encryptedPath)
                    if (source.isFile) {
                        val plain = File(context.cacheDir, "backup_${UUID.randomUUID()}.tmp")
                        FileCrypto.decryptToTemp(source, plain)
                        output.putNextEntry(ZipEntry("files/${page.documentId}/${page.pageIndex}.pf"))
                        try {
                            FileInputStream(plain).use { it.copyTo(output) }
                        } finally {
                            output.closeEntry()
                            plain.delete()
                        }
                    }
                }
            }
            BackupCrypto.encryptFile(zip, context, destination, password.toCharArray())
            true
        } finally {
            zip.delete()
        }
        encrypted
    }

    suspend fun restore(source: Uri, password: String) = withContext(Dispatchers.IO) {
        require(password.length >= 8) { "Ο κωδικός πρέπει να έχει τουλάχιστον 8 χαρακτήρες." }
        val zip = File(context.cacheDir, "personal_folder_restore_${System.currentTimeMillis()}.zip")
        try {
            BackupCrypto.decryptToFile(context, source, zip, password.toCharArray())
            restoreZip(zip)
        } finally {
            zip.delete()
        }
    }

    private suspend fun snapshot(): JSONObject {
        val documents = database.documentDao().getAll()
        val pages = database.documentPageDao().getAll()
        val cases = database.caseDao().getAll()
        val relations = database.caseDocumentDao().getAll()
        val events = database.timelineDao().getAll()
        val checklist = database.checklistDao().getAll()
        val reminders = database.reminderDao().getAll()
        return JSONObject().apply {
            put("formatVersion", 2)
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

    private suspend fun restoreZip(zip: File) {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(FileInputStream(zip)).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = input.readBytes()
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        val manifest = entries["backup.json"]?.toString(Charsets.UTF_8)?.let(::JSONObject)
            ?: error("Το αντίγραφο δεν περιέχει έγκυρα δεδομένα.")
        val formatVersion = manifest.optInt("formatVersion", -1)
        require(formatVersion in 1..2) { "Η έκδοση του αντιγράφου δεν υποστηρίζεται." }
        val portableFiles = formatVersion >= 2

        val root = File(context.filesDir, "documents")
        val stagingRoot = File(context.cacheDir, "restore_documents_${UUID.randomUUID()}").apply { mkdirs() }
        val previousRoot = File(context.cacheDir, "previous_documents_${UUID.randomUUID()}")
        val documents = mutableListOf<DocumentEntity>()
        val pages = mutableListOf<DocumentPageEntity>()
        val fileMap = mutableMapOf<String, String>()
        val pageArray = manifest.optJSONArray("pages") ?: JSONArray()
        for (index in 0 until pageArray.length()) {
            val item = pageArray.getJSONObject(index)
            val documentId = requireSafeId(item.getString("documentId"))
            val pageIndex = item.getInt("pageIndex").also { require(it >= 0) }
            val key = "files/$documentId/$pageIndex.pf"
            val bytes = entries[key] ?: continue
            val stagedPath = File(stagingRoot, "$documentId/page_$pageIndex.pf")
            val finalPath = File(root, "$documentId/page_$pageIndex.pf")
            stagedPath.parentFile?.mkdirs()
            if (portableFiles) ByteArrayInputStream(bytes).use { FileCrypto.encrypt(it, stagedPath) } else stagedPath.writeBytes(bytes)
            fileMap["$documentId:$pageIndex"] = finalPath.absolutePath
            pages += DocumentPageEntity(documentId, pageIndex, finalPath.absolutePath, item.optString("ocrText"))
        }
        val documentArray = manifest.optJSONArray("documents") ?: JSONArray()
        for (index in 0 until documentArray.length()) {
            val item = documentArray.getJSONObject(index)
            val id = requireSafeId(item.getString("id"))
            val firstPath = fileMap.entries.firstOrNull { it.key.startsWith("$id:") }?.value ?: continue
            documents += DocumentEntity(
                id = id, title = item.getString("title"), originalFileName = item.getString("originalFileName"),
                mimeType = item.getString("mimeType"), encryptedPath = firstPath, pageCount = item.getInt("pageCount"),
                category = item.optString("category", "Άλλα"), tags = item.optString("tags"), provider = item.optString("provider"),
                issuedDate = item.optNullableString("issuedDate"), expiryDate = item.optNullableString("expiryDate"),
                protocolNumber = item.optNullableString("protocolNumber"), ocrText = item.optString("ocrText"),
                extractedMetadataJson = item.optString("extractedMetadataJson"), processingState = item.optString("processingState", ProcessingState.PROCESSED),
                processingError = item.optNullableString("processingError"), createdAt = item.optLong("createdAt"), updatedAt = item.optLong("updatedAt")
            )
        }
        val cases = parseCases(manifest.optJSONArray("cases"))
        val relations = parseRelations(manifest.optJSONArray("relations"))
        val events = parseEvents(manifest.optJSONArray("events"))
        val checklist = parseChecklist(manifest.optJSONArray("checklist"))
        val reminders = parseReminders(manifest.optJSONArray("reminders"))
        var previousMoved = false
        try {
            previousMoved = root.exists() && root.renameTo(previousRoot)
            require(!root.exists()) { "Δεν ήταν δυνατή η προετοιμασία της επαναφοράς." }
            require(stagingRoot.renameTo(root)) { "Δεν ήταν δυνατή η εγκατάσταση των αρχείων επαναφοράς." }
            try {
                database.withTransaction {
                    database.caseDocumentDao().deleteAll(); database.timelineDao().deleteAll(); database.checklistDao().deleteAll(); database.reminderDao().deleteAll(); database.documentPageDao().deleteAll(); database.documentDao().deleteAll(); database.caseDao().deleteAll()
                    database.documentDao().insertAll(documents); database.documentPageDao().insertAll(pages); database.caseDao().insertAll(cases)
                    relations.forEach { database.caseDocumentDao().insert(it) }; events.forEach { database.timelineDao().insert(it) }; checklist.forEach { database.checklistDao().insert(it) }; reminders.forEach { database.reminderDao().insert(it) }
                }
            } catch (error: Throwable) {
                FileCrypto.deleteRecursively(root)
                if (previousMoved) previousRoot.renameTo(root)
                throw error
            }
            if (previousMoved) FileCrypto.deleteRecursively(previousRoot)
            ReminderScheduler.rescheduleAll(context)
        } finally {
            if (stagingRoot.exists()) FileCrypto.deleteRecursively(stagingRoot)
            if (previousMoved && previousRoot.exists() && !root.exists()) previousRoot.renameTo(root)
        }
    }

    private fun documentJson(item: DocumentEntity) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("originalFileName", item.originalFileName); put("mimeType", item.mimeType); put("pageCount", item.pageCount); put("category", item.category); put("tags", item.tags); put("provider", item.provider); putNullable("issuedDate", item.issuedDate); putNullable("expiryDate", item.expiryDate); putNullable("protocolNumber", item.protocolNumber); put("ocrText", item.ocrText); put("extractedMetadataJson", item.extractedMetadataJson); put("processingState", item.processingState); putNullable("processingError", item.processingError); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
    }
    private fun pageJson(item: DocumentPageEntity) = JSONObject().apply { put("documentId", item.documentId); put("pageIndex", item.pageIndex); put("ocrText", item.ocrText) }
    private fun caseJson(item: CaseEntity) = JSONObject().apply { put("id", item.id); put("title", item.title); put("description", item.description); put("status", item.status); putNullable("startDate", item.startDate); putNullable("deadline", item.deadline); put("nextStep", item.nextStep); put("notes", item.notes); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt) }
    private fun eventJson(item: TimelineEventEntity) = JSONObject().apply { put("id", item.id); put("caseId", item.caseId); put("title", item.title); put("note", item.note); put("eventType", item.eventType); put("eventDate", item.eventDate); put("createdAt", item.createdAt) }
    private fun checklistJson(item: ChecklistItemEntity) = JSONObject().apply { put("id", item.id); put("caseId", item.caseId); put("title", item.title); put("isComplete", item.isComplete); putNullable("linkedDocumentId", item.linkedDocumentId); put("createdAt", item.createdAt) }
    private fun reminderJson(item: ReminderEntity) = JSONObject().apply { put("id", item.id); put("title", item.title); put("dueAt", item.dueAt); putNullable("documentId", item.documentId); putNullable("caseId", item.caseId); put("leadDays", item.leadDays); put("isDone", item.isDone) }
    private fun JSONObject.putNullable(key: String, value: String?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun parseCases(array: JSONArray?): List<CaseEntity> = buildList { for (i in 0 until (array?.length() ?: 0)) { val x = array!!.getJSONObject(i); add(CaseEntity(x.getString("id"), x.getString("title"), x.optString("description"), x.optString("status", CaseStatus.NEW), x.optNullableString("startDate"), x.optNullableString("deadline"), x.optString("nextStep"), x.optString("notes"), x.optLong("createdAt"), x.optLong("updatedAt"))) } }
    private fun parseRelations(array: JSONArray?): List<CaseDocumentCrossRef> = buildList { for (i in 0 until (array?.length() ?: 0)) { val x = array!!.getJSONObject(i); add(CaseDocumentCrossRef(x.getString("caseId"), x.getString("documentId"))) } }
    private fun parseEvents(array: JSONArray?): List<TimelineEventEntity> = buildList { for (i in 0 until (array?.length() ?: 0)) { val x = array!!.getJSONObject(i); add(TimelineEventEntity(x.getString("id"), x.getString("caseId"), x.getString("title"), x.optString("note"), x.optString("eventType", "manual"), x.getString("eventDate"), x.optLong("createdAt"))) } }
    private fun parseChecklist(array: JSONArray?): List<ChecklistItemEntity> = buildList { for (i in 0 until (array?.length() ?: 0)) { val x = array!!.getJSONObject(i); add(ChecklistItemEntity(x.getString("id"), x.getString("caseId"), x.getString("title"), x.optBoolean("isComplete"), x.optNullableString("linkedDocumentId"), x.optLong("createdAt"))) } }
    private fun parseReminders(array: JSONArray?): List<ReminderEntity> = buildList { for (i in 0 until (array?.length() ?: 0)) { val x = array!!.getJSONObject(i); add(ReminderEntity(x.getString("id"), x.getString("title"), x.getLong("dueAt"), x.optNullableString("documentId"), x.optNullableString("caseId"), x.optInt("leadDays"), x.optBoolean("isDone"))) } }

    private fun requireSafeId(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Μη έγκυρο αναγνωριστικό στο αντίγραφο." }
        return value
    }
}
