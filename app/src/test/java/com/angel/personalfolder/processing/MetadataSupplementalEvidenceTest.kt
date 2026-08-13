package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSupplementalEvidenceTest {
    @Test
    fun sparseEvidenceCanSupplyDatelineAndProtocolMissingFromPrimaryLayout() {
        val primary = """
            ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
            ΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ
            Ταχ. Κώδικας: 00000 Αριθμ. Πρωτ: 321 /Φ.7
            ΒΕΒΑΙΩΣΗ ΣΠΟΥΔΩΝ
            Φοίτησε στο 12ο Γυμνάσιο Δοκιμής με Αριθμό Μητρώου 9999
        """.trimIndent()
        val sparse = """
            Θεσσαλονίκη: 14-08-2026
            Αριθμ. Πρωτ: 321 /Φ.7
        """.trimIndent()

        val result = MetadataExtractor.extract(primary, "scan.pdf", sparse)

        assertEquals("ΒΕΒΑΙΩΣΗ ΣΠΟΥΔΩΝ", result.title)
        assertEquals("12ο Γυμνάσιο Δοκιμής", result.provider)
        assertEquals("2026-08-14", result.issuedDate)
        assertEquals("321/Φ.7", result.protocolNumber)
        assertEquals(null, result.expiryDate)
    }

    @Test
    fun supplementalEvidenceDoesNotInflatePrimaryCategoryScoring() {
        val result = MetadataExtractor.extract(
            "Σύμβαση εργασίας\nΕργοδότης",
            "document.pdf",
            "ΔΗΜΟΣ\nΔΗΜΟΣ\nΔΗΜΟΣ\nβεβαίωση\nβεβαίωση"
        )
        assertEquals("Εργασία", result.category)
        assertTrue(result.categoryConfidence == "high" || result.categoryConfidence == "medium")
    }
}
