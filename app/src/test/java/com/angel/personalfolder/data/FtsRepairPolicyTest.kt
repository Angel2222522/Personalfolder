package com.angel.personalfolder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsRepairPolicyTest {
    @Test
    fun partialFtsLossRequiresRebuildEvenWhenSomeRowsRemain() {
        assertTrue(FtsRepairPolicy.requiresRebuild(2, 1L, setOf("a", "b"), setOf("a")))
    }

    @Test
    fun matchingDocumentAndFtsIdsDoNotRequireRebuild() {
        assertFalse(FtsRepairPolicy.requiresRebuild(2, 2L, setOf("a", "b"), setOf("b", "a")))
    }
}
