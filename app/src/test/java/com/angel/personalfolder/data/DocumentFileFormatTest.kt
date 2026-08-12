package com.angel.personalfolder.data

import java.io.File
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFileFormatTest {
    @Test
    fun detectsPdfBySignatureWhenProviderMimeIsGeneric() {
        val file = temporaryFile("%PDF-1.7\n")
        try {
            assertTrue(DocumentFileFormat.isPdf(file, "application/octet-stream", "source.bin", "application/octet-stream"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun doesNotTreatAnImageAsPdfWithoutMetadataOrSignature() {
        val file = temporaryFile("not a pdf")
        try {
            assertFalse(DocumentFileFormat.isPdf(file, "image/png", "source.png", "image/png"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun acceptsGenericMimeImageBySignature() {
        val file = temporaryFile(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        try {
            assertTrue(DocumentFileFormat.isSupported(file, "application/octet-stream", "source.bin", "application/octet-stream"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsGenericMimeUnknownBytes() {
        val file = temporaryFile("not an image or pdf")
        try {
            assertFalse(DocumentFileFormat.isSupported(file, "application/octet-stream", "source.bin", "application/octet-stream"))
        } finally {
            file.delete()
        }
    }

    private fun temporaryFile(content: String): File = File.createTempFile("document-format-${UUID.randomUUID()}", ".tmp").apply {
        writeText(content)
    }

    private fun temporaryFile(content: ByteArray): File = File.createTempFile("document-format-${UUID.randomUUID()}", ".tmp").apply {
        writeBytes(content)
    }
}
