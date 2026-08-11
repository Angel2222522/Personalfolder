package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TesseractOcrEngine(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val dataPath = prepareDataPath()
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataPath.absolutePath, "ell+eng")) return@withContext ""
            tess.setImage(bitmap)
            tess.getUTF8Text().orEmpty().trim()
        } finally {
            tess.recycle()
        }
    }

    private fun prepareDataPath(): File {
        val root = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        context.assets.list("tessdata").orEmpty().forEach { name ->
            val destination = File(tessdata, name)
            if (!destination.exists() || destination.length() == 0L) {
                context.assets.open("tessdata/$name").use { input -> destination.outputStream().use(input::copyTo) }
            }
        }
        return root
    }
}
