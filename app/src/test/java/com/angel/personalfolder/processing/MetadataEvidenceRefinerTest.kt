package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataEvidenceRefinerTest {
    @Test
    fun explicitTwoDigitIssueDateIsNormalizedDeterministically() {
        val text = """
            ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ ΔΟΚΙΜΗΣ
            Από 01/04/26 - Έως 30/06/26 - Ημερ. Έκδοσης 30/06/26
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-statement.pdf")

        assertNull(raw.issuedDate)

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("2026-06-30", refined.issuedDate)
        assertEquals("high", refined.issuedConfidence)
        assertEquals("issued-explicit-short-date", refined.issuedProvenance)
    }

    @Test
    fun validityRangeSuppliesPermitExpiryWithoutChangingIssueDate() {
        val text = """
            ΔΟΚΙΜΑΣΤΙΚΗ ΑΠΟΦΑΣΗ
            Πόλη, 29/11/2021
            Χορηγείται άδεια ισχύος από 28/09/2021 έως 27/09/2026.
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-decision.pdf")

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("2021-11-29", refined.issuedDate)
        assertEquals("2026-09-27", refined.expiryDate)
        assertEquals("high", refined.expiryConfidence)
        assertEquals("expiry-validity-range", refined.expiryProvenance)
    }

    @Test
    fun applicationNeverOwnsTheExpiryOfAReferencedPermit() {
        val text = """
            ΑΙΤΗΣΗ - ΑΝΑΦΟΡΑ
            Υφιστάμενη άδεια διαμονής - Λήξη: 31/12/2030
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-application.pdf")

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertNull(refined.expiryDate)
        assertEquals("none", refined.expiryConfidence)
    }

    @Test
    fun unlabelledTwoDigitDateIsNotPromotedToIssueDate() {
        val text = "ΔΟΚΙΜΑΣΤΙΚΟ ΕΓΓΡΑΦΟ\n30/06/26"
        val raw = MetadataExtractor.extract(text, "synthetic.pdf")

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertNull(refined.issuedDate)
    }
}
