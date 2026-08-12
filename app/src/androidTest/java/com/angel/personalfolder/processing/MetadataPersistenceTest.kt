package com.angel.personalfolder.processing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.angel.personalfolder.data.AppDatabase
import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.ProcessingState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun unsafeExpiryIsStoredAsNullInDocumentEntity() = runBlocking {
        val extracted = extracted(expiryDate = null, expiryConfidence = "none")
        val safeExpiry = MetadataMerge.safeExpiry(extracted)
        database.documentDao().insert(document(expiryDate = safeExpiry))

        assertNull(database.documentDao().getById("doc")?.expiryDate)
    }

    @Test
    fun explicitExpiryIsStoredAsTheSameValueUsedByTheMergeRule() = runBlocking {
        val extracted = extracted(expiryDate = "2027-08-03", expiryConfidence = "high")
        val safeExpiry = MetadataMerge.safeExpiry(extracted)
        database.documentDao().insert(document(expiryDate = safeExpiry))

        assertEquals("2027-08-03", database.documentDao().getById("doc")?.expiryDate)
    }

    private fun extracted(expiryDate: String?, expiryConfidence: String) = ExtractedMetadata(
        title = "Έγγραφο",
        category = "Άλλα",
        provider = "",
        issuedDate = null,
        expiryDate = expiryDate,
        protocolNumber = null,
        keywords = emptyList(),
        issuedConfidence = "none",
        expiryConfidence = expiryConfidence,
        json = "{}"
    )

    private fun document(expiryDate: String?) = DocumentEntity(
        id = "doc",
        title = "Έγγραφο",
        originalFileName = "document.pdf",
        mimeType = "application/pdf",
        encryptedPath = "/private/doc/page_0.pf",
        pageCount = 1,
        expiryDate = expiryDate,
        processingState = ProcessingState.PROCESSED,
        createdAt = 1L,
        updatedAt = 1L
    )
}
