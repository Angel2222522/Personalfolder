package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class OcrRecognition(
    val text: String,
    val metadataAssistText: String = ""
)

class TesseractOcrEngine(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap): String = recognizeDetailed(bitmap, includeMetadataAssist = false).text

    /**
     * The visible OCR stays on PSM_AUTO because it preserves paragraphs/tables
     * better. A sparse pass is optional and is used only as extra evidence for
     * metadata fields that the normal layout pass may miss. Sparse text is never
     * shown to the user or stored as the page OCR text.
     *
     * A Greek-only pass is also allowed to repair the Greek side of standard
     * bilingual identity fields. It is merged only into those known field lines;
     * the main ell+eng pass remains authoritative for the rest of the document.
     */
    suspend fun recognizeDetailed(bitmap: Bitmap, includeMetadataAssist: Boolean = true): OcrRecognition = withContext(Dispatchers.Default) {
        val dataPath = withContext(Dispatchers.IO) { prepareDataPath() }
        val primaryRaw = recognizePass(dataPath, "ell+eng", TessBaseAPI.PageSegMode.PSM_AUTO, bitmap)
        val primary = OcrTextPostProcessor.normalizeOcrText(primaryRaw)

        val greekAssist = if (OcrTextPostProcessor.needsGreekAdministrativeFieldAssist(primary)) {
            runCatching {
                OcrTextPostProcessor.normalizeOcrText(
                    recognizePass(dataPath, "ell", TessBaseAPI.PageSegMode.PSM_AUTO, bitmap)
                )
            }.getOrDefault("")
        } else {
            ""
        }
        val displayText = OcrTextPostProcessor.mergeGreekAdministrativeFields(primary, greekAssist)

        val metadataAssist = if (includeMetadataAssist) {
            runCatching {
                OcrTextPostProcessor.normalizeOcrText(
                    recognizePass(dataPath, "ell+eng", TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT, bitmap)
                )
            }.getOrDefault("")
        } else {
            ""
        }

        OcrRecognition(text = displayText, metadataAssistText = metadataAssist)
    }

    private fun recognizePass(dataPath: File, language: String, pageSegMode: Int, bitmap: Bitmap): String {
        val tess = TessBaseAPI()
        try {
            val configuration = mapOf(
                "preserve_interword_spaces" to "1",
                "user_defined_dpi" to OCR_DPI.toString()
            )
            check(
                tess.init(
                    dataPath.absolutePath,
                    language,
                    TessBaseAPI.OEM_LSTM_ONLY,
                    configuration
                )
            ) {
                "Δεν φορτώθηκαν τα δεδομένα OCR για: $language"
            }
            tess.setPageSegMode(pageSegMode)
            tess.setImage(bitmap)
            return tess.getUTF8Text().orEmpty()
        } finally {
            tess.recycle()
        }
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
        const val MODEL_BUNDLE_VERSION = "tessdata-best-e12c65a915945e4c"
        const val MIN_TRAINEDDATA_BYTES = 5_000_000L
        const val OCR_DPI = 300
    }
}
