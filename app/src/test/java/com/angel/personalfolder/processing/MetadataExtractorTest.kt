package com.angel.personalfolder.processing

import java.text.Normalizer
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataExtractorTest {
    @Test
    fun extractsExplicitGreekExpiryIssueAndProtocol() {
        val result = MetadataExtractor.extract(
            "ΔΗΜΟΣ ΘΕΣΣΑΛΟΝΙΚΗΣ\nΑριθμός πρωτοκόλλου: 12345/2026\nΗμερομηνία έκδοσης: 03/08/2026\nΙσχύει έως: 03/08/2027",
            "Έγγραφο"
        )
        assertEquals("Δημόσιες υπηρεσίες", result.category)
        assertEquals("2027-08-03", result.expiryDate)
        assertEquals("2026-08-03", result.issuedDate)
        assertEquals("high", result.expiryConfidence)
        assertEquals("12345 /2026", result.protocolNumber)
    }

    @Test
    fun keepsFallbackWhenTextIsEmpty() {
        val result = MetadataExtractor.extract("", "my-document")
        assertEquals("my-document", result.title)
        assertEquals("Άλλα", result.category)
        assertNull(result.issuedDate)
        assertNull(result.expiryDate)
        assertNull(result.protocolNumber)
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
    fun manyUnrelatedDatesDoNotCreateExpiryOrIssueDate() {
        val result = MetadataExtractor.extract(
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ\n" +
                "ΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ ΘΡΗΣΚΕΥΜΑΤΩΝ ΚΑΙ\nΑΘΛΗΤΙΣΜΟΥ\n" +
                "Θεσσαλονίκη: 11-08-2026\n" +
                "Σχολικό έτος 2012-2013\n" +
                "Προαγωγή: 07/10-09-2013\n" +
                "Απόρριψη λόγω φοίτησης: 22-05-2014",
            "Έγγραφο"
        )
        assertNull(result.expiryDate)
        assertNull(result.issuedDate)
        assertEquals("none", result.expiryConfidence)
        assertEquals("none", result.issuedConfidence)
    }

    @Test
    fun rejectionAndDecisionDatesAreNotExpiryOrIssueDates() {
        val result = MetadataExtractor.extract(
            "Ημερομηνία απόρριψης: 04/06/2024\nΗμερομηνία απόφασης: 05/06/2024\nΣχολικό έτος 2023-2024",
            "Έγγραφο"
        )
        assertNull(result.expiryDate)
        assertNull(result.issuedDate)
    }

    @Test
    fun explicitUntilVariantsProduceExpiry() {
        listOf(
            "Ισχύει έως: 03/08/2027",
            "Ισχύει μέχρι 03/08/2027",
            "Ημερομηνία λήξης: 03/08/2027",
            "Valid until: 2027-08-03"
        ).forEach { text ->
            assertEquals("2027-08-03", MetadataExtractor.extract(text, "Έγγραφο").expiryDate)
        }
    }

    @Test
    fun providerPrefersSpecificMinistryAndJoinsWrappedHeaderLines() {
        val result = MetadataExtractor.extract(
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ\n" +
                "ΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ ΘΡΗΣΚΕΥΜΑΤΩΝ ΚΑΙ\nΑΘΛΗΤΙΣΜΟΥ\n" +
                "ΠΕΡΙΦΕΡΕΙΑΚΗ ΔΙΕΥΘΥΝΣΗ ΕΚΠΑΙΔΕΥΣΗΣ",
            "Έγγραφο"
        )
        val foldedProvider = foldForAssertion(result.provider)
        assertTrue(foldedProvider.contains(foldForAssertion("Υπουργείο Παιδείας")))
        assertTrue(foldedProvider.contains(foldForAssertion("Αθλητισμού")))
        assertTrue(!result.provider.equals("Ελληνική Δημοκρατία", ignoreCase = true))
    }

    @Test
    fun protocolRequiresProtocolLabelAndNumericPart() {
        val invalid = MetadataExtractor.extract("Αριθμός Μητρώου 4073", "Έγγραφο")
        assertNull(invalid.protocolNumber)

        val noNumericPart = MetadataExtractor.extract("Αριθμ. Πρωτ: μητρώου", "Έγγραφο")
        assertNull(noNumericPart.protocolNumber)
    }

    @Test
    fun protocolRecognizesGreekMuAndMicroSignVariants() {
        listOf("Αριθµ. Πρωτ: 684 /Φ.21", "Αριθμ. Πρωτ: 684 /Φ.21").forEach { label ->
            val result = MetadataExtractor.extract("$label\nΑριθμός Μητρώου 4073", "Έγγραφο")
            assertEquals("684 /φ.21", result.protocolNumber)
            assertTrue(!result.protocolNumber.orEmpty().contains("μητρώου"))
        }
    }

    @Test
    fun protocolSupportsFullAndAbbreviatedLabels() {
        listOf(
            "Αριθμός Πρωτοκόλλου: ΑΒ-123",
            "Αριθµ. Πρωτ: ΑΒ-123",
            "Αριθμ. Πρωτ: ΑΒ-123",
            "Αρ. Πρωτ.: ΑΒ-123"
        ).forEach { label ->
            assertEquals("αβ-123", MetadataExtractor.extract(label, "Έγγραφο").protocolNumber)
        }
    }

    @Test
    fun compositeDateIsNotCutIntoAStandaloneDate() {
        val result = MetadataExtractor.extract("Προαγωγή: 07/10-09-2013", "Έγγραφο")
        assertNull(result.issuedDate)
        assertNull(result.expiryDate)
    }

    private fun foldForAssertion(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
}
