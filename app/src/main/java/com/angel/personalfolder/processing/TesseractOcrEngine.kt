package com.angel.personalfolder.processing

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class TesseractOcrEngine(private val context: Context) {
    private var modelsVerified = false

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
        if (!modelsVerified) {
            val assetNames = context.assets.list("tessdata").orEmpty().toSet()
            MODEL_SPECS.forEach { spec ->
                require(spec.name in assetNames) { "Λείπει το μοντέλο OCR: ${spec.name}" }
                val destination = File(tessdata, spec.name)
                if (!isValidModel(destination, spec)) {
                    installModel(spec, destination)
                }
                require(isValidModel(destination, spec)) {
                    "Το μοντέλο OCR είναι ελλιπές ή κατεστραμμένο: ${spec.name}"
                }
            }
            modelsVerified = true
        }
        root
    }

    private fun installModel(spec: ModelSpec, destination: File) {
        val temporary = File(destination.parentFile, ".${spec.name}.${System.nanoTime()}.part")
        try {
            context.assets.open("tessdata/${spec.name}").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            require(isValidModel(temporary, spec)) {
                "Το ενσωματωμένο μοντέλο OCR είναι ελλιπές ή κατεστραμμένο: ${spec.name}"
            }
            if (destination.exists()) require(destination.delete()) {
                "Δεν ήταν δυνατή η αντικατάσταση του παλιού μοντέλου OCR: ${spec.name}"
            }
            require(temporary.renameTo(destination)) {
                "Δεν ήταν δυνατή η εγκατάσταση του μοντέλου OCR: ${spec.name}"
            }
        } finally {
            temporary.delete()
        }
    }

    private fun isValidModel(file: File, spec: ModelSpec): Boolean {
        if (!file.isFile || file.length() != spec.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == spec.sha256
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val modelLock = Mutex()
        val MODEL_SPECS = listOf(
            ModelSpec(
                name = "ell.traineddata",
                size = 1_419_514L,
                sha256 = "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"
            ),
            ModelSpec(
                name = "eng.traineddata",
                size = 4_113_088L,
                sha256 = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
            )
        )
    }

    private data class ModelSpec(val name: String, val size: Long, val sha256: String)
}
