package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.data.ReminderScheduler
import com.angel.personalfolder.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DocumentProcessor(private val context: Context, private val database: AppDatabase) {
    suspend fun process(documentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val document = database.documentDao().getById(documentId) ?: return@withContext Result.failure(IllegalArgumentException("Document not found"))
        database.documentDao().updateProcessing(
            id = document.id,
            category = document.category,
            ocrText = document.ocrText,
            provider = document.provider,
            issuedDate = document.issuedDate,
            expiryDate = document.expiryDate,
            protocolNumber = document.protocolNumber,
            metadataJson = document.extractedMetadataJson,
            state = ProcessingState.PROCESSING,
            error = null,
            updatedAt = System.currentTimeMillis()
        )
        val temporaryFiles = mutableListOf<File>()
        try {
            val pageRows = database.documentPageDao().getForDocument(document.id)
            val sources = if (pageRows.isEmpty()) {
                listOf(PageSource(0, File(document.encryptedPath)))
            } else {
                pageRows.map { PageSource(it.pageIndex, File(it.encryptedPath)) }
            }
            val ocr = TesseractOcrEngine(context)
            val pageTexts = mutableListOf<String>()
            for (source in sources) {
                val plain = File(context.cacheDir, "ocr_${UUID.randomUUID()}").also { temporaryFiles += it }
                FileCrypto.decryptToTemp(source.encryptedFile, plain)
                val text = if (document.mimeType == "application/pdf" || plain.extension.equals("pdf", true)) {
                    recognizePdf(plain, ocr)
                } else {
                    val bitmap = BitmapFactory.decodeFile(plain.absolutePath)
                        ?: throw IllegalStateException("Δεν ήταν δυνατή η αποκωδικοποίηση της εικόνας.")
                    try { ocr.recognize(bitmap) } finally { bitmap.recycle() }
                }
                pageTexts += text
                if (pageRows.isNotEmpty()) database.documentPageDao().updateOcr(document.id, source.index, text)
            }
            val fullText = pageTexts.joinToString("\n\n").trim()
            val metadata = MetadataExtractor.extract(fullText, document.title)
            database.documentDao().updateProcessing(
                id = document.id,
                category = metadata.category,
                ocrText = fullText,
                provider = metadata.provider,
                issuedDate = metadata.issuedDate,
                expiryDate = metadata.expiryDate,
                protocolNumber = metadata.protocolNumber,
                metadataJson = metadata.json,
                state = ProcessingState.PROCESSED,
                error = null,
                updatedAt = System.currentTimeMillis()
            )
            ReminderScheduler.replaceForDocument(context, document.id, document.title, metadata.expiryDate)
            Result.success(Unit)
        } catch (error: Throwable) {
            database.documentDao().updateProcessing(
                id = document.id,
                category = document.category,
                ocrText = document.ocrText,
                provider = document.provider,
                issuedDate = document.issuedDate,
                expiryDate = document.expiryDate,
                protocolNumber = document.protocolNumber,
                metadataJson = document.extractedMetadataJson,
                state = ProcessingState.FAILED,
                error = error.message ?: "Άγνωστο σφάλμα",
                updatedAt = System.currentTimeMillis()
            )
            Result.failure(error)
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    }

    private suspend fun recognizePdf(file: File, ocr: TesseractOcrEngine): String {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { renderer ->
            buildString {
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(2f, 2200f / page.width.coerceAtLeast(1))
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            append(ocr.recognize(bitmap))
                            append("\n\n")
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }.trim().toString()
        }.also { descriptor.close() }
    }

    private data class PageSource(val index: Int, val encryptedFile: File)
}
