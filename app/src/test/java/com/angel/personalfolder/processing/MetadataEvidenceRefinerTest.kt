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

    @Test
    fun stackedProtocolValueAfterParallelFieldLabelsIsRecovered() {
        val text = """
            ΔΟΚΙΜΑΣΤΙΚΗ ΥΠΗΡΕΣΙΑ
            ΠΟΛΗ,
            ΑΡ.ΠΡΩΤ. :
            ΑΡ.ΦΑΚΕΛΟΥ :
            Ε.Κ.Α. :
            ΑΡ. ΑΔΕΙΑΣ :
            29/11/2021
            2021/53071
            360696
            X999999
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-decision.pdf")

        assertNull(raw.protocolNumber)

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("2021/53071", refined.protocolNumber)
        assertEquals("medium", refined.protocolConfidence)
        assertEquals("protocol-stacked-field", refined.protocolProvenance)
    }

    @Test
    fun genericRegistryIssuerUsesSpecificUppercaseOfficeQualifierFromFlattenedHeader() {
        val text = """
            ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
            ΝΟΜΟΣ
            ΔΗΜΟΣ
            ΛΗΞΙΑΡΧΕΙΟ
            Δ/ΝΣΗ
            Τηλέφωνο
            Ληξιαρχική Πράξη Γέννησης
            Περιφέρειας
            Δείγματος
            ΔΟΚΙΜΑΣΤΙΚΟΥ
            Οδός Δοκιμής 1
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-registry.pdf")

        assertEquals("ΛΗΞΙΑΡΧΕΙΟ", raw.provider)

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("ΛΗΞΙΑΡΧΕΙΟ ΔΟΚΙΜΑΣΤΙΚΟΥ", refined.provider)
        assertEquals("high", refined.providerConfidence)
        assertEquals("registry-office-layout", refined.providerProvenance)
    }

    @Test
    fun alreadySpecificRegistryIssuerIsNotExpandedAgain() {
        val text = """
            ΛΗΞΙΑΡΧΕΙΟ ΔΟΚΙΜΗΣ
            Ληξιαρχική Πράξη Γέννησης
            ΑΛΛΗ ΠΕΡΙΟΧΗ
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-registry.pdf")

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("ΛΗΞΙΑΡΧΕΙΟ ΔΟΚΙΜΗΣ", refined.provider)
    }

    @Test
    fun bankAccountStatementIsFinancialRatherThanUtilityAccount() {
        val text = """
            SampleBank
            ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ PLUS
            IBAN: GR0000000000000000000000000
            BIC: SAMPLEXXX
            Previous Balance 100.00
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-bank-statement.pdf")

        assertEquals("Λογαριασμοί", raw.category)

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("Οικονομικά", refined.category)
        assertEquals("high", refined.categoryConfidence)
    }

    @Test
    fun utilityAccountIsNotPromotedWithoutBankIssuer() {
        val text = """
            ΔΕΗ
            ΛΟΓΑΡΙΑΣΜΟΣ ΡΕΥΜΑΤΟΣ
            Κωδικός πληρωμής: TEST-000
        """.trimIndent()
        val raw = MetadataExtractor.extract(text, "synthetic-utility.pdf")

        val refined = MetadataEvidenceRefiner.refine(raw, text)

        assertEquals("Λογαριασμοί", refined.category)
    }
}
