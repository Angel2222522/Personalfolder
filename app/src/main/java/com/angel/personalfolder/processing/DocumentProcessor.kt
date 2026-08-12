package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.DocumentPageEntity
import com.angel.personalfolder.data.LibraryOperationCoordinator
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class DocumentProcessor(private val context: Context, private val database: AppDatabase) {
    suspend fun process(documentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        LibraryOperationCoordinator.withExclusive { processInternal(documentId) }
    }

    private suspend fun processInternal(documentId: String): Result<Unit> {
        val document = database.documentDao().getById(documentId)
            ?: return Result.failure(IllegalArgumentException("Το έγγραφο δεν βρέθηκε."))
        database.documentDao().updateProcessingState(
            id = document.id,
            state = ProcessingState.PROCESSING,
            error = null,
            updatedAt = System.currentTimeMillis()
        )
        val temporaryFiles = mutableListOf<File>()
        return try {
            val pageRows = database.documentPageDao().getForDocument(document.id).sortedBy { it.pageIndex }
            val sources = if (pageRows.isEmpty()) {
                listOf(DocumentPageEntity(document.id, 0, document.encryptedPath, sourceFileName = document.originalFileName, mimeType = document.mimeType))
            } else pageRows
            val ocr = TesseractOcrEngine(context)
            val fullTextBuilder = StringBuilder()
            val pageResults = mutableListOf<PageOcr>()
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
                val text = if (remainingCharacters == 0) {
                    ""
                } else if (DocumentSourceClassifier.isPdf(mime, source.sourceFileName, document.mimeType)) {
                    recognizePdf(plain, ocr, remainingCharacters)
                } else {
                    val bitmap = decodeForOcr(plain)
                    try { ocr.recognize(bitmap).take(remainingCharacters) } finally { bitmap.recycle() }
                }
                if (text.isNotBlank()) {
                    if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append("\n\n")
                    fullTextBuilder.append(text.take((MAX_DOCUMENT_OCR_CHARS - fullTextBuilder.length).coerceAtLeast(0)))
                }
                pageResults += PageOcr(source.pageIndex, text)
            }
            val fullText = fullTextBuilder.toString().trim()
            val metadata = MetadataExtractor.extract(fullText, document.title)
            // Read the latest row before applying OCR suggestions. A user may have edited
            // metadata while OCR was running; never overwrite that newer manual choice.
            val latest = database.documentDao().getById(document.id) ?: document
            val keepManualMetadata = latest.metadataManuallyEdited
            if (fullText.isBlank()) {
                val message = "Το OCR ολοκληρώθηκε χωρίς αναγνωρίσιμο κείμενο."
                database.withTransaction {
                    pageResults.forEach { result ->
                        database.documentPageDao().updateOcr(document.id, result.pageIndex, result.text)
                    }
                    database.documentDao().updateProcessing(
                        id = document.id,
                        category = latest.category,
                        ocrText = latest.ocrText,
                        provider = latest.provider,
                        issuedDate = latest.issuedDate,
                        expiryDate = latest.expiryDate,
                        protocolNumber = latest.protocolNumber,
                        metadataJson = latest.extractedMetadataJson,
                        state = ProcessingState.FAILED,
                        error = message,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                return Result.failure(IllegalStateException(message))
            }
            database.withTransaction {
                pageResults.forEach { result ->
                    database.documentPageDao().updateOcr(document.id, result.pageIndex, result.text)
                }
                database.documentDao().updateProcessing(
                    id = document.id,
                    category = if (keepManualMetadata) latest.category else metadata.category,
                    ocrText = fullText,
                    provider = if (keepManualMetadata) latest.provider else metadata.provider,
                    issuedDate = if (keepManualMetadata) latest.issuedDate else metadata.issuedDate,
                    expiryDate = if (keepManualMetadata) latest.expiryDate else metadata.expiryDate,
                    protocolNumber = if (keepManualMetadata) latest.protocolNumber else metadata.protocolNumber,
                    metadataJson = metadata.json,
                    state = ProcessingState.PROCESSED,
                    error = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
            val finalExpiry = if (keepManualMetadata) {
                latest.expiryDate
            } else if (metadata.expiryConfidence == "high" || metadata.expiryConfidence == "medium") {
                metadata.expiryDate
            } else {
                null
            }
            ReminderScheduler.replaceForDocument(context, document.id, latest.title, finalExpiry)
            Result.success(Unit)
        } catch (error: CancellationException) {
            markInterrupted(document.id, "Η επεξεργασία OCR διακόπηκε και μπορεί να επαναληφθεί.")
            throw error
        } catch (error: OutOfMemoryError) {
            markFailed(document.id, "Η συσκευή δεν είχε αρκετή μνήμη για την επεξεργασία του εγγράφου.")
            throw error
        } catch (error: IOException) {
            markInterrupted(document.id, "Προσωρινό σφάλμα ανάγνωσης. Η επεξεργασία θα επαναληφθεί.")
            Result.failure(error)
        } catch (error: Throwable) {
            markFailed(document.id, error.message?.take(300) ?: "Άγνωστο σφάλμα επεξεργασίας.")
            Result.failure(error)
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    }

    private suspend fun markInterrupted(documentId: String, message: String) = withContext(NonCancellable) {
        database.documentDao().updateProcessingStateIfCurrent(
            id = documentId,
            expectedState = ProcessingState.PROCESSING,
            state = ProcessingState.QUEUED,
            error = message,
            updatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun markFailed(documentId: String, message: String) = withContext(NonCancellable) {
        val latest = database.documentDao().getById(documentId) ?: return@withContext
        if (latest.processingState == ProcessingState.PROCESSING) {
            database.documentDao().updateProcessing(
                id = documentId,
                category = latest.category,
                ocrText = latest.ocrText,
                provider = latest.provider,
                issuedDate = latest.issuedDate,
                expiryDate = latest.expiryDate,
                protocolNumber = latest.protocolNumber,
                metadataJson = latest.extractedMetadataJson,
                state = ProcessingState.FAILED,
                error = message,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun recognizePdf(file: File, ocr: TesseractOcrEngine, maxCharacters: Int): String {
        if (maxCharacters <= 0) return ""
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount <= MAX_LOGICAL_PAGES) { "Το έγγραφο περιέχει υπερβολικά πολλές σελίδες." }
                return buildString {
                    for (index in 0 until renderer.pageCount) {
                        if (length >= maxCharacters) break
                        renderer.openPage(index).use { page ->
                            val scale = minOf(3f, 2200f / max(page.width, page.height).coerceAtLeast(1))
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).roundToInt().coerceAtLeast(1),
                                (page.height * scale).roundToInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val text = ocr.recognize(bitmap)
                                if (text.isNotBlank()) {
                                    if (isNotEmpty()) append("\n\n")
                                    append(text.take((maxCharacters - length).coerceAtLeast(0)))
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }.trim().toString()
            }
        }
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

    private data class PageOcr(val pageIndex: Int, val text: String)

    private companion object {
        const val OCR_MAX_SIDE = 2600
        const val MAX_DOCUMENT_OCR_CHARS = 2_000_000
        const val MAX_LOGICAL_PAGES = 1_000
    }
}
