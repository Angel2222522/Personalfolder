package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import androidx.exifinterface.media.ExifInterface
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.DataOperationCoordinator
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.DocumentPageEntity
import com.angel.personalfolder.data.LibraryLimits
import com.angel.personalfolder.data.PdfBitmapRenderer
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

class DocumentProcessor(private val context: Context, private val database: AppDatabase) {
    suspend fun process(documentId: String): Result<Unit> = DataOperationCoordinator.withDocumentExclusive(documentId) { withContext(Dispatchers.IO) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext Result.failure(IllegalArgumentException("Το έγγραφο δεν βρέθηκε."))
        val temporaryFiles = mutableListOf<File>()
        var ocr: TesseractOcrEngine? = null
        try {
            DataOperationCoordinator.withExclusive {
                database.withTransaction {
                database.documentDao().update(
                    document.copy(
                        processingState = ProcessingState.PROCESSING,
                        processingError = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                    database.documentPageDao().clearOcrForDocument(document.id)
                }
            }
            val pageRows = database.documentPageDao().getForDocument(document.id).sortedBy { it.pageIndex }
            val sources = if (pageRows.isEmpty()) {
                listOf(DocumentPageEntity(document.id, 0, document.encryptedPath, sourceFileName = document.originalFileName, mimeType = document.mimeType))
            } else pageRows
            ocr = TesseractOcrEngine(context)
            val fullTextBuilder = StringBuilder()
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
                val remainingCharacters = (LibraryLimits.MAX_DOCUMENT_OCR_CHARS - fullTextBuilder.length).coerceAtLeast(0)
                val text = if (remainingCharacters == 0) {
                    ""
                } else if (isPdf(mime, source.sourceFileName, document)) {
                    recognizePdf(plain, ocr!!, remainingCharacters)
                } else {
                    val bitmap = decodeForOcr(plain)
                    try { ocr!!.recognize(bitmap).take(remainingCharacters) } finally { bitmap.recycle() }
                }
                if (text.isNotBlank()) {
                    if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append("\n\n")
                    fullTextBuilder.append(text.take((LibraryLimits.MAX_DOCUMENT_OCR_CHARS - fullTextBuilder.length).coerceAtLeast(0)))
                }
                DataOperationCoordinator.withExclusive {
                    database.documentPageDao().updateOcr(document.id, source.pageIndex, text)
                }
            }
            val fullText = fullTextBuilder.toString().trim()
            val metadata = MetadataExtractor.extract(fullText, document.title)
            // Read the latest row before applying OCR suggestions. A user may have edited
            // metadata while OCR was running; never overwrite that newer manual choice.
            val outcome: Pair<DocumentEntity?, Result<Unit>> = DataOperationCoordinator.withExclusive {
                val latest = database.documentDao().getById(document.id) ?: document
                if (fullText.isBlank()) {
                    val message = "Το OCR ολοκληρώθηκε χωρίς αναγνωρίσιμο κείμενο."
                    database.documentDao().update(latest.copy(processingState = ProcessingState.FAILED, processingError = message, updatedAt = System.currentTimeMillis()))
                    return@withExclusive null to Result.failure(IllegalStateException(message))
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
                updated to Result.success(Unit)
            }
            val updated = outcome.first
            // The document and OCR state are already durable. WorkManager
            // reconciliation may repair a reminder scheduling failure later;
            // it must not turn a successful OCR run into a false FAILED row.
            if (updated != null) {
                runCatching { ReminderScheduler.replaceForDocument(context, document.id, updated.title, updated.expiryDate) }
                    .onFailure { android.util.Log.w("PersonalFolder", "Reminder reconciliation deferred", it) }
            }
            outcome.second
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    DataOperationCoordinator.withExclusive {
                        database.withTransaction {
                            val latest = database.documentDao().getById(document.id) ?: document
                            database.documentPageDao().clearOcrForDocument(document.id)
                            database.documentDao().update(
                                latest.copy(
                                    processingState = ProcessingState.FAILED,
                                    processingError = error.message?.take(300) ?: "Η επεξεργασία διακόπηκε και μπορεί να επαναληφθεί.",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }.onFailure { cleanupError ->
                    android.util.Log.e("PersonalFolder", "Could not persist OCR failure state", cleanupError)
                }
            }
            if (error is CancellationException || error is OutOfMemoryError) throw error
            Result.failure(error)
        } finally {
            withContext(NonCancellable) {
                temporaryFiles.forEach { it.delete() }
                ocr?.close()
            }
        }
    } }

    private suspend fun recognizePdf(file: File, ocr: TesseractOcrEngine, maxCharacters: Int): String {
        if (maxCharacters <= 0) return ""
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount <= LibraryLimits.MAX_LOGICAL_PAGES_PER_DOCUMENT) { "Το έγγραφο περιέχει υπερβολικά πολλές σελίδες." }
                return buildString {
                    for (index in 0 until renderer.pageCount) {
                        if (length >= maxCharacters) break
                        renderer.openPage(index).use { page ->
                            val bitmap = PdfBitmapRenderer.render(page, OCR_MAX_SIDE)
                            try {
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
        require(bounds.outWidth <= MAX_IMAGE_SIDE && bounds.outHeight <= MAX_IMAGE_SIDE) { "Η εικόνα έχει υπερβολική ανάλυση." }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) { "Η εικόνα είναι υπερβολικά μεγάλη." }
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

    companion object {
        const val OCR_MAX_SIDE = 2600
        const val MAX_IMAGE_SIDE = 12_000
        const val MAX_IMAGE_PIXELS = 50_000_000L

        suspend fun reconcileInterruptedProcessing(database: AppDatabase) = withContext(Dispatchers.IO) {
            DataOperationCoordinator.withExclusiveDuringStartup {
                database.documentDao().getByProcessingState(ProcessingState.PROCESSING).forEach { document ->
                    database.withTransaction {
                        database.documentPageDao().clearOcrForDocument(document.id)
                        database.documentDao().update(
                            document.copy(
                                processingState = ProcessingState.FAILED,
                                processingError = "Η προηγούμενη επεξεργασία διακόπηκε. Επίλεξε επανάληψη OCR.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }
}
