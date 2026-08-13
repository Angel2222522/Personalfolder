package com.angel.personalfolder.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders one PDF page into an opaque display/OCR input bitmap.
 *
 * PdfRenderer may leave pixels transparent when a PDF page has no explicit
 * background. Filling the ARGB bitmap first prevents those pixels from being
 * composited as black by a viewer or OCR engine. The returned bitmap is a new
 * buffer owned by the caller; it is never the encrypted source or a viewer
 * cache shared with another pipeline.
 */
object PdfBitmapRenderer {
    fun render(page: PdfRenderer.Page, maxDimension: Int): Bitmap {
        val scale = minOf(
            3f,
            maxDimension.toFloat() / max(page.width, page.height).coerceAtLeast(1)
        )
        val rendered = Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        // Do this before rendering. It is intentional that the bitmap is
        // opaque white instead of relying on the PDF's transparency state.
        rendered.eraseColor(Color.WHITE)
        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Composite once more onto an opaque surface. Some PDF pages contain
        // transparent pixels and PdfRenderer is allowed to preserve alpha;
        // this final composition makes their visual result deterministic.
        val opaque = Bitmap.createBitmap(rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(rendered, 0f, 0f, null)
        }
        opaque.setHasAlpha(false)
        rendered.recycle()
        return opaque
    }
}
