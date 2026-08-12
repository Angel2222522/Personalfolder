package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTypePolicyTest {
    @Test
    fun providerOctetStreamFallsBackToKnownExtension() {
        assertEquals("application/pdf", ImportTypePolicy.resolveMime("application/octet-stream", "document.pdf"))
        assertTrue(ImportTypePolicy.isSupported(ImportTypePolicy.resolveMime("", "photo.png")))
    }

    @Test
    fun unsupportedTypeIsRejectedBeforeDecoderWork() {
        assertFalse(ImportTypePolicy.isSupported(ImportTypePolicy.resolveMime("text/plain", "document.txt")))
        assertFalse(ImportTypePolicy.isSupported(ImportTypePolicy.resolveMime("", "document.zip")))
    }
}
