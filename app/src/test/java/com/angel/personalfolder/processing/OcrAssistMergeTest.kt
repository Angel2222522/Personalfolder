package com.angel.personalfolder.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAssistMergeTest {
    @Test
    fun greekOnlyPassRepairsOnlyGreekSideOfBilingualAdministrativeFields() {
        val primary = """
            Επώνυμο: NTEPBIZI (DERVISHI, latin text)
            Όνομα: ΑΓΓΕΛΟΣ (AGJELOS, latin text)
            Πατρώνυμο: ΠΕΤΡΙΤ (PETRIT, latin text)
            Μητρώνυμο: MIPEAA (MIRELA, latin text)
            RESIDENCE PERMIT
        """.trimIndent()
        val greekOnly = """
            Επώνυμο: ΝΤΕΡΒΙΣΙ (garbled latin)
            Όνομα: ΑΓΓΕΛΟΣ (garbled latin)
            Πατρώνυμο: ΠΕΤΡΙΤ (garbled latin)
            Μητρώνυμο: ΜΙΡΕΛΑ (garbled latin)
        """.trimIndent()

        val merged = OcrTextPostProcessor.mergeGreekAdministrativeFields(primary, greekOnly)

        assertTrue(merged.contains("Επώνυμο: ΝΤΕΡΒΙΣΙ (DERVISHI, latin text)"))
        assertTrue(merged.contains("Μητρώνυμο: ΜΙΡΕΛΑ (MIRELA, latin text)"))
        assertTrue(merged.contains("RESIDENCE PERMIT"))
        assertFalse(merged.contains("garbled latin"))
    }

    @Test
    fun weakGreekAssistDoesNotOverwritePrimaryField() {
        val primary = "Μητρώνυμο: MIPEAA (MIRELA)"
        val weakGreek = "Μητρώνυμο: MIRELA (MIRELA)"
        assertEquals(primary, OcrTextPostProcessor.mergeGreekAdministrativeFields(primary, weakGreek))
    }

    @Test
    fun recognizesWhenAdministrativeGreekAssistIsWorthRunning() {
        assertTrue(OcrTextPostProcessor.needsGreekAdministrativeFieldAssist("Πατρώνυμο: ABC"))
        assertFalse(OcrTextPostProcessor.needsGreekAdministrativeFieldAssist("RESIDENCE PERMIT"))
    }
}
