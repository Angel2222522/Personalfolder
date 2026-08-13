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

    @Test
    fun pickerListsSurviveProcessRecreationAndAreConsumedOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PendingActivityStateStore.clearList(context, "export_ids")
        PendingActivityStateStore.saveList(context, "export_ids", listOf("doc-1", "doc-2"))
        assertEquals(listOf("doc-1", "doc-2"), PendingActivityStateStore.peekList(context, "export_ids"))
        assertEquals(listOf("doc-1", "doc-2"), PendingActivityStateStore.consumeList(context, "export_ids"))
        assertEquals(emptyList<String>(), PendingActivityStateStore.consumeList(context, "export_ids"))
    }
}
