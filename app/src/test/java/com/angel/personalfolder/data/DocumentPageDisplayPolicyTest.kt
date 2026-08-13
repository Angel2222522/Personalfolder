package com.angel.personalfolder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPageDisplayPolicyTest {
    @Test
    fun oldBitmapCannotBeShownWithNewPageNumber() {
        assertFalse(DocumentPageDisplayPolicy.canDisplay(requestedPageIndex = 2, renderedPageIndex = 1))
        assertTrue(DocumentPageDisplayPolicy.canDisplay(requestedPageIndex = 2, renderedPageIndex = 2))
    }
}
