package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataPermitCategoryTest {
    @Test
    fun classifiesGreekResidencePermitVariants() {
        val text = """
            ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
            ΥΠΟΥΡΓΕΙΟ ΜΕΤΑΝΑΣΤΕΥΣΗΣ ΚΑΙ ΑΣΥΛΟΥ
            ΤΙΤΛΟΣ ΔΙΑΜΟΝΗΣ
            ΔΕΥΤΕΡΗΣ ΓΕΝΙΑΣ
        """.trimIndent()

        assertEquals(
            "Μετανάστευση / άδειες",
            MetadataExtractor.extract(text, "Έγγραφο").category
        )
    }

    @Test
    fun classifiesEnglishResidencePermitVariants() {
        val text = """
            HELLENIC REPUBLIC
            RESIDENCE PERMIT
            PERMIT TYPE: SECOND GENERATION
        """.trimIndent()

        assertEquals(
            "Μετανάστευση / άδειες",
            MetadataExtractor.extract(text, "Document").category
        )
    }

    @Test
    fun mixedScriptCleanupEnablesPermitClassification() {
        val raw = "AΔEIA ΔIAMONHΣ"
        val cleaned = OcrTextPostProcessor.normalizeOcrText(raw)

        assertEquals(
            "Μετανάστευση / άδειες",
            MetadataExtractor.extract(cleaned, "Έγγραφο").category
        )
    }
}
