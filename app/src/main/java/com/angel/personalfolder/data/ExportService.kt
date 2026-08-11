package com.angel.personalfolder.data

import android.content.Context
import android.net.Uri
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportService(private val context: Context) {
    private val database = AppDatabase.get(context)

    suspend fun exportDocuments(destination: Uri, documentIds: List<String>) = withContext(Dispatchers.IO) {
        val selectedIds = documentIds.toSet()
        require(selectedIds.isNotEmpty()) { "Επίλεξε τουλάχιστον ένα έγγραφο." }
        val documents = database.documentDao().getAll().filter { it.id in selectedIds }
        require(documents.isNotEmpty()) { "Δεν βρέθηκαν τα επιλεγμένα έγγραφα." }
        val pages = database.documentPageDao().getAll().filter { it.documentId in selectedIds }
        context.contentResolver.openOutputStream(destination, "w")?.use { rawOutput ->
            ZipOutputStream(rawOutput).use { output ->
                val manifest = JSONObject().apply {
                    put("formatVersion", 1)
                    put("documents", JSONArray().apply {
                        documents.forEach { document ->
                            put(JSONObject().apply {
                                put("id", document.id)
                                put("title", document.title)
                                put("originalFileName", document.originalFileName)
                                put("mimeType", document.mimeType)
                                put("pageCount", document.pageCount)
                            })
                        }
                    })
                }
                output.putNextEntry(ZipEntry("manifest.json"))
                output.write(manifest.toString().toByteArray(Charsets.UTF_8))
                output.closeEntry()
                pages.groupBy { it.documentId }.forEach { (documentId, documentPages) ->
                    val document = documents.first { it.id == documentId }
                    documentPages.sortedBy { it.pageIndex }.forEach { page ->
                        val source = File(page.encryptedPath)
                        require(source.isFile) { "Λείπει σελίδα από το έγγραφο «${document.title}»." }
                        val plain = File(context.cacheDir, "export_${documentId}_${page.pageIndex}.tmp")
                        try {
                            FileCrypto.decryptToTemp(source, plain)
                            val extension = document.originalFileName.substringAfterLast('.', "bin").lowercase()
                            output.putNextEntry(ZipEntry("documents/$documentId/page_${page.pageIndex + 1}.$extension"))
                            FileInputStream(plain).use { it.copyTo(output) }
                            output.closeEntry()
                        } finally {
                            plain.delete()
                        }
                    }
                }
            }
        } ?: error("Δεν ήταν δυνατή η δημιουργία του αρχείου εξαγωγής.")
    }
}
