package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.DataOperationCoordinator
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.DocumentPageEntity
import com.angel.personalfolder.data.PdfBitmapRenderer
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

class DocumentProcessor(private val context: Context, private val database: AppDatabase) {
    suspend fun process(documentId: String): Result<Unit> = DataOperationCoordinator.withExclusive { withContext(Dispatchers.IO) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext Result.failure(IllegalArgumentException("Το έγγραφο δεν βρέθηκε."))
        database.documentDao().update(document.copy(processingState = ProcessingState.PROCESSING, processingError = null, updatedAt = System.currentTimeMillis()))
        val temporaryFiles = mutableListOf<File>()
        try {
            val pageRows = database.documentPageDao().getForDocument(document.id).sortedBy { it.pageIndex }
            val sources = if (pageRows.isEmpty()) {
                listOf(DocumentPageEntity(document.id, 0, document.encryptedPath, sourceFileName = document.originalFileName, mimeType = document.mimeType))
            } else pageRows
            val ocr = TesseractOcrEngine(context)
            val fullTextBuilder = StringBuilder()
            val metadataAssistBuilder = StringBuilder()
            for (source in sources) {
                val encrypted = File(source.encryptedPath)
                require(FileCrypto.isPrivateDocumentFile(context, encrypted)) { "Η σελίδα βρίσκεται εκτός του ιδιωτικού χώρου." }
                require(encrypted.isFile) { "Λείπει η σελίδα του εγγράφου." }
                val plain = File(context.cacheDir, "ocr/ocr_${UUID.randomUUID()}.tmp").also {
                    it.parentFile?.mkdirs()
                    temporaryFiles += it
                }
                FileCrypto.decryptToTemp(encrypted, plain)
                val mime = source.mimeType.ifBlank { document.mimeType }
                val remainingCharacters = (MAX_DOCUMENT_OCR_CHARS - fullTextBuilder.length).coerceAtLeast(0)
                val recognized = if (remainingCharacters == 0) {
                    RecognizedContent("", "")
                } else if (isPdf(mime, source.sourceFileName, document)) {
                    recognizePdf(plain, ocr, remainingCharacters)
                } else {
                    val bitmap = decodeForOcr(plain)
                    try {
                        val result = ocr.recognizeDetailed(
                            bitmap,
                            includeMetadataAssist = source.pageIndex < MAX_METADATA_ASSIST_PAGES
                        )
                        RecognizedContent(result.text.take(remainingCharacters), result.metadataAssistText)
                    } finally {
                        bitmap.recycle()
                    }
                }
                val text = recognized.text
                if (text.isNotBlank()) {
                    if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append("\n\n")
                    fullTextBuilder.append(text.take((MAX_DOCUMENT_OCR_CHARS - fullTextBuilder.length).coerceAtLeast(0)))
                }
                appendMetadataAssist(metadataAssistBuilder, recognized.metadataAssistText)
                database.documentPageDao().updateOcr(document.id, source.pageIndex, text)
            }
            val fullText = fullTextBuilder.toString().trim()
            val metadata = MetadataExtractor.extract(
                fullText,
                document.title,
                metadataAssistBuilder.toString()
            )
            // Read the latest row before applying OCR suggestions. A user may have edited
            // metadata while OCR was running; never overwrite that newer manual choice.
            val latest = database.documentDao().getById(document.id) ?: document
            if (fullText.isBlank()) {
                val message = "Η αναγνώριση ολοκληρώθηκε χωρίς αναγνωρίσιμο κείμενο."
                database.documentDao().update(latest.copy(processingState = ProcessingState.FAILED, processingError = message, updatedAt = System.currentTimeMillis()))
                return@withContext Result.failure(IllegalStateException(message))
            }
            // Keep low-confidence candidates in their fields together with
            // confidence/provenance. They remain visibly unconfirmed and
            // cannot create reminders, but can be confirmed per field later.
            val updated = MetadataApplicationPolicy.apply(latest, metadata).copy(
                ocrText = fullText,
                extractedMetadataJson = metadata.json,
                processingState = ProcessingState.PROCESSED,
                processingError = null,
                updatedAt = System.currentTimeMillis()
            )
            database.documentDao().update(updated)
            val finalExpiry = updated.expiryDate
            ReminderScheduler.replaceForDocument(context, document.id, updated.title, finalExpiry)
            Result.success(Unit)
        } catch (error: Throwable) {
            if (error is CancellationException || error is OutOfMemoryError) throw error
            val latest = database.documentDao().getById(document.id) ?: document
            database.documentDao().update(latest.copy(
                processingState = ProcessingState.FAILED,
                processingError = error.message?.take(300) ?: "Άγνωστο σφάλμα επεξεργασίας.",
                updatedAt = System.currentTimeMillis()
            ))
            Result.failure(error)
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    } }

