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
    fun scoresDateContextUsingExplicitKeywords() {
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
    fun doesNotInventCreationOrExpiryFromUnlabelledDate() {
        val result = MetadataExtractor.extract("Αριθμός αίτησης 12345\n03/08/2026", "Έγγραφο")
        assertEquals(null, result.issuedDate)
        assertEquals(null, result.expiryDate)
        assertEquals("none", result.issuedConfidence)
        assertEquals("none", result.expiryConfidence)
    }

    @Test
    fun doesNotInventExpiryFromASecondUnlabelledDate() {
        val result = MetadataExtractor.extract("Δημιουργία: 01/01/2024\nΑναφορά 02/02/2025", "Έγγραφο")
        assertEquals("2024-01-01", result.issuedDate)
        assertEquals(null, result.expiryDate)
        assertEquals("none", result.expiryConfidence)
        assertTrue(result.json.contains("\"expiryProvenance\":\"none\""))
    }

    @Test
    fun genericValidityWordsDoNotTurnCreationIntoExpiry() {
        val result = MetadataExtractor.extract(
            "Ημερομηνία δημιουργίας: 03/08/2026\nΗ παρούσα βεβαίωση ισχύει για κάθε νόμιμη χρήση.",
            "Έγγραφο"
        )
        assertEquals("2026-08-03", result.issuedDate)
        assertEquals(null, result.expiryDate)
        assertEquals("none", result.expiryConfidence)
    }

    @Test
    fun creationKeywordStillWinsWhenOtherKeywordsExistOnTheSameLine() {
        val result = MetadataExtractor.extract(
            "Αριθμός αίτησης 12345 | Κατηγορία βεβαίωση | Δημιουργία: 03/08/2026 | κατάσταση ενεργή",
            "Έγγραφο"
        )
        assertEquals("2026-08-03", result.issuedDate)
        assertEquals(null, result.expiryDate)
    }

    @Test
    fun expiryKeywordStillWinsWhenOtherKeywordsExistOnTheSameLine() {
        val result = MetadataExtractor.extract(
            "Κατηγορία άδεια | κατάσταση ενεργή | Λήξη: 05/09/2027 | υπενθύμιση",
            "Έγγραφο"
        )
        assertEquals(null, result.issuedDate)
        assertEquals("2027-09-05", result.expiryDate)
    }

    @Test
    fun mapsCreationAndExpirySeparatelyWhenBothShareOneBusyLine() {
        val result = MetadataExtractor.extract(
            "Κατηγορία άδεια | Δημιουργία: 03/08/2026 | φορέας δήμος | Λήξη: 05/09/2027 | ισχύει κανονικά",
            "Έγγραφο"
        )
        assertEquals("2026-08-03", result.issuedDate)
        assertEquals("2027-09-05", result.expiryDate)
        assertTrue(result.issuedConfidence == "high" || result.issuedConfidence == "medium")
        assertTrue(result.expiryConfidence == "high" || result.expiryConfidence == "medium")
    }

    @Test
    fun acceptsLabelOnPreviousLineForCreationAndExpiry() {
        val created = MetadataExtractor.extract("Ημερομηνία δημιουργίας\n03/08/2026", "Έγγραφο")
        val expires = MetadataExtractor.extract("Ημερομηνία λήξης\n05/09/2027", "Έγγραφο")
        assertEquals("2026-08-03", created.issuedDate)
        assertEquals("2027-09-05", expires.expiryDate)
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
    fun acceptsProtocolLabelVariantsOnlyAtTheStartOfTheirLine() {
        assertEquals("12345/2026", MetadataExtractor.extract("Αρ Πρωτ: 12345/2026", "Έγγραφο").protocolNumber)
        assertEquals("ab-7", MetadataExtractor.extract("Protocol No.: AB-7", "Έγγραφο").protocolNumber)
        assertEquals(null, MetadataExtractor.extract("Σχόλιο: Αριθμός πρωτοκόλλου: 12345", "Έγγραφο").protocolNumber)
        assertEquals(null, MetadataExtractor.extract("application number: 12345", "Έγγραφο").protocolNumber)
    }

    @Test
    fun carriesConfidenceAndProvenanceForProviderAndProtocol() {
        val result = MetadataExtractor.extract(
            "Ελληνική Δημοκρατία\nΥπουργείο Παιδείας\nΑρ. Πρωτ.: 12345/2026",
            "Έγγραφο"
        )
        assertEquals("Υπουργείο Παιδείας", result.provider)
        assertEquals("high", result.providerConfidence)
        assertEquals("12345/2026", result.protocolNumber)
        assertEquals("high", result.protocolConfidence)
        assertTrue(result.json.contains("\"providerProvenance\":\"issuer-marker:"))
        assertTrue(result.json.contains("\"protocolProvenance\":\"protocol-label\""))
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
