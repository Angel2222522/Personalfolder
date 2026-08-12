package com.angel.personalfolder.processing

import com.angel.personalfolder.data.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataMergeTest {
    @Test
    fun unsafeOrLowConfidenceExpiryDoesNotEnterDocumentEntity() {
        val document = document(expiryDate = "2014-05-22")
        val extracted = extracted(expiryDate = null, expiryConfidence = "none")

        val persisted = document.copy(expiryDate = MetadataMerge.expiryForOcr(document, extracted))

        assertNull(persisted.expiryDate)
    }

    @Test
    fun explicitExpiryIsTheSameValueSelectedForPersistence() {
        val document = document()
        val extracted = extracted(expiryDate = "2027-08-03", expiryConfidence = "high")

        val persisted = document.copy(expiryDate = MetadataMerge.expiryForOcr(document, extracted))

        assertEquals("2027-08-03", persisted.expiryDate)
    }

    @Test
    fun manualExpirySurvivesAReprocessingPass() {
        val document = document(expiryDate = "2030-01-01", expiryDateManuallyEdited = true)
        val extracted = extracted(expiryDate = null, expiryConfidence = "none")

        assertEquals(document.expiryDate, MetadataMerge.expiryForOcr(document, extracted))
    }

    @Test
    fun legacyManualCorrectionIsDetectedFromPreviousSuggestion() {
        val document = document(
            expiryDate = "2030-01-01",
            metadataManuallyEdited = true,
            extractedMetadataJson = "{\"expiryDate\":\"2029-01-01\"}"
        )

        assertEquals(document.expiryDate, MetadataMerge.expiryForOcr(document, extracted()))
    }

    @Test
    fun legacyUnchangedAutomaticExpiryIsCleared() {
        val document = document(
            expiryDate = "2029-01-01",
            metadataManuallyEdited = true,
            extractedMetadataJson = "{\"expiryDate\":\"2029-01-01\"}"
        )

        assertNull(MetadataMerge.expiryForOcr(document, extracted()))
    }

    private fun extracted(expiryDate: String? = null, expiryConfidence: String = "none") = ExtractedMetadata(
        title = "Έγγραφο",
        category = "Άλλα",
        provider = "",
        issuedDate = null,
        expiryDate = expiryDate,
        protocolNumber = null,
        keywords = emptyList(),
        issuedConfidence = "none",
        expiryConfidence = expiryConfidence,
        json = "{}"
    )

    private fun document(
        expiryDate: String? = null,
        expiryDateManuallyEdited: Boolean = false,
        metadataManuallyEdited: Boolean = false,
        extractedMetadataJson: String = "{}"
    ) = DocumentEntity(
        id = "doc",
        title = "Έγγραφο",
        originalFileName = "document.pdf",
        mimeType = "application/pdf",
        encryptedPath = "/private/doc/page_0.pf",
        pageCount = 1,
        expiryDate = expiryDate,
        extractedMetadataJson = extractedMetadataJson,
        metadataManuallyEdited = metadataManuallyEdited,
        expiryDateManuallyEdited = expiryDateManuallyEdited,
        createdAt = 1L,
        updatedAt = 1L
    )
}
