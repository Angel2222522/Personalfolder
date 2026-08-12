package com.angel.personalfolder.processing

import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.MetadataConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataApplicationPolicyTest {
    private val baseDocument = DocumentEntity(
        id = "id",
        title = "fallback",
        originalFileName = "file.png",
        mimeType = "image/png",
        encryptedPath = "/private/doc.pf",
        pageCount = 1,
        createdAt = 1,
        updatedAt = 1
    )

    @Test
    fun unlabelledDatesDoNotCreateExpiryOrSuggestion() {
        val metadata = MetadataExtractor.extract("Εκδόθηκε 01/01/2024\nΑναφορά 02/02/2025", "fallback")

        val updated = MetadataApplicationPolicy.apply(baseDocument, metadata)

        assertNull(updated.expiryDate)
        assertNull(updated.expiryDateSuggestion)
        assertEquals(MetadataConfidence.NONE, updated.expiryDateSuggestionConfidence)
    }

    @Test
    fun extractedTitleIsAppliedWhenTitleIsNotManuallyOwned() {
        val metadata = MetadataExtractor.extract("Βεβαίωση κατοικίας", "fallback")

        assertEquals("Βεβαίωση κατοικίας", MetadataApplicationPolicy.apply(baseDocument, metadata).title)
    }

    @Test
    fun manuallyOwnedFieldSurvivesNewOcrCandidate() {
        val document = baseDocument.copy(
            provider = "Χειροκίνητος φορέας",
            providerManuallyEdited = true,
            providerConfidence = MetadataConfidence.MANUAL
        )
        val metadata = MetadataExtractor.extract("Υπουργείο Παιδείας", "fallback")

        val updated = MetadataApplicationPolicy.apply(document, metadata)

        assertEquals("Χειροκίνητος φορέας", updated.provider)
        assertEquals(MetadataConfidence.MANUAL, updated.providerConfidence)
    }
}
