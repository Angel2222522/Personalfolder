package com.angel.personalfolder.data

import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBitmapRendererTest {
    @Test
    fun normalPdfPageRemainsBrightAndTextRemainsDark() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pdf = File(context.cacheDir, "pdf-render-test-${System.nanoTime()}.pdf")
        val document = android.graphics.pdf.PdfDocument()
        try {
            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, 1).create())
            // Leave the page background implicit to exercise the renderer's
            // transparent/empty-pixel handling, and draw representative text.
            page.canvas.drawRect(120f, 300f, 480f, 380f, Paint().apply { color = Color.BLACK })
            page.canvas.drawText("Ελληνικό έγγραφο", 120f, 250f, Paint().apply { color = Color.BLACK; textSize = 32f })
            document.finishPage(page)
            FileOutputStream(pdf).use(document::writeTo)

            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { sourcePage ->
                        val bitmap = PdfBitmapRenderer.render(sourcePage, 900)
                        try {
                            val background = bitmap.getPixel(10, 10)
                            val darkRegion = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                            assertTrue("PDF background became dark: $background", Color.luminance(background) > 0.85f)
                            assertTrue("PDF text/shape was not rendered dark: $darkRegion", Color.luminance(darkRegion) < 0.2f)
                            assertTrue("Rendered bitmap must be opaque", Color.alpha(background) == 255)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            document.close()
            pdf.delete()
        }
    }

    @Test
    fun separateRendersKeepViewerBitmapIndependentFromOcrCopy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pdf = File(context.cacheDir, "pdf-copy-test-${System.nanoTime()}.pdf")
        val document = android.graphics.pdf.PdfDocument()
        try {
            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 300, 1).create())
            page.canvas.drawColor(Color.WHITE)
            document.finishPage(page)
            FileOutputStream(pdf).use(document::writeTo)
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { sourcePage ->
                        val viewerBitmap = PdfBitmapRenderer.render(sourcePage, 600)
                        val ocrBitmap = PdfBitmapRenderer.render(sourcePage, 600)
                        try {
                            Canvas(ocrBitmap).drawColor(Color.BLACK)
                            assertTrue(Color.luminance(viewerBitmap.getPixel(10, 10)) > 0.85f)
                            assertTrue(Color.luminance(ocrBitmap.getPixel(10, 10)) < 0.1f)
                        } finally {
                            viewerBitmap.recycle()
                            ocrBitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            document.close()
            pdf.delete()
        }
    }
}
