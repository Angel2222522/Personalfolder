package com.angel.personalfolder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataOwnershipPolicyTest {
    @Test
    fun editingTitleDoesNotClaimOtherMetadataFields() {
        val document = DocumentEntity("id", "old", "file.pdf", "application/pdf", "/private/doc.pf", 1, createdAt = 1, updatedAt = 1)
        val ownership = MetadataOwnershipPolicy.merge(
            document,
            MetadataFieldChanges(title = true, category = false, provider = false, issuedDate = false, expiryDate = false, protocolNumber = false)
        )
        assertTrue(ownership.title)
        assertFalse(ownership.category)
        assertFalse(ownership.provider)
        assertFalse(ownership.issuedDate)
        assertFalse(ownership.expiryDate)
        assertFalse(ownership.protocolNumber)
    }
}
