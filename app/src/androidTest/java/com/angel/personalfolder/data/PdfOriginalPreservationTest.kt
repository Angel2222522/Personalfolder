package com.angel.personalfolder.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.angel.personalfolder.security.FileCrypto
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfOriginalPreservationTest {
    @Test
    fun externalOpenOfSinglePdfUsesOriginalBytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = AppDatabase.get(context)
        val id = "pdf-original-${UUID.randomUUID()}"
        val root = context.filesDir.resolve("documents/$id").apply { mkdirs() }
        val plainPdf = context.cacheDir.resolve("pdf-original-${UUID.randomUUID()}.pdf")
        val encrypted = root.resolve("page_0.pf")
        val generated = PdfDocument()
        var shared: File? = null
        try {
            val page = generated.startPage(PdfDocument.PageInfo.Builder(400, 500, 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Αυθεντικό PDF", 40f, 100f, Paint().apply { color = Color.BLACK; textSize = 24f })
            generated.finishPage(page)
            FileOutputStream(plainPdf).use(generated::writeTo)
            val originalBytes = plainPdf.readBytes()
            FileCrypto.encrypt(ByteArrayInputStream(originalBytes), encrypted)
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.documentDao().insert(
                    DocumentEntity(
                        id = id,
                        title = "PDF original",
                        originalFileName = "original.pdf",
                        mimeType = "application/pdf",
                        encryptedPath = encrypted.absolutePath,
                        pageCount = 1,
                        processingState = ProcessingState.PROCESSED,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                database.documentPageDao().insertAll(
                    listOf(DocumentPageEntity(id, 0, encrypted.absolutePath, sourceFileName = "original.pdf", mimeType = "application/pdf"))
                )
            }

            shared = ExportService(context).createSharePdf(id)
            assertArrayEquals("The external PDF path must not re-encode an imported PDF", originalBytes, shared.readBytes())
        } finally {
            shared?.delete()
            database.withTransaction {
                database.documentPageDao().deleteForDocument(id)
                database.documentDao().deleteById(id)
            }
            FileCrypto.deleteRecursively(root)
            generated.close()
            plainPdf.delete()
        }
    }

    @Test
    fun multiPagePdfRendersEachLogicalPageInOrder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = AppDatabase.get(context)
        val id = "pdf-pages-${UUID.randomUUID()}"
        val root = context.filesDir.resolve("documents/$id").apply { mkdirs() }
        val plainPdf = context.cacheDir.resolve("pdf-pages-${UUID.randomUUID()}.pdf")
        val encrypted = root.resolve("page_0.pf")
        val generated = PdfDocument()
        try {
            listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                val page = generated.startPage(PdfDocument.PageInfo.Builder(240, 240, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(40f, 40f, 200f, 200f, Paint().apply { this.color = color })
                generated.finishPage(page)
            }
            FileOutputStream(plainPdf).use(generated::writeTo)
            FileCrypto.encrypt(ByteArrayInputStream(plainPdf.readBytes()), encrypted)
            val now = System.currentTimeMillis()
            val document = DocumentEntity(
                id = id,
                title = "PDF πολλών σελίδων",
                originalFileName = "pages.pdf",
                mimeType = "application/pdf",
                encryptedPath = encrypted.absolutePath,
                pageCount = 2,
                processingState = ProcessingState.PROCESSED,
                createdAt = now,
                updatedAt = now
            )
            database.withTransaction {
                database.documentDao().insert(document)
                database.documentPageDao().insertAll(
                    listOf(DocumentPageEntity(id, 0, encrypted.absolutePath, sourceFileName = "pages.pdf", mimeType = "application/pdf"))
                )
            }

            val service = DocumentRenderService(context)
            val pages = service.logicalPages(document)
            assertEquals(listOf(0, 1), pages.map { it.number })
            val first = service.renderPage(document, pages[0], maxDimension = 480)
            val second = service.renderPage(document, pages[1], maxDimension = 480)
            try {
                assertEquals(Color.RED, first.getPixel(first.width / 2, first.height / 2))
                assertEquals(Color.BLUE, second.getPixel(second.width / 2, second.height / 2))
            } finally {
                first.recycle()
                second.recycle()
            }
        } finally {
            database.withTransaction {
                database.documentPageDao().deleteForDocument(id)
                database.documentDao().deleteById(id)
            }
            FileCrypto.deleteRecursively(root)
            generated.close()
            plainPdf.delete()
        }
    }
}
