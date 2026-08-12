package com.angel.personalfolder.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingActivityStateStoreTest {
    @Test
    fun passwordSurvivesPickerBoundaryOnlyOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PendingActivityStateStore.clear(context)
        PendingActivityStateStore.savePassword(context, "correct horse battery")
        assertEquals("correct horse battery", PendingActivityStateStore.consumePassword(context))
        assertNull(PendingActivityStateStore.consumePassword(context))
    }
}
