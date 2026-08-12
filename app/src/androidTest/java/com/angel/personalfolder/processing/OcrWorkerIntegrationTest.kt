package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.DocumentPageEntity
import com.angel.personalfolder.data.ProcessingState
import com.angel.personalfolder.security.FileCrypto
import com.angel.personalfolder.workers.OcrWorker
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrWorkerIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private lateinit var documentId: String
    private lateinit var documentRoot: File

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.get(context)
        documentId = "ocr-test-${UUID.randomUUID()}"
        documentRoot = context.filesDir.resolve("documents/$documentId").apply { mkdirs() }
    }

    @After
    fun tearDown() = runBlocking {
        database.documentPageDao().deleteForDocument(documentId)
        database.documentDao().deleteById(documentId)
        FileCrypto.deleteRecursively(documentRoot)
    }

    @Test
    fun workerReadsGreekImagePersistsTextAndUpdatesSearchIndex() = runBlocking {
        val source = createTextImage("ΔΗΜΟΣ ΘΕΣΣΑΛΟΝΙΚΗΣ", "Αριθμός πρωτοκόλλου: AB-123/2026")
        insertDocument(source, "image/png", "greek.png")

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Success)
        val document = database.documentDao().getById(documentId)
        assertNotNull(document)
        assertEquals(ProcessingState.PROCESSED, document!!.processingState)
        val foldedText = fold(document.ocrText)
        assertTrue("OCR text: ${document.ocrText}", foldedText.contains("δημος"))
        assertTrue(database.documentPageDao().getForDocument(documentId).single().ocrText.isNotBlank())
        val searchResults = database.documentDao().search(
            ftsQuery = "\"δημος\"*",
            category = "",
            processingState = "",
            caseId = null,
            today = "2026-08-12",
            expiryBefore = null
        ).first()
        assertTrue(searchResults.any { it.id == documentId })
        val accentlessResults = database.documentDao().search(
            ftsQuery = "\"ημερομηνια\"*",
            category = "",
            processingState = "",
            caseId = null,
            today = "2026-08-12",
            expiryBefore = null
        ).first()
        assertTrue(accentlessResults.any { it.id == documentId })
    }

    @Test
    fun workerRendersMultiPagePdfAndCombinesText() = runBlocking {
        val source = createPdf(
            listOf("ΔΗΜΟΣ ΘΕΣΣΑΛΟΝΙΚΗΣ"),
            listOf("Ημερομηνία έκδοσης: 03/08/2026")
        )
        insertDocument(source, "application/pdf", "multi-page.pdf")

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Success)
        val document = database.documentDao().getById(documentId)
        assertNotNull(document)
        assertEquals(ProcessingState.PROCESSED, document!!.processingState)
        val foldedText = fold(document.ocrText)
        assertTrue("OCR text: ${document.ocrText}", foldedText.contains("δημος"))
        assertTrue("OCR text: ${document.ocrText}", foldedText.contains("2026"))
    }

    @Test
    fun workerReportsUnreadableInputAsFailed() = runBlocking {
        val source = context.cacheDir.resolve("ocr-test-${UUID.randomUUID()}.bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        insertDocument(source, "image/png", "broken.png")

        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Failure)
        val document = database.documentDao().getById(documentId)
        assertNotNull(document)
        assertEquals(ProcessingState.FAILED, document!!.processingState)
        assertTrue(document.processingError.orEmpty().isNotBlank())
    }

    private suspend fun runWorker(): ListenableWorker.Result {
        return TestListenableWorkerBuilder<OcrWorker>(context)
            .setInputData(workDataOf(OcrWorker.KEY_DOCUMENT_ID to documentId))
            .build()
            .doWork()
    }

    private suspend fun insertDocument(source: File, mimeType: String, name: String) {
        val encrypted = documentRoot.resolve("page_0.pf")
        source.inputStream().use { input -> FileCrypto.encrypt(input, encrypted) }
        val now = System.currentTimeMillis()
        database.documentDao().insert(
            DocumentEntity(
                id = documentId,
                title = "OCR δοκιμή",
                originalFileName = name,
                mimeType = mimeType,
                encryptedPath = encrypted.absolutePath,
                pageCount = 1,
                processingState = ProcessingState.QUEUED,
                createdAt = now,
                updatedAt = now
            )
        )
        database.documentPageDao().insertAll(
            listOf(DocumentPageEntity(documentId, 0, encrypted.absolutePath, sourceFileName = name, mimeType = mimeType))
        )
        source.delete()
    }

    private fun createTextImage(vararg lines: String): File {
        val bitmap = Bitmap.createBitmap(1800, 720, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 64f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            lines.forEachIndexed { index, line -> drawText(line, 80f, 180f + index * 150f, paint) }
        }
        return context.cacheDir.resolve("ocr-test-${UUID.randomUUID()}.png").also { file ->
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
            bitmap.recycle()
        }
    }

    private fun createPdf(vararg pages: List<String>): File {
        val pdf = PdfDocument()
        pages.forEachIndexed { pageIndex, lines ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(1800, 720, pageIndex + 1).create())
            page.canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 64f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            lines.forEachIndexed { index, line -> page.canvas.drawText(line, 80f, 180f + index * 150f, paint) }
            pdf.finishPage(page)
        }
        return context.cacheDir.resolve("ocr-test-${UUID.randomUUID()}.pdf").also { file ->
            file.outputStream().use { output -> pdf.writeTo(output) }
            pdf.close()
        }
    }

    private fun fold(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
}
