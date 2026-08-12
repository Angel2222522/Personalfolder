package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test
    fun normalizesGreekCaseAccentsAndWhitespace() {
        assertEquals("δημος θεσσαλονικης", SearchText.normalize("  ΔΗΜΟΣ   Θεσσαλονίκης  "))
    }
}
