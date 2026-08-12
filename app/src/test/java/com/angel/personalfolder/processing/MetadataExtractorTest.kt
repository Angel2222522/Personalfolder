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

    @Test
    fun doesNotUseRegistryLabelAsProtocolNumber() {
        val result = MetadataExtractor.extract(
            "ΑΡΙΘΜΟΣ ΜΗΤΡΩΟΥ: 12345\nΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ",
            "Έγγραφο"
        )
        assertEquals(null, result.protocolNumber)
    }

    @Test
    fun scoresDateContextUsingOriginalTextPositions() {
        val result = MetadataExtractor.extract(
            "Ημερομηνία έκδοσης: 01/02/2024\nΤο έγγραφο ισχύει έως: 15-03-2025",
            "Έγγραφο"
        )
        assertEquals("2024-02-01", result.issuedDate)
        assertEquals("2025-03-15", result.expiryDate)
        assertEquals("high", result.issuedConfidence)
        assertEquals("high", result.expiryConfidence)
    }

    @Test
    fun doesNotInventExpiryFromOneUnlabelledDate() {
        val result = MetadataExtractor.extract("Αριθμός αίτησης 12345\n03/08/2026", "Έγγραφο")
        assertEquals(null, result.expiryDate)
        assertEquals(null, result.issuedDate)
        assertEquals("none", result.issuedConfidence)
        assertTrue(result.json.contains("\"expiryConfidence\":\"none\""))
    }

    @Test
    fun doesNotTurnAnUnlabelledSecondDateIntoExpiry() {
        val result = MetadataExtractor.extract("Εκδόθηκε 01/01/2024\nΑναφορά 02/02/2025", "Έγγραφο")
        assertEquals(null, result.expiryDate)
        assertEquals("none", result.expiryConfidence)
    }

    @Test
    fun doesNotUseGreekRepublicHeaderAsProvider() {
        val result = MetadataExtractor.extract(
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ\nΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ ΚΑΙ ΘΡΗΣΚΕΥΜΑΤΩΝ",
            "Έγγραφο"
        )
        assertEquals("ΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ ΚΑΙ ΘΡΗΣΚΕΥΜΑΤΩΝ", result.provider)
    }
}
