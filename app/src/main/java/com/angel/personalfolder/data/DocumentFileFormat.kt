package com.angel.personalfolder.data

import java.io.File
import java.io.FileInputStream

/** Identifies PDF sources even when a content provider reports a generic MIME type. */
object DocumentFileFormat {
    fun isSupported(file: File, mimeType: String, sourceName: String, fallbackMimeType: String): Boolean {
        if (isPdf(file, mimeType, sourceName, fallbackMimeType)) return true
        val declaredMime = mimeType.substringBefore(';').trim()
        val fallback = fallbackMimeType.substringBefore(';').trim()
        if (declaredMime.startsWith("image/", ignoreCase = true)) return true
        if (fallback.startsWith("image/", ignoreCase = true) && declaredMime.isBlank()) return true
        if (sourceName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS) return true
        return hasImageSignature(file)
    }

    fun isPdf(file: File, mimeType: String, sourceName: String, fallbackMimeType: String): Boolean {
        if (mimeType.substringBefore(';').trim().equals("application/pdf", ignoreCase = true)) return true
        if (sourceName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)) return true
        if (sourceName.isBlank() && fallbackMimeType.substringBefore(';').trim().equals("application/pdf", ignoreCase = true)) return true
        return hasPdfSignature(file)
    }

    private fun hasPdfSignature(file: File): Boolean {
        if (!file.isFile) return false
        val expected = "%PDF-".toByteArray(Charsets.US_ASCII)
        return runCatching {
            FileInputStream(file).use { input ->
                val actual = ByteArray(expected.size)
                var offset = 0
                while (offset < actual.size) {
                    val count = input.read(actual, offset, actual.size - offset)
                    if (count <= 0) return@use false
                    offset += count
                }
                actual.contentEquals(expected)
            }
        }.getOrDefault(false)
    }

    private fun hasImageSignature(file: File): Boolean {
        if (!file.isFile) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(12)
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count <= 0) break
                    offset += count
                }
                header.copyOf(offset).let { bytes ->
                    bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) ||
                        bytes.startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) ||
                        bytes.startsWith("GIF8".toByteArray(Charsets.US_ASCII)) ||
                        bytes.startsWith("BM".toByteArray(Charsets.US_ASCII)) ||
                        bytes.startsWith(byteArrayOf(0x49, 0x49, 0x2a, 0x00)) ||
                        bytes.startsWith(byteArrayOf(0x4d, 0x4d, 0x00, 0x2a)) ||
                        bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
                        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII))
                }
            }
        }.getOrDefault(false)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && copyOf(prefix.size).contentEquals(prefix)

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "tif", "tiff")
}
