package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataExtractorTest {
    @Test
    fun extractsGreekDocumentMetadata() {
        val result = MetadataExtractor.extract(
            "ΔΗΜΟΣ ΘΕΣΣΑΛΟΝΙΚΗΣ\nΑριθμός πρωτοκόλλου: 12345/2026\nΗμερομηνία έκδοσης: 03/08/2026\nΙσχύει έως: 03/08/2027",
            "Έγγραφο"
        )
        assertEquals("Δημόσιες υπηρεσίες", result.category)
        assertEquals("2027-08-03", result.expiryDate)
        assertEquals("2026-08-03", result.issuedDate)
        assertTrue(result.protocolNumber?.contains("12345") == true)
    }

    @Test
    fun keepsFallbackWhenTextIsEmpty() {
        val result = MetadataExtractor.extract("", "my-document")
        assertEquals("my-document", result.title)
        assertEquals("Άλλα", result.category)
    }

    @Test
    fun handlesAccentlessGreekAndEscapesJsonValues() {
        val result = MetadataExtractor.extract(
            "ΔΗΜΟΣ \"δοκιμή\"\nΑΡΙΘΜΟΣ ΠΡΩΤΟΚΟΛΛΟΥ: ΑΒ-123",
            "Έγγραφο"
        )
        assertEquals("Δημόσιες υπηρεσίες", result.category)
        assertEquals("αβ-123", result.protocolNumber)
        assertTrue(result.json.contains("\\\"δοκιμή\\\""))
    }
}
