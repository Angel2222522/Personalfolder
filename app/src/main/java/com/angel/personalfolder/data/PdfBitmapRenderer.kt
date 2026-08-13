package com.angel.personalfolder.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders one PDF page into an opaque bitmap without ever modifying the source PDF.
 * Display rendering remains conservative, while OCR can request an accuracy-first
 * raster close to 300 DPI for small/normal document pages.
 */
object PdfBitmapRenderer {
    fun render(page: PdfRenderer.Page, maxDimension: Int): Bitmap =
        renderInternal(
            page = page,
            maxDimension = maxDimension,
            preferredScale = DISPLAY_MAX_SCALE,
            renderMode = PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

    fun renderForOcr(
        page: PdfRenderer.Page,
        maxDimension: Int = OCR_MAX_DIMENSION,
        targetDpi: Int = OCR_TARGET_DPI
    ): Bitmap {
        require(targetDpi > 0) { "Το DPI OCR πρέπει να είναι θετικό." }
        val preferredScale = targetDpi / PDF_POINTS_PER_INCH
        return renderInternal(
            page = page,
            maxDimension = maxDimension,
            preferredScale = preferredScale,
            renderMode = PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )
    }

    private fun renderInternal(
        page: PdfRenderer.Page,
        maxDimension: Int,
        preferredScale: Float,
        renderMode: Int
    ): Bitmap {
        require(maxDimension > 0) { "Η μέγιστη διάσταση πρέπει να είναι θετική." }
        val longestPageSide = max(page.width, page.height).coerceAtLeast(1)
        val dimensionScale = maxDimension.toFloat() / longestPageSide
        val scale = minOf(preferredScale, dimensionScale).coerceAtLeast(MIN_RENDER_SCALE)
        val rendered = Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )

        // PdfRenderer may preserve transparency. OCR must always receive a
        // deterministic white page rather than transparent pixels rendered dark.
        rendered.eraseColor(Color.WHITE)
        page.render(rendered, null, null, renderMode)

        val opaque = Bitmap.createBitmap(rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(rendered, 0f, 0f, null)
        }
        opaque.setHasAlpha(false)
        rendered.recycle()
        return opaque
    }

    private const val DISPLAY_MAX_SCALE = 3f
    private const val OCR_TARGET_DPI = 300
    private const val OCR_MAX_DIMENSION = 3_600
    private const val PDF_POINTS_PER_INCH = 72f
    private const val MIN_RENDER_SCALE = 0.25f
}
