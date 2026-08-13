package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataProtocolRegressionTest {
    @Test
    fun compactMixedScriptProtocolLabelIsAccepted() {
        val result = MetadataExtractor.extract(
            """
                ΒΕΒΑΙΩΣΗ
                Aριθ.Πρωτ.117
            """.trimIndent(),
            "synthetic-certificate.pdf"
        )
        assertEquals("117", result.protocolNumber)
    }

    @Test
    fun parallelColumnProtocolLabelIsAccepted() {
        val result = MetadataExtractor.extract(
            """
                ΔΙΟΙΚΗΤΙΚΗ ΑΡΧΗ                         ΑΡ.ΠΡΩΤ. : SYN-2026/42
                ΑΠΟΦΑΣΗ
            """.trimIndent(),
            "synthetic-decision.pdf"
        )
        assertEquals("SYN-2026/42", result.protocolNumber)
    }

    @Test
    fun fullNumberAbbreviationAtLineStartIsAccepted() {
        val result = MetadataExtractor.extract(
            """
                Αριθ.Πρωτ.: 2026
                ΠΙΣΤΟΠΟΙΗΤΙΚΟ
            """.trimIndent(),
            "synthetic-record.pdf"
        )
        assertEquals("2026", result.protocolNumber)
    }

    @Test
    fun narrativeRequestForFutureProtocolDoesNotInventAValue() {
        val result = MetadataExtractor.extract(
            """
                ΑΙΤΗΣΗ - ΑΝΑΦΟΡΑ
                Ζητώ να μου γνωστοποιηθεί νέος αριθμός πρωτοκόλλου όταν καταχωριστεί η αίτηση.
            """.trimIndent(),
            "synthetic-request.pdf"
        )
        assertNull(result.protocolNumber)
    }

    @Test
    fun legalCitationInsideSentenceDoesNotBecomeDocumentProtocol() {
        val result = MetadataExtractor.extract(
            """
                ΑΠΟΦΑΣΗ
                Λαμβάνεται υπόψη η αρ. πρωτ. 555/2020 προηγούμενη διοικητική πράξη.
            """.trimIndent(),
            "synthetic-legal-text.pdf"
        )
        assertNull(result.protocolNumber)
    }
}
