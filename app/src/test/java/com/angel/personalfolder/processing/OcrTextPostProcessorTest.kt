package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextPostProcessorTest {
    @Test
    fun repairsLatinLookalikesInsideClearlyGreekUppercaseTokens() {
        val input = "EΛΛHNIKH ΔHMOKPATIA"

        assertEquals(
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ",
            OcrTextPostProcessor.normalizeOcrText(input)
        )
    }

    @Test
    fun repairsKnownGreekRepublicHeadingVariantsWithoutGuessingValues() {
        assertEquals(
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ",
            OcrTextPostProcessor.normalizeOcrText("EAAHNIKH AHMOKPATIA")
        )
        assertEquals(
            "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ",
            OcrTextPostProcessor.normalizeOcrText("ΕΑΛΗΝΙΚΗ ΑΗΜΟΚΡΑΤΙΑ")
        )
    }

    @Test
    fun repairsOnlyKnownAdministrativeFieldTokens() {
        val input = "Natpwvupo: ΠΕΤΡΙΤ\nMntpwvuypo: ΜΙΡΕΛΑ\nόπως αναγράφεται στην aitnon"
        assertEquals(
            "Πατρώνυμο: ΠΕΤΡΙΤ\nΜητρώνυμο: ΜΙΡΕΛΑ\nόπως αναγράφεται στην αίτηση",
            OcrTextPostProcessor.normalizeOcrText(input)
        )
    }

    @Test
    fun repairsSchoolOrdinalSymbolWithoutChangingOtherNumbers() {
        val input = "Φοίτησε στο 28° Γυμνάσιο\nΑριθμός Μητρώου 4073"
        assertEquals(
            "Φοίτησε στο 28ο Γυμνάσιο\nΑριθμός Μητρώου 4073",
            OcrTextPostProcessor.normalizeOcrText(input)
        )
    }

    @Test
    fun nativePdfTextJoinsSplitSchoolOrdinalAndJoinedSchoolType() {
        val input = """
            ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ
            8
            ο ΔΗΜΟΤΙΚΟΣΧΟΛΕΙΟ ΔΟΚΙΜΗΣ
            Αριθ.Πρωτ.117
            Ημερομηνία: 09/03/2026
            ΒΕΒΑΙΩΣΗ ΦΟΙΤΗΣΗΣ
        """.trimIndent()

        val normalized = OcrTextPostProcessor.normalizeNativePdfText(input)

        assertTrue(normalized.contains("8ο ΔΗΜΟΤΙΚΟ ΣΧΟΛΕΙΟ ΔΟΚΙΜΗΣ"))
        val metadata = MetadataEvidenceRefiner.refine(
            MetadataExtractor.extract(normalized, "synthetic-school.pdf"),
            normalized
        )
        assertEquals("8ο ΔΗΜΟΤΙΚΟ ΣΧΟΛΕΙΟ ΔΟΚΙΜΗΣ", metadata.provider)
        assertEquals("117", metadata.protocolNumber)
        assertEquals("2026-03-09", metadata.issuedDate)
    }

    @Test
    fun doesNotRewritePureEnglishTokens() {
        val input = "RESIDENCE PERMIT API ID"

        assertEquals(input, OcrTextPostProcessor.normalizeOcrText(input))
    }

    @Test
    fun leavesAmbiguousSingleGreekMixedTokenUntouched() {
        val input = "IDΑ"

        assertEquals(input, OcrTextPostProcessor.normalizeOcrText(input))
    }

    @Test
    fun removesControlNoiseAndNormalizesSpaces() {
        val input = "  ΑΔΕΙΑ\u0000   ΔΙΑΜΟΝΗΣ\r\n\r\n\r\n  ΕΛΛΑΔΑ  "

        assertEquals(
            "ΑΔΕΙΑ ΔΙΑΜΟΝΗΣ\n\nΕΛΛΑΔΑ",
            OcrTextPostProcessor.normalizeOcrText(input)
        )
    }

    @Test
    fun acceptsUsefulNativePdfTextAndRejectsNoise() {
        assertTrue(OcrTextPostProcessor.isUsableNativePdfText("ΑΔΕΙΑ ΔΙΑΜΟΝΗΣ 12345"))
        assertFalse(OcrTextPostProcessor.isUsableNativePdfText("!@#$%^"))
        assertFalse(OcrTextPostProcessor.isUsableNativePdfText("abc\uFFFDdefgh"))
    }
}
