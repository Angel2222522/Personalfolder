package com.angel.personalfolder.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TesseractOcrEngineTest {
    @Test
    fun recognizesRealGreekAndEnglishText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.filesDir.resolve("tesseract").deleteRecursively()

        val bitmap = Bitmap.createBitmap(1800, 650, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 120f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            canvas.drawText("ΕΛΛΑΔΑ 12345", 80f, 240f, paint)
            canvas.drawText("HELLO DOCUMENT", 80f, 480f, paint)

            val output = TesseractOcrEngine(context).recognize(bitmap)
            val normalized = output.uppercase(Locale.ROOT)

            assertTrue("Greek OCR failed. Output: $output", normalized.contains("ΕΛΛΑΔΑ"))
            assertTrue("English OCR failed. Output: $output", normalized.contains("HELLO"))
            assertTrue("Numeric OCR failed. Output: $output", normalized.contains("12345"))
        } finally {
            bitmap.recycle()
        }
    }
}
