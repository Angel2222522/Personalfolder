package com.angel.personalfolder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

data class LogicalDocumentPage(
    val number: Int,
    val source: DocumentPageEntity,
    val sourcePageIndex: Int
)

/** Reads one logical page at a time; it never keeps an entire document in memory. */
class DocumentRenderService(private val context: Context) {
    private val database = AppDatabase.get(context)

    suspend fun logicalPages(document: DocumentEntity): List<LogicalDocumentPage> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LogicalDocumentPage>()
        sources(document).forEach { source ->
            val count = withDecryptedSource(source) { file -> countPages(file, source.mimeType, source.sourceFileName, document) }
            require(result.size + count <= MAX_LOGICAL_PAGES) { "Το έγγραφο περιέχει υπερβολικά πολλές σελίδες." }
            repeat(count) { sourcePage ->
                result += LogicalDocumentPage(result.size, source, sourcePage)
            }
        }
        result
    }

    suspend fun pageCount(document: DocumentEntity): Int = logicalPages(document).size

    suspend fun renderPage(document: DocumentEntity, logicalPage: LogicalDocumentPage, maxDimension: Int = 1800): Bitmap =
        withContext(Dispatchers.IO) {
            withDecryptedSource(logicalPage.source) { file ->
                renderSourcePage(
                    file = file,
                    source = logicalPage.source,
                    sourcePageIndex = logicalPage.sourcePageIndex,
                    document = document,
                    maxDimension = maxDimension
                )
            }
        }

    private suspend fun <T> withDecryptedSource(source: DocumentPageEntity, block: (File) -> T): T {
        val encrypted = File(source.encryptedPath)
        require(isSafeDocumentFile(encrypted)) { "Το αρχείο του εγγράφου δεν βρίσκεται στον ιδιωτικό χώρο της εφαρμογής." }
        require(encrypted.isFile) { "Λείπει η σελίδα του εγγράφου." }
        val directory = context.cacheDir.resolve("viewer").apply { mkdirs() }
        val plain = directory.resolve("viewer_${System.nanoTime()}.tmp")
        return try {
            FileCrypto.decryptToTemp(encrypted, plain)
            block(plain)
        } finally {
            plain.delete()
        }
    }

    private suspend fun sources(document: DocumentEntity): List<DocumentPageEntity> {
        val rows = database.documentPageDao().getForDocument(document.id)
        return if (rows.isNotEmpty()) rows.sortedBy { it.pageIndex } else listOf(
            DocumentPageEntity(
                documentId = document.id,
                pageIndex = 0,
                encryptedPath = document.encryptedPath,
                sourceFileName = document.originalFileName,
                mimeType = document.mimeType
            )
        )
    }

    private fun countPages(file: File, mimeType: String, sourceName: String, document: DocumentEntity): Int {
        if (isPdf(mimeType, sourceName, document)) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val count = renderer.pageCount.coerceAtLeast(1)
                    require(count <= MAX_LOGICAL_PAGES) { "Το έγγραφο περιέχει υπερβολικά πολλές σελίδες." }
                    return count
                }
            }
        }
        return 1
    }

    private fun renderSourcePage(
        file: File,
        source: DocumentPageEntity,
        sourcePageIndex: Int,
        document: DocumentEntity,
        maxDimension: Int
    ): Bitmap {
        if (isPdf(source.mimeType, source.sourceFileName, document)) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(sourcePageIndex in 0 until renderer.pageCount) { "Μη έγκυρος αριθμός σελίδας." }
                    renderer.openPage(sourcePageIndex).use { page ->
                        return PdfBitmapRenderer.render(page, maxDimension)
                    }
                }
            }
        }
        return decodeImage(file, maxDimension)
    }

    private fun decodeImage(file: File, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Δεν ήταν δυνατή η ανάγνωση της εικόνας." }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Δεν ήταν δυνατή η αποκωδικοποίηση της εικόνας.")
        val rotation = runCatching { ExifInterface(file.absolutePath).rotationDegrees }.getOrDefault(0)
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun isPdf(mimeType: String, sourceName: String, document: DocumentEntity): Boolean =
        mimeType.equals("application/pdf", ignoreCase = true) ||
            sourceName.substringAfterLast('.', "").equals("pdf", ignoreCase = true) ||
            (sourceName.isBlank() && document.mimeType.equals("application/pdf", ignoreCase = true))

    private fun isSafeDocumentFile(file: File): Boolean {
        val root = context.filesDir.resolve("documents").canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return candidate == root || candidate.toPath().startsWith(root.toPath())
    }

    private companion object { const val MAX_LOGICAL_PAGES = 1_000 }
}
