package com.angel.personalfolder.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.angel.personalfolder.security.FileCrypto
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryImportDeleteExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = AppDatabase.get(context)

    @Test
    fun importRejectsInvalidImageBeforeRoomCommit() = runBlocking {
        val source = context.cacheDir.resolve("share/repository-import-${UUID.randomUUID()}.png").apply {
            parentFile?.mkdirs()
        }
        val documentsRoot = context.filesDir.resolve("documents")
        val before = documentsRoot.listFiles().orEmpty().map(File::getName).toSet()
        source.writeText("not an image", StandardCharsets.UTF_8)
        val sourceUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)

        try {
            assertThrows(Exception::class.java) {
                runBlocking { FolderRepository(context).importUris(listOf(sourceUri)) }
            }
            assertEquals(before, documentsRoot.listFiles().orEmpty().map(File::getName).toSet())
            assertTrue(database.documentDao().getAll().none { it.originalFileName == source.name })
        } finally {
            source.delete()
        }
    }

    @Test
    fun exportZipContainsManifestAndEncryptedSourceBytesAsPlainPageBytes() = runBlocking {
        val fixture = insertFixture("export-${UUID.randomUUID()}", "export-source.png", "exported page")
        val destination = context.cacheDir.resolve("share/${fixture.id}-${UUID.randomUUID()}.zip").apply {
            parentFile?.mkdirs()
        }
        val destinationUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)

        try {
            ExportService(context).exportDocuments(destinationUri, listOf(fixture.id))
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(FileInputStream(destination)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            assertTrue(entries.containsKey("manifest.json"))
            assertEquals("exported page", String(entries["documents/${fixture.id}/page_1.png"]!!, StandardCharsets.UTF_8))
        } finally {
            destination.delete()
            removeFixture(fixture.id)
        }
    }

    @Test
    fun exportZipKeepsEverySourceOfOneDocument() = runBlocking {
        val id = "export-multi-${UUID.randomUUID()}"
        val root = context.filesDir.resolve("documents/$id").apply { mkdirs() }
        val sources = listOf(
            root.resolve("page_0.pf") to "first source",
            root.resolve("page_1.pf") to "second source"
        )
        val destination = context.cacheDir.resolve("share/${id}-${UUID.randomUUID()}.zip").apply {
            parentFile?.mkdirs()
        }
        val destinationUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
        try {
            sources.forEach { (file, text) ->
                FileCrypto.encrypt(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)), file)
            }
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.documentDao().insert(
                    DocumentEntity(
                        id = id,
                        title = id,
                        originalFileName = "first.png",
                        mimeType = "image/png",
                        encryptedPath = sources.first().first.absolutePath,
                        pageCount = 2,
                        processingState = ProcessingState.PROCESSED,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                database.documentPageDao().insertAll(
                    sources.mapIndexed { index, (file, _) ->
                        DocumentPageEntity(id, index, file.absolutePath, sourceFileName = "source-$index.png", mimeType = "image/png")
                    }
                )
            }

            ExportService(context).exportDocuments(destinationUri, listOf(id))
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(FileInputStream(destination)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            assertEquals("first source", String(entries["documents/$id/page_1.png"]!!, StandardCharsets.UTF_8))
            assertEquals("second source", String(entries["documents/$id/page_2.png"]!!, StandardCharsets.UTF_8))
        } finally {
            destination.delete()
            database.withTransaction {
                database.documentPageDao().deleteForDocument(id)
                database.documentDao().deleteById(id)
            }
            FileCrypto.deleteRecursively(root)
        }
    }

    @Test
    fun deleteRemovesRoomRowsAndPrivateDocumentTree() = runBlocking {
        val fixture = insertFixture("delete-${UUID.randomUUID()}", "delete-source.png", "delete me")
        val root = context.filesDir.resolve("documents/${fixture.id}")

        try {
            FolderRepository(context).deleteDocument(fixture.id)
            assertEquals(null, database.documentDao().getById(fixture.id))
            assertTrue(database.documentPageDao().getForDocument(fixture.id).isEmpty())
            assertFalse(root.exists())
        } finally {
            removeFixture(fixture.id)
        }
    }

    @Test
    fun malformedDeleteJournalFailsClosedDuringStartupRecovery() = runBlocking {
        val journal = context.filesDir.resolve("document_delete_journal.json")
        try {
            journal.writeText("{not-json}")
            assertThrows(IllegalStateException::class.java) {
                runBlocking { DocumentDeletionRecovery.recover(context, database) }
            }
            assertTrue(journal.isFile)
        } finally {
            journal.delete()
        }
    }

    @Test
    fun confirmedExpiryCreatesRemindersAndClearingExpiryRemovesThem() = runBlocking {
        val fixture = insertFixture("reminder-${UUID.randomUUID()}", "reminder-source.png", "reminder")
        val repository = FolderRepository(context)

        try {
            repository.updateDocumentMetadata(
                id = fixture.id,
                title = fixture.title,
                category = fixture.category,
                tags = fixture.tags,
                provider = fixture.provider,
                issuedDate = fixture.issuedDate,
                expiryDate = "2099-12-31",
                protocolNumber = fixture.protocolNumber,
                confirmedFields = MetadataFieldConfirmations(
                    title = false,
                    category = false,
                    provider = false,
                    issuedDate = false,
                    expiryDate = true,
                    protocolNumber = false
                )
            )
            val confirmed = database.documentDao().getById(fixture.id)!!
            assertEquals("2099-12-31", confirmed.expiryDate)
            assertTrue(confirmed.expiryDateManuallyEdited)
            assertEquals(null, confirmed.expiryDateSuggestion)
            val reminders = database.reminderDao().getForDocument(fixture.id)
            assertEquals(3, reminders.size)
            assertTrue(reminders.all { it.deadlineAt > 0L && it.deadlineAt >= it.dueAt })

            repository.updateDocumentMetadata(
                id = fixture.id,
                title = fixture.title,
                category = fixture.category,
                tags = fixture.tags,
                provider = fixture.provider,
                issuedDate = fixture.issuedDate,
                expiryDate = null,
                protocolNumber = fixture.protocolNumber,
                confirmedFields = MetadataFieldConfirmations(
                    title = false,
                    category = false,
                    provider = false,
                    issuedDate = false,
                    expiryDate = true,
                    protocolNumber = false
                )
            )
            assertTrue(database.reminderDao().getForDocument(fixture.id).isEmpty())
        } finally {
            removeFixture(fixture.id)
        }
    }

    private suspend fun insertFixture(id: String, sourceName: String, contents: String): DocumentEntity {
        val root = context.filesDir.resolve("documents/$id").apply { mkdirs() }
        val encrypted = root.resolve("page_0.pf")
        FileCrypto.encrypt(ByteArrayInputStream(contents.toByteArray(StandardCharsets.UTF_8)), encrypted)
        val now = System.currentTimeMillis()
        val document = DocumentEntity(
            id = id,
            title = id,
            originalFileName = sourceName,
            mimeType = "image/png",
            encryptedPath = encrypted.absolutePath,
            pageCount = 1,
            processingState = ProcessingState.PROCESSED,
            createdAt = now,
            updatedAt = now
        )
        database.withTransaction {
            database.documentDao().insert(document)
            database.documentPageDao().insertAll(
                listOf(DocumentPageEntity(id, 0, encrypted.absolutePath, sourceFileName = sourceName, mimeType = "image/png"))
            )
        }
        return document
    }

    private suspend fun removeFixture(id: String) {
        ReminderScheduler.removeForDocument(context, id)
        database.withTransaction {
            database.documentPageDao().deleteForDocument(id)
            database.documentDao().deleteById(id)
        }
        FileCrypto.deleteRecursively(context.filesDir.resolve("documents/$id"))
    }
}
