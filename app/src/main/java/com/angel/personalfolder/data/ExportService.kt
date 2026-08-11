package com.angel.personalfolder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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

    suspend fun exportPdf(destination: Uri, documentIds: List<String>) = withContext(Dispatchers.IO) {
        val selectedIds = documentIds.toSet()
        require(selectedIds.isNotEmpty()) { "Επίλεξε τουλάχιστον ένα έγγραφο." }
        val documents = database.documentDao().getAll().filter { it.id in selectedIds }
        require(documents.isNotEmpty()) { "Δεν βρέθηκαν τα επιλεγμένα έγγραφα." }
        val pages = database.documentPageDao().getAll().filter { it.documentId in selectedIds }
        val pdf = PdfDocument()
        var outputPageNumber = 1
        try {
            pages.groupBy { it.documentId }.forEach { (documentId, documentPages) ->
                val document = documents.first { it.id == documentId }
                documentPages.sortedBy { it.pageIndex }.forEach { page ->
                    val source = File(page.encryptedPath)
                    require(source.isFile) { "Λείπει σελίδα από το έγγραφο «${document.title}»." }
                    val plain = File(context.cacheDir, "pdf_export_${documentId}_${page.pageIndex}.tmp")
                    try {
                        FileCrypto.decryptToTemp(source, plain)
                        if (document.mimeType == "application/pdf" || plain.extension.equals("pdf", true)) {
                            ParcelFileDescriptor.open(plain, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                                PdfRenderer(descriptor).use { renderer ->
                                    for (sourcePageIndex in 0 until renderer.pageCount) {
                                        renderer.openPage(sourcePageIndex).use { sourcePage ->
                                            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
                                            try {
                                                sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                addBitmapPage(pdf, bitmap, outputPageNumber++)
                                            } finally {
                                                bitmap.recycle()
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val bitmap = BitmapFactory.decodeFile(plain.absolutePath)
                                ?: error("Δεν ήταν δυνατή η ανάγνωση εικόνας από το έγγραφο «${document.title}».")
                            try {
                                addBitmapPage(pdf, bitmap, outputPageNumber++)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    } finally {
                        plain.delete()
                    }
                }
            }
            require(outputPageNumber > 1) { "Δεν βρέθηκαν σελίδες για εξαγωγή." }
            context.contentResolver.openOutputStream(destination, "w")?.use { pdf.writeTo(it) }
                ?: error("Δεν ήταν δυνατή η δημιουργία του PDF.")
        } finally {
            pdf.close()
        }
    }

    private fun addBitmapPage(pdf: PdfDocument, bitmap: Bitmap, pageNumber: Int) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        page.canvas.drawColor(Color.WHITE)
        val scale = minOf(PAGE_WIDTH.toFloat() / bitmap.width, PAGE_HEIGHT.toFloat() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val target = RectF((PAGE_WIDTH - width) / 2f, (PAGE_HEIGHT - height) / 2f, (PAGE_WIDTH + width) / 2f, (PAGE_HEIGHT + height) / 2f)
        page.canvas.drawBitmap(bitmap, null, target, null)
        pdf.finishPage(page)
    }

    private companion object {
        const val PAGE_WIDTH = 1240
        const val PAGE_HEIGHT = 1754
    }
}
