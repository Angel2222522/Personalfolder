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
    private var tess: TessBaseAPI? = null

    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val engine = tess ?: TessBaseAPI().also { created ->
            val dataPath = withContext(Dispatchers.IO) { prepareDataPath() }
            check(created.init(dataPath.absolutePath, "ell+eng")) {
                created.recycle()
                "Δεν φορτώθηκαν τα ελληνικά/αγγλικά δεδομένα OCR."
            }
            tess = created
        }
        engine.setImage(bitmap)
        engine.getUTF8Text().orEmpty().trim()
    }

    /** Releases the native OCR engine after one document/job finishes. */
    fun close() {
        tess?.recycle()
        tess = null
    }

    private suspend fun prepareDataPath(): File = modelLock.withLock {
        val root = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        val versionFile = File(root, ".model-version")
        val installedVersion = runCatching { versionFile.readText() }.getOrNull()

        if (installedVersion != MODEL_BUNDLE_VERSION) {
            File(tessdata, "ell.traineddata").delete()
            File(tessdata, "eng.traineddata").delete()
            versionFile.delete()
        }

        val bundledModels = context.assets.list("tessdata").orEmpty().toSet()
        require(bundledModels.containsAll(REQUIRED_MODELS)) {
            "Λείπουν τα ελληνικά ή αγγλικά δεδομένα OCR."
        }

        for (name in REQUIRED_MODELS) {
            val destination = File(tessdata, name)
            if (!destination.exists() || destination.length() < MIN_TRAINEDDATA_BYTES) {
                val temporary = File(tessdata, ".${name}.${System.nanoTime()}.part")
                try {
                    context.assets.open("tessdata/$name").use { input ->
                        temporary.outputStream().use(input::copyTo)
                    }
                    require(temporary.length() >= MIN_TRAINEDDATA_BYTES) {
                        "Το αρχείο OCR είναι ελλιπές: $name"
                    }
                    if (destination.exists()) {
                        require(destination.delete()) {
                            "Δεν ήταν δυνατή η αντικατάσταση του αρχείου OCR: $name"
                        }
                    }
                    require(temporary.renameTo(destination)) {
                        "Δεν ήταν δυνατή η εγκατάσταση του αρχείου OCR: $name"
                    }
                } finally {
                    temporary.delete()
                }
            }
            require(destination.length() >= MIN_TRAINEDDATA_BYTES) {
                "Το αρχείο OCR είναι ελλιπές: $name"
            }
        }

        versionFile.writeText(MODEL_BUNDLE_VERSION)
        root
    }

    private companion object {
        val modelLock = Mutex()
        val REQUIRED_MODELS = setOf("ell.traineddata", "eng.traineddata")
        const val MODEL_BUNDLE_VERSION = "tessdata-fast-87416418657359cb"
        const val MIN_TRAINEDDATA_BYTES = 1_000_000L
    }
}
