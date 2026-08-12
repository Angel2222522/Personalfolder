package com.angel.personalfolder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataConfidenceTest {
    @Test
    fun lowConfidenceValueIsNotConfirmed() {
        assertFalse(MetadataConfidence.isConfirmed("2026-12-31", MetadataConfidence.LOW, manuallyEdited = false))
    }

    @Test
    fun manualOwnershipConfirmsOnlyTheOwnedField() {
        assertTrue(MetadataConfidence.isConfirmed("2026-12-31", MetadataConfidence.UNKNOWN, manuallyEdited = true))
        assertFalse(MetadataConfidence.isConfirmed("2026-12-31", MetadataConfidence.UNKNOWN, manuallyEdited = false))
    }
}
