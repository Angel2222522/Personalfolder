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
        assertEquals("low", result.issuedConfidence)
        assertTrue(result.json.contains("\"expiryConfidence\":\"none\""))
    }

    @Test
    fun marksLastDateFallbackAsLowConfidence() {
        val result = MetadataExtractor.extract("Εκδόθηκε 01/01/2024\nΑναφορά 02/02/2025", "Έγγραφο")
        assertEquals("2025-02-02", result.expiryDate)
        assertEquals("low", result.expiryConfidence)
        assertTrue(result.json.contains("fallback:last-date"))
    }

    @Test
    fun acceptsOnlyProtocolLabelsAndKeepsTheWholeValue() {
        val validLabels = listOf(
            "Αριθμός μητρώου: 12345",
            "Αρ. Πρωτ.: 12345/2026",
            "Αρ. Πρωτοκόλλου: ΑΒ-123/7",
            "Αριθμός πρωτοκόλλου: 991"
        )
        assertEquals(null, MetadataExtractor.extract(validLabels.first(), "Έγγραφο").protocolNumber)
        assertEquals("12345/2026", MetadataExtractor.extract(validLabels[1], "Έγγραφο").protocolNumber)
        assertEquals("αβ-123/7", MetadataExtractor.extract(validLabels[2], "Έγγραφο").protocolNumber)
        assertEquals("991", MetadataExtractor.extract(validLabels[3], "Έγγραφο").protocolNumber)
    }

    @Test
    fun prefersSpecificIssuingAuthorityOverGenericStateHeading() {
        val result = MetadataExtractor.extract(
            "Ελληνική Δημοκρατία\nΥπουργείο Παιδείας\nΔιεύθυνση Διοικητικού",
            "Έγγραφο"
        )
        assertEquals("Υπουργείο Παιδείας", result.provider)
        assertTrue(result.providerConfidence == "high" || result.providerConfidence == "medium")
    }

    @Test
    fun usesUiCategoriesAndSpecificEmploymentRule() {
        val result = MetadataExtractor.extract("Σύμβαση εργασίας\nΕργοδότης", "Έγγραφο")
        assertEquals("Εργασία", result.category)
        assertTrue(result.categoryConfidence == "high" || result.categoryConfidence == "medium")
    }
}
