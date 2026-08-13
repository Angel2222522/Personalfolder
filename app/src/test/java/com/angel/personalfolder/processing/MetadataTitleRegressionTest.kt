package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataTitleRegressionTest {
    @Test
    fun extractsDocumentTypeValueInsteadOfTheLabel() {
        val result = MetadataExtractor.extract(
            """
                SYNTHETIC RETAIL COMPANY
                ΣΤΟΙΧΕΙΑ ΠΑΡΑΣΤΑΤΙΚΟΥ
                ΕΙΔΟΣ ΠΑΡΑΣΤΑΤΙΚΟΥ: Δελτίο Παραγγελίας
                ΑΡΙΘΜΟΣ: 555000
            """.trimIndent(),
            "synthetic-order.pdf"
        )
        assertEquals("Δελτίο Παραγγελίας", result.title)
    }

    @Test
    fun birthRecordHeadingBeatsGenericMetadataLines() {
        val result = MetadataExtractor.extract(
            """
                ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
                Κωδικός εγγράφου: SYNTHETIC-CODE
                Ληξιαρχική Πράξη Γέννησης
                ΣΤΟΙΧΕΙΑ ΠΡΑΞΗΣ
            """.trimIndent(),
            "synthetic-record.pdf"
        )
        assertEquals("Ληξιαρχική Πράξη Γέννησης", result.title)
    }

    @Test
    fun exactDecisionHeadingBeatsLicenseNumberField() {
        val result = MetadataExtractor.extract(
            """
                ΑΡ. ΑΔΕΙΑΣ: SYNTHETIC-777
                ΘΕΜΑ: Χορήγηση άδειας διαμονής
                ΑΠΟΦΑΣΗ
                Έχοντας υπόψη τα ακόλουθα
            """.trimIndent(),
            "synthetic-decision.pdf"
        )
        assertEquals("ΑΠΟΦΑΣΗ", result.title)
    }

    @Test
    fun accountProductHeadingBeatsLeadingHolderLikeText() {
        val result = MetadataExtractor.extract(
            """
                SAMPLE PERSON
                SAMPLE STREET 10
                ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ PLUS
                IBAN: GR0000000000000000000000000
            """.trimIndent(),
            "synthetic-statement.pdf"
        )
        assertEquals("ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ PLUS", result.title)
    }

    @Test
    fun pipeSeparatedHeaderKeepsOnlyTheDocumentHeading() {
        val result = MetadataExtractor.extract(
            "ΑΙΤΗΣΗ - ΑΝΑΦΟΡΑ | ΑΔΕΙΑ Μ.2 | SAMPLE PERSON\nΠΡΟΣ ΑΡΜΟΔΙΑ ΥΠΗΡΕΣΙΑ",
            "synthetic-application.pdf"
        )
        assertEquals("ΑΙΤΗΣΗ - ΑΝΑΦΟΡΑ", result.title)
    }
}
