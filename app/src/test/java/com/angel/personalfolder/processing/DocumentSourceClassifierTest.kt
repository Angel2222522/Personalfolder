package com.angel.personalfolder.processing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSourceClassifierTest {
    @Test
    fun recognizesPdfByMimeType() {
        assertTrue(DocumentSourceClassifier.isPdf("application/pdf", "scan.bin"))
    }

    @Test
    fun recognizesPdfByFileNameWhenMimeTypeIsMissing() {
        assertTrue(DocumentSourceClassifier.isPdf("", "scan.PDF"))
    }

    @Test
    fun usesDocumentMimeTypeOnlyWhenSourceNameIsUnavailable() {
        assertTrue(DocumentSourceClassifier.isPdf("", "", "application/pdf"))
    }

    @Test
    fun doesNotClassifyImageAsPdf() {
        assertFalse(DocumentSourceClassifier.isPdf("image/jpeg", "scan.jpg"))
    }
}
