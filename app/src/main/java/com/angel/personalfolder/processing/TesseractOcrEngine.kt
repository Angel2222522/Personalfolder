package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TesseractOcrEngine(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val dataPath = withContext(Dispatchers.IO) { prepareDataPath() }
        val tess = TessBaseAPI()
        try {
            check(tess.init(dataPath.absolutePath, "ell+eng")) {
                "Δεν φορτώθηκαν τα ελληνικά/αγγλικά δεδομένα OCR."
            }
            tess.setImage(bitmap)
            tess.getUTF8Text().orEmpty().trim()
        } finally {
            tess.recycle()
        }
    }

    private suspend fun prepareDataPath(): File = modelLock.withLock {
        val root = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        context.assets.list("tessdata").orEmpty().forEach { name ->
            require(name.endsWith(".traineddata")) { "Μη έγκυρο αρχείο OCR." }
            val destination = File(tessdata, name)
            if (!destination.exists() || destination.length() < MIN_TRAINEDDATA_BYTES) {
                val temporary = File(tessdata, ".${name}.${System.nanoTime()}.part")
                try {
                    context.assets.open("tessdata/$name").use { input -> temporary.outputStream().use(input::copyTo) }
                    require(temporary.length() >= MIN_TRAINEDDATA_BYTES) { "Το αρχείο OCR είναι ελλιπές: $name" }
                    require(temporary.renameTo(destination)) { "Δεν ήταν δυνατή η εγκατάσταση του αρχείου OCR: $name" }
                } finally {
                    temporary.delete()
                }
            }
            require(destination.length() >= MIN_TRAINEDDATA_BYTES) { "Το αρχείο OCR είναι ελλιπές: $name" }
        }
        require(File(tessdata, "ell.traineddata").isFile && File(tessdata, "eng.traineddata").isFile) {
            "Λείπουν τα ελληνικά ή αγγλικά δεδομένα OCR."
        }
        root
    }

    private companion object {
        val modelLock = Mutex()
        const val MIN_TRAINEDDATA_BYTES = 100_000L
    }
}
