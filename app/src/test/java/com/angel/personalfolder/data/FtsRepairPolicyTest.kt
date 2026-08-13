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

    @Test
    fun sameIdsWithDifferentIndexedContentRequireRebuild() {
        val documents = listOf(row("a", "old title"))
        val fts = listOf(row("a", "new title"))
        assertTrue(
            FtsRepairPolicy.requiresRebuild(
                documentCount = 1,
                ftsCount = 1L,
                documentIds = setOf("a"),
                ftsIds = setOf("a"),
                documentContentFingerprint = FtsRepairPolicy.contentFingerprint(documents),
                ftsContentFingerprint = FtsRepairPolicy.contentFingerprint(fts)
            )
        )
    }

    private fun row(id: String, title: String) = SearchIndexRow(
        documentId = id,
        title = title,
        originalFileName = "file.pdf",
        ocrText = "κείμενο",
        provider = "φορέας",
        category = "Άλλα",
        tags = "",
        protocolNumber = null
    )
}
