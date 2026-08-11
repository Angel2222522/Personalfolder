package com.angel.personalfolder.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

class ExportService(private val context: Context) {
    private val database = AppDatabase.get(context)
    private val renderService = DocumentRenderService(context)

    suspend fun exportDocuments(destination: Uri, documentIds: List<String>) = withContext(Dispatchers.IO) {
        val documents = selectedDocuments(documentIds)
        val selectedIds = documents.map(DocumentEntity::id).toSet()
        val pages = database.documentPageDao().getAll()
            .filter { it.documentId in selectedIds }
        require(pages.isNotEmpty()) { "Δεν βρέθηκαν σελίδες για εξαγωγή." }
        val temporary = context.cacheDir.resolve("export/export_${UUID.randomUUID()}.zip").apply { parentFile?.mkdirs() }
        try {
            ZipOutputStream(temporary.outputStream()).use { output ->
                val manifest = JSONObject().apply {
                    put("formatVersion", 2)
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
                documents.forEach { document ->
                    val documentPages = pages.filter { it.documentId == document.id }.sortedBy { it.pageIndex }
                    require(documentPages.isNotEmpty()) { "Λείπει σελίδα από το έγγραφο «${document.title}»." }
                    documentPages.forEach { page ->
                        val source = File(page.encryptedPath)
                        require(FileCrypto.isPrivateDocumentFile(context, source)) { "Η σελίδα βρίσκεται εκτός του ιδιωτικού χώρου." }
                        require(source.isFile) { "Λείπει σελίδα από το έγγραφο «${document.title}»." }
                        val plain = context.cacheDir.resolve("export/plain_${UUID.randomUUID()}.tmp").apply { parentFile?.mkdirs() }
                        try {
                            FileCrypto.decryptToTemp(source, plain)
                            val extension = extensionOf(page.sourceFileName.ifBlank { document.originalFileName })
                            output.putNextEntry(ZipEntry("documents/${document.id}/page_${page.pageIndex + 1}.$extension"))
                            FileInputStream(plain).use { it.copyTo(output) }
                            output.closeEntry()
                        } finally {
                            plain.delete()
                        }
                    }
                }
            }
            require(temporary.length() in 1..MAX_EXPORT_BYTES) { "Το αρχείο εξαγωγής είναι υπερβολικά μεγάλο." }
            copyToDestination(temporary, destination, "Δεν ήταν δυνατή η δημιουργία του αρχείου εξαγωγής.")
        } finally {
            temporary.delete()
        }
    }

    suspend fun exportPdf(destination: Uri, documentIds: List<String>) = withContext(Dispatchers.IO) {
        val documents = selectedDocuments(documentIds)
        val pdf = context.cacheDir.resolve("export/export_${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
        try {
            buildPdfFile(documents, pdf)
            copyToDestination(pdf, destination, "Δεν ήταν δυνατή η δημιουργία του PDF.")
        } finally {
            pdf.delete()
        }
    }

    suspend fun createSharePdf(documentId: String): File = withContext(Dispatchers.IO) {
        val document = database.documentDao().getById(documentId) ?: error("Το έγγραφο δεν βρέθηκε.")
        val output = context.cacheDir.resolve("share/${document.id}_${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
        try {
            buildPdfFile(listOf(document), output)
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private suspend fun buildPdfFile(documents: List<DocumentEntity>, output: File) {
        val pages = buildList {
            documents.forEach { document ->
                renderService.logicalPages(document).forEach { page -> add(document to page) }
            }
        }
        require(pages.isNotEmpty()) { "Δεν βρέθηκαν σελίδες για εξαγωγή." }
        require(pages.size <= MAX_PDF_PAGES) { "Η εξαγωγή PDF υποστηρίζει έως $MAX_PDF_PAGES σελίδες ανά αρχείο." }
        FileOutputStream(output).use { stream ->
            StreamingPdfWriter(stream, pages.size).use { writer ->
                pages.forEach { (document, page) ->
                    val bitmap = renderService.renderPage(document, page)
                    try {
                        writer.writePage(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                writer.finish()
            }
        }
    }

    private fun copyToDestination(source: File, destination: Uri, errorMessage: String) {
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        } ?: error(errorMessage)
    }

    private suspend fun selectedDocuments(documentIds: List<String>): List<DocumentEntity> {
        val selectedIds = documentIds.toSet()
        require(selectedIds.isNotEmpty()) { "Επίλεξε τουλάχιστον ένα έγγραφο." }
        return database.documentDao().getAll().filter { it.id in selectedIds }.also {
            require(it.size == selectedIds.size) { "Δεν βρέθηκαν όλα τα επιλεγμένα έγγραφα." }
        }
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "bin")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")
        .ifBlank { "bin" }

    private class StreamingPdfWriter(output: OutputStream, pageCount: Int) : AutoCloseable {
        private val out = CountingOutputStream(output, MAX_EXPORT_BYTES)
        private val objectCount = 2 + pageCount * 3
        private val offsets = LongArray(objectCount + 1)
        private var finished = false

        init {
            out.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
            writeObject(1, "<< /Type /Catalog /Pages 2 0 R >>")
            val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 3} 0 R" }
            writeObject(2, "<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
        }

        fun writePage(bitmap: Bitmap) {
            val pageIndex = (offsets.count { it != 0L } - 2) / 3
            val pageObject = 3 + pageIndex * 3
            val imageObject = pageObject + 1
            val contentObject = pageObject + 2
            val jpeg = ByteArrayOutputStream()
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, jpeg)) { "Δεν ήταν δυνατή η συμπίεση σελίδας PDF." }
            require(jpeg.size <= MAX_PAGE_BYTES) { "Μία σελίδα είναι υπερβολικά μεγάλη για εξαγωγή PDF." }
            val scale = min(PDF_WIDTH / bitmap.width.toFloat(), PDF_HEIGHT / bitmap.height.toFloat())
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val x = (PDF_WIDTH - width) / 2f
            val y = (PDF_HEIGHT - height) / 2f
            writeObject(
                pageObject,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PDF_WIDTH $PDF_HEIGHT] " +
                    "/Resources << /XObject << /Im0 $imageObject 0 R >> >> /Contents $contentObject 0 R >>"
            )
            offsets[imageObject] = out.count
            writeAscii("$imageObject 0 obj\n<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
            out.write(jpeg.toByteArray())
            writeAscii("\nendstream\nendobj\n")
            val content = "q\n${format(width)} 0 0 ${format(height)} ${format(x)} ${format(y)} cm\n/Im0 Do\nQ\n"
            writeStreamObject(contentObject, content.toByteArray(Charsets.US_ASCII), "")
        }

        fun finish() {
            if (finished) return
            finished = true
            val xref = out.count
            writeAscii("xref\n0 ${objectCount + 1}\n0000000000 65535 f \n")
            for (object in 1..objectCount) {
                writeAscii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[object]))
            }
            writeAscii("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }

        override fun close() {
            if (!finished) finish()
        }

        private fun writeObject(number: Int, body: String) {
            offsets[number] = out.count
            writeAscii("$number 0 obj\n$body\nendobj\n")
        }

        private fun writeStreamObject(number: Int, bytes: ByteArray, dictionary: String) {
            offsets[number] = out.count
            writeAscii("$number 0 obj\n<< /Length ${bytes.size} $dictionary>>\nstream\n")
            out.write(bytes)
            writeAscii("endstream\nendobj\n")
        }

        private fun writeAscii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        private fun format(value: Float) = String.format(Locale.ROOT, "%.3f", value)
    }

    private class CountingOutputStream(private val delegate: OutputStream, private val maxBytes: Long) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            ensure(1)
            delegate.write(b)
            count++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            ensure(length.toLong())
            delegate.write(bytes, offset, length)
            count += length
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()

        private fun ensure(incoming: Long) {
            require(count <= maxBytes - incoming) { "Το αρχείο εξαγωγής είναι υπερβολικά μεγάλο." }
        }
    }

    private companion object {
        const val PDF_WIDTH = 595f
        const val PDF_HEIGHT = 842f
        const val MAX_PDF_PAGES = 1_000
        const val MAX_PAGE_BYTES = 16 * 1024 * 1024
        const val MAX_EXPORT_BYTES = 512L * 1024 * 1024
    }
}
