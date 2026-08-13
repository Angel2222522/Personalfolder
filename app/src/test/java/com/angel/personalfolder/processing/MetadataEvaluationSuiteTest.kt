package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ten fully synthetic document shapes used as a permanent regression gate.
 * They mirror structural challenges from private evaluation without retaining
 * any real document, person, identifier, address, date or OCR body.
 */
class MetadataEvaluationSuiteTest {
    @Test
    fun exam01SchoolStudyCertificate() {
        val result = process(
            """
                ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
                ΥΠΟΥΡΓΕΙΟ ΠΑΙΔΕΙΑΣ
                12ο ΓΥΜΝΑΣΙΟ ΔΟΚΙΜΗΣ
                Ημερομηνία: 14/08/2026
                Αριθμ. Πρωτ: 321 /Φ.7
                ΒΕΒΑΙΩΣΗ ΣΠΟΥΔΩΝ
                Βεβαιώνεται ότι ο μαθητής φοίτησε στη σχολική μονάδα.
            """.trimIndent(),
            "synthetic-school-study.pdf"
        )

        assertEquals("ΒΕΒΑΙΩΣΗ ΣΠΟΥΔΩΝ", result.title)
        assertEquals("12ο ΓΥΜΝΑΣΙΟ ΔΟΚΙΜΗΣ", result.provider)
        assertEquals("2026-08-14", result.issuedDate)
        assertEquals("321/Φ.7", result.protocolNumber)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam02SchoolAttendanceCertificate() {
        val result = process(
            """
                8ο ΔΗΜΟΤΙΚΟ ΣΧΟΛΕΙΟ ΔΟΚΙΜΗΣ
                Αριθ. Πρωτ. 117
                Ημερομηνία: 09/03/2026
                ΒΕΒΑΙΩΣΗ ΦΟΙΤΗΣΗΣ ΑΛΛΟΔΑΠΟΥ ΜΑΘΗΤΗ ΣΕ ΕΛΛΗΝΙΚΟ ΣΧΟΛΕΙΟ ΣΤΗΝ ΕΛΛΑΔΑ
                Η παρούσα βεβαίωση χορηγείται για διοικητική χρήση.
            """.trimIndent(),
            "synthetic-school-attendance.pdf"
        )

        assertTrue(result.title.startsWith("ΒΕΒΑΙΩΣΗ ΦΟΙΤΗΣΗΣ"))
        assertEquals("8ο ΔΗΜΟΤΙΚΟ ΣΧΟΛΕΙΟ ΔΟΚΙΜΗΣ", result.provider)
        assertEquals("2026-03-09", result.issuedDate)
        assertEquals("117", result.protocolNumber)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam03MedicalCertificateWithoutExpiry() {
        val result = process(
            """
                Ιατρική Βεβαίωση
                Ημ/νία Έκδοσης : 05/08/2026
                Ημ/νία λήξης :
                Μονάδα Υγείας: ΠΟΛΥΔΥΝΑΜΗ ΝΟΣΗΛΕΥΤΙΚΗ
                ΜΟΝΑΔΑ ΨΥΧΙΚΗΣ ΥΓΕΙΑΣ ΔΟΚΙΜΗΣ
                Συμπληρωματική ιατρική εκτίμηση.
            """.trimIndent(),
            "synthetic-medical.pdf"
        )

        assertEquals("Ιατρική Βεβαίωση", result.title)
        assertEquals("Υγεία", result.category)
        assertEquals("2026-08-05", result.issuedDate)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam04RetailOrderKeepsDocumentDateAndNoFakeProtocol() {
        val result = process(
            """
                DEMO RETAIL SERVICES ΜΟΝΟΠΡΟΣΩΠΗ ΑΝΩΝΥΜΗ ΕΤΑΙΡΕΙΑ
                ΣΤΟΙΧΕΙΑ ΠΑΡΑΣΤΑΤΙΚΟΥ
                ΕΙΔΟΣ ΠΑΡΑΣΤΑΤΙΚΟΥ: Δελτιο Παραγγελίας
                ΑΡΙΘΜΟΣ: 555000111222
                ΗΜΕΡΟΜΗΝΙΑ: 06-08-2026
                ΣΚΟΠΟΣ ΔΙΑΚΙΝΗΣΗΣ: Πώληση
            """.trimIndent(),
            "synthetic-order.pdf"
        )

        assertTrue(result.title.contains("Παραγγελ"))
        assertEquals("2026-08-06", result.issuedDate)
        assertNull(result.protocolNumber)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam05FlattenedRegistryHeaderGetsSpecificOffice() {
        val result = process(
            """
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
                Δοκιμή, 04/08/2026
                Αριθ.Πρωτ.: 2026
            """.trimIndent(),
            "synthetic-registry.pdf"
        )

        assertEquals("Ληξιαρχική Πράξη Γέννησης", result.title)
        assertEquals("ΛΗΞΙΑΡΧΕΙΟ ΔΟΚΙΜΑΣΤΙΚΟΥ", result.provider)
        assertEquals("2026-08-04", result.issuedDate)
        assertEquals("2026", result.protocolNumber)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam06ApplicationDoesNotInheritReferencedPermitExpiry() {
        val result = process(
            """
                ΑΙΤΗΣΗ - ΑΝΑΦΟΡΑ
                Αίτηση εξατομικευμένης εξέτασης διοικητικού ζητήματος
                Υφιστάμενη άδεια: λήξη 31/12/2030
                Δοκιμή, 30/07/2026
            """.trimIndent(),
            "synthetic-application.pdf"
        )

        assertEquals("ΑΙΤΗΣΗ - ΑΝΑΦΟΡΑ", result.title)
        assertEquals("2026-07-30", result.issuedDate)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam07PermitDecisionRecoversStackedProtocolAndValidityEnd() {
        val result = process(
            """
                ΑΠΟΚΕΝΤΡΩΜΕΝΗ ΔΙΟΙΚΗΣΗ ΔΟΚΙΜΗΣ
                ΔΙΕΥΘΥΝΣΗ ΑΛΛΟΔΑΠΩΝ ΚΑΙ ΜΕΤΑΝΑΣΤΕΥΣΗΣ
                ΔΟΚΙΜΗΣ
                ΑΡ.ΠΡΩΤ. :
                ΑΡ.ΦΑΚΕΛΟΥ :
                Ε.Κ.Α. :
                ΑΡ. ΑΔΕΙΑΣ :
                29/11/2024
                2024/77777
                123456
                X000001
                ΘΕΜΑ : Χορήγηση άδειας διαμονής
                ΑΠΟΦΑΣΗ
                Χορηγείται άδεια ισχύος από 01/01/2025 έως 31/12/2029.
            """.trimIndent(),
            "synthetic-permit-decision.pdf"
        )

        assertEquals("ΑΠΟΦΑΣΗ", result.title)
        assertEquals("2024-11-29", result.issuedDate)
        assertEquals("2029-12-31", result.expiryDate)
        assertEquals("2024/77777", result.protocolNumber)
    }

    @Test
    fun exam08BankStatementUsesIssueDateAndFinancialCategory() {
        val result = process(
            """
                SampleBank
                ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ PLUS
                IBAN: GR0000000000000000000000000
                BIC: SAMPLEXXX
                Από 01/04/26 - Έως 30/06/26 - Ημερ. Έκδοσης 30/06/26
            """.trimIndent(),
            "synthetic-bank-statement.pdf"
        )

        assertEquals("ΚΑΘΗΜΕΡΙΝΟΣ ΛΟΓΑΡΙΑΣΜΟΣ PLUS", result.title)
        assertEquals("Οικονομικά", result.category)
        assertEquals("2026-06-30", result.issuedDate)
        assertNull(result.expiryDate)
        assertNull(result.protocolNumber)
    }

    @Test
    fun exam09UtilityBillStaysUtilityCategory() {
        val result = process(
            """
                ΔΕΗ
                ΛΟΓΑΡΙΑΣΜΟΣ ΡΕΥΜΑΤΟΣ
                Περίοδος κατανάλωσης
                Κωδικός πληρωμής: TEST-000
            """.trimIndent(),
            "synthetic-utility.pdf"
        )

        assertEquals("Λογαριασμοί", result.category)
        assertNull(result.expiryDate)
    }

    @Test
    fun exam10UnlabelledNumbersAndDatesDoNotBecomeFacts() {
        val result = process(
            """
                ΓΕΝΙΚΟ ΕΝΗΜΕΡΩΤΙΚΟ ΕΓΓΡΑΦΟ
                Αναφορά 12/03/2024 και 18/04/2025 στο ιστορικό κείμενο.
                Κωδικός προϊόντος 123456789.
            """.trimIndent(),
            "synthetic-generic.pdf"
        )

        assertNull(result.expiryDate)
        assertNull(result.protocolNumber)
    }

    private fun process(text: String, fallbackTitle: String): ExtractedMetadata =
        MetadataEvidenceRefiner.refine(
            MetadataExtractor.extract(text, fallbackTitle),
            text
        )
}
