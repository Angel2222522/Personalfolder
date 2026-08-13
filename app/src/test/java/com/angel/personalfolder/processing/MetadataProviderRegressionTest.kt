package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataProviderRegressionTest {
    @Test
    fun concreteNumberedSchoolBeatsGenericMinistryAndSentenceContext() {
        val result = MetadataExtractor.extract(
            """
                ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
                ΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ
                Βεβαιώνεται από το αρχείο του 12ου Δημοτικού Σχολείου Δείγματος, ο μαθητής.
            """.trimIndent(),
            "synthetic-school.pdf"
        )
        assertEquals("12ου Δημοτικού Σχολείου Δείγματος", result.provider)
    }

    @Test
    fun splitMigrationAuthorityDropsParallelMetadataField() {
        val result = MetadataExtractor.extract(
            """
                ΔΙΕΥΘΥΝΣΗ ΑΛΛΟΔΑΠΩΝ ΚΑΙ                    ΑΡ. ΑΔΕΙΑΣ: X999
                ΜΕΤΑΝΑΣΤΕΥΣΗΣ
                ΔΕΙΓΜΑΤΟΣ
                ΤΜΗΜΑ ΑΔΕΙΩΝ ΔΙΑΜΟΝΗΣ Α' ΚΑΙ Β'
            """.trimIndent(),
            "synthetic-decision.pdf"
        )
        assertEquals(
            "ΔΙΕΥΘΥΝΣΗ ΑΛΛΟΔΑΠΩΝ ΚΑΙ ΜΕΤΑΝΑΣΤΕΥΣΗΣ ΔΕΙΓΜΑΤΟΣ ΤΜΗΜΑ ΑΔΕΙΩΝ ΔΙΑΜΟΝΗΣ Α' ΚΑΙ Β'",
            result.provider
        )
    }

    @Test
    fun registryOfficeBeatsLaterHospitalContext() {
        val result = MetadataExtractor.extract(
            """
                ΛΗΞΙΑΡΧΕΙΟ ΔΕΙΓΜΑΤΟΣ
                Ληξιαρχική Πράξη Γέννησης
                Μέρος Γέννησης Νοσοκομείο ή Μαιευτήριο
            """.trimIndent(),
            "synthetic-birth-record.pdf"
        )
        assertEquals("ΛΗΞΙΑΡΧΕΙΟ ΔΕΙΓΜΑΤΟΣ", result.provider)
    }

    @Test
    fun healthUnitJoinsUppercaseContinuationAndDropsParallelIdentityField() {
        val result = MetadataExtractor.extract(
            """
                Ιατρική Βεβαίωση
                Μονάδα Υγείας: ΠΟΛΥΔΥΝΑΜΗ ΔΟΚΙΜΑΣΤΙΚΗ                    Αρ. Ταυτ. Στοιχείου: 0000000
                ΜΟΝΑΔΑ ΨΥΧΙΚΗΣ ΥΓΕΙΑΣ
                ΔΕΙΓΜΑΤΟΣ
            """.trimIndent(),
            "synthetic-medical.pdf"
        )
        assertEquals(
            "ΠΟΛΥΔΥΝΑΜΗ ΔΟΚΙΜΑΣΤΙΚΗ ΜΟΝΑΔΑ ΨΥΧΙΚΗΣ ΥΓΕΙΑΣ ΔΕΙΓΜΑΤΟΣ",
            result.provider
        )
    }

    @Test
    fun wrappedLegalEntityKeepsIssuerButDropsHeadOfficeSideField() {
        val result = MetadataExtractor.extract(
            """
                SAMPLE RETAIL SERVICES ΗΛΕΚΤΡΙΚΩΝ
                ΚΑΙ ΥΠΗΡΕΣΙΩΝ ΜΟΝΟΠΡΟΣΩΠΗ ΑΝΩΝΥΜΗ ΕΤΑΙΡΕΙΑ ΕΔΡΑ: SAMPLE ADDRESS
                ΣΤΟΙΧΕΙΑ ΠΑΡΑΣΤΑΤΙΚΟΥ
            """.trimIndent(),
            "synthetic-order.pdf"
        )
        assertEquals(
            "SAMPLE RETAIL SERVICES ΗΛΕΚΤΡΙΚΩΝ ΚΑΙ ΥΠΗΡΕΣΙΩΝ ΜΟΝΟΠΡΟΣΩΠΗ ΑΝΩΝΥΜΗ ΕΤΑΙΡΕΙΑ",
            result.provider
        )
    }

    @Test
    fun conciseBankBrandBeatsLaterNarrativeMentionAndAddressPhrase() {
        val result = MetadataExtractor.extract(
            """
                ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ PLUS
                Διαθέσιμα από κάρτες SampleBank για τον δικαιούχο
                Αλλαγή διεύθυνσης κατοικίας για υπηρεσίες SampleBank
            """.trimIndent(),
            "synthetic-statement.pdf"
        )
        assertEquals("SampleBank", result.provider)
    }
}
