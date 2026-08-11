package com.angel.personalfolder.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative offline scan cleanup. It normalises camera orientation, removes a clearly
 * different border, and increases contrast without inventing document content.
 */
object ScannerImageProcessor {
    fun enhance(input: File, output: File, rotationDegrees: Int = 0): File {
        val bitmap = decode(input)
        var working = bitmap
        try {
            val exifRotation = runCatching { ExifInterface(input.absolutePath).rotationDegrees }.getOrDefault(0)
            val rotation = ((exifRotation + rotationDegrees) % 360 + 360) % 360
            if (rotation != 0) working = rotate(working, rotation)
            val crop = findPaperBounds(working)
            val cropped = if (crop != null && crop.width() * crop.height() < working.width * working.height * 0.97f) {
                Bitmap.createBitmap(working, crop.left, crop.top, crop.width(), crop.height())
            } else working
            if (cropped !== working && working !== bitmap) working.recycle()
            if (cropped !== bitmap && cropped !== working) {
                // The original is released below; cropped is now the working bitmap.
            }
            working = cropped
            val enhanced = contrast(working, 1.14f)
            output.parentFile?.mkdirs()
            output.outputStream().use { stream ->
                require(enhanced.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "Δεν ήταν δυνατή η αποθήκευση της σαρωμένης σελίδας." }
            }
            enhanced.recycle()
            return output
        } finally {
            if (!working.isRecycled) working.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Δεν ήταν δυνατή η ανάγνωση της φωτογραφίας." }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_SIDE) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Δεν ήταν δυνατή η αποκωδικοποίηση της φωτογραφίας.")
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun contrast(source: Bitmap, factor: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val offset = 128f * (1f - factor)
        val matrix = ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, offset,
            0f, factor, 0f, 0f, offset,
            0f, 0f, factor, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return output
    }

    private fun findPaperBounds(bitmap: Bitmap): android.graphics.Rect? {
        val sampleScale = min(1f, 320f / max(bitmap.width, bitmap.height).toFloat())
        val width = (bitmap.width * sampleScale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * sampleScale).toInt().coerceAtLeast(1)
        val sample = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = sample.getPixel(x, y)
                    val luminance = (Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f)
                    if (luminance >= PAPER_LUMINANCE) {
                        left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
                    }
                }
            }
            if (right < 0 || bottom < 0) return null
            val scaled = android.graphics.Rect(
                (left / sampleScale).toInt().coerceIn(0, bitmap.width - 1),
                (top / sampleScale).toInt().coerceIn(0, bitmap.height - 1),
                ((right + 1) / sampleScale).toInt().coerceIn(1, bitmap.width),
                ((bottom + 1) / sampleScale).toInt().coerceIn(1, bitmap.height)
            )
            val area = scaled.width().toLong() * scaled.height().toLong()
            val total = bitmap.width.toLong() * bitmap.height.toLong()
            return if (area > total * MIN_CROP_AREA && scaled.width() > 32 && scaled.height() > 32) scaled else null
        } finally {
            sample.recycle()
        }
    }

    private const val MAX_SIDE = 3200
    private const val PAPER_LUMINANCE = 150f
    private const val MIN_CROP_AREA = 0.35
}