    private suspend fun recognizePdf(file: File, ocr: TesseractOcrEngine, maxCharacters: Int): RecognizedContent {
        if (maxCharacters <= 0) return RecognizedContent("", "")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount <= MAX_LOGICAL_PAGES) { "Το έγγραφο περιέχει υπερβολικά πολλές σελίδες." }
                val visible = StringBuilder()
                val metadataAssist = StringBuilder()
                for (index in 0 until renderer.pageCount) {
                    if (visible.length >= maxCharacters) break
                    renderer.openPage(index).use { page ->
                        val nativeText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                            OcrTextPostProcessor.normalizeNativePdfText(
                                page.textContents.joinToString("\n") { content -> content.text }
                            )
                        } else {
                            ""
                        }

                        val pageResult = if (OcrTextPostProcessor.isUsableNativePdfText(nativeText)) {
                            OcrRecognition(nativeText)
                        } else {
                            val bitmap = PdfBitmapRenderer.renderForOcr(page, OCR_MAX_SIDE)
                            try {
                                ocr.recognizeDetailed(
                                    bitmap,
                                    includeMetadataAssist = index < MAX_METADATA_ASSIST_PAGES
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }

                        if (pageResult.text.isNotBlank()) {
                            if (visible.isNotEmpty()) visible.append("\n\n")
                            visible.append(pageResult.text.take((maxCharacters - visible.length).coerceAtLeast(0)))
                        }
                        appendMetadataAssist(metadataAssist, pageResult.metadataAssistText)
                    }
                }
                return RecognizedContent(visible.toString().trim(), metadataAssist.toString().trim())
            }
        }
    }

    /**
     * Sparse OCR is intentionally kept out of the visible/stored OCR body. It is
     * bounded to a few early pages and a small character budget, then supplied
     * only to MetadataExtractor as secondary evidence for title/provider/date/
     * protocol candidates missed by normal page segmentation.
     */
    private fun appendMetadataAssist(builder: StringBuilder, value: String) {
        if (value.isBlank() || builder.length >= MAX_METADATA_ASSIST_CHARS) return
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(value.take((MAX_METADATA_ASSIST_CHARS - builder.length).coerceAtLeast(0)))
    }

    private fun decodeForOcr(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Δεν ήταν δυνατή η ανάγνωση της εικόνας." }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > OCR_MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Δεν ήταν δυνατή η αποκωδικοποίηση της εικόνας.")
        val rotation = runCatching { ExifInterface(file.absolutePath).rotationDegrees }.getOrDefault(0)
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun isPdf(mimeType: String, sourceName: String, document: DocumentEntity): Boolean =
        mimeType.equals("application/pdf", true) ||
            sourceName.substringAfterLast('.', "").equals("pdf", true) ||
            (sourceName.isBlank() && document.mimeType.equals("application/pdf", true))

    private data class RecognizedContent(val text: String, val metadataAssistText: String)

    private companion object {
        const val OCR_MAX_SIDE = 3_600
        const val MAX_DOCUMENT_OCR_CHARS = 2_000_000
        const val MAX_LOGICAL_PAGES = 1_000
        const val MAX_METADATA_ASSIST_PAGES = 3
        const val MAX_METADATA_ASSIST_CHARS = 100_000
    }
}
