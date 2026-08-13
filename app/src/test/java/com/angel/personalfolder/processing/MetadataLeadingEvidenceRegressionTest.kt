package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataLeadingEvidenceRegressionTest {
    @Test
    fun lateSupportingAttachmentCannotReplaceTopLevelCategoryOrProtocol() {
        val topLevel = buildString {
            appendLine("ΑΙΤΗΣΗ ΓΙΑ ΑΔΕΙΑ ΔΙΑΜΟΝΗΣ")
            appendLine("ΠΡΟΣ ΔΙΕΥΘΥΝΣΗ ΑΛΛΟΔΑΠΩΝ ΚΑΙ ΜΕΤΑΝΑΣΤΕΥΣΗΣ")
            // Keep the synthetic attachment genuinely outside the extractor's
            // bounded leading-evidence window. The repetition is intentionally
            // larger than any metadata window used by this regression.
            repeat(420) { appendLine("Αίτημα σχετικό με άδεια διαμονής και διοικητική εξέταση.") }
        }
        val lateAttachment = buildString {
            repeat(400) { appendLine("Μισθωτήριο κατοικίας ενοίκιο μίσθωση μισθωτής.") }
            appendLine("Αρ. Πρωτ.: 999999/2099")
        }

        val result = MetadataExtractor.extract(topLevel + lateAttachment, "synthetic-bundle.pdf")

        assertEquals("Μετανάστευση / άδειες", result.category)
        assertEquals(null, result.protocolNumber)
    }
}
