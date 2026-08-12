package com.angel.personalfolder.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.angel.personalfolder.security.BackupCrypto
import com.angel.personalfolder.security.FileCrypto
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val password = "correct horse battery"
    private lateinit var database: AppDatabase
    private lateinit var documentId: String
    private lateinit var documentRoot: File

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.get(context)
        documentId = "backup-test-${UUID.randomUUID()}"
        documentRoot = context.filesDir.resolve("documents/$documentId").apply { mkdirs() }
        val encrypted = documentRoot.resolve("page_0.pf")
        FileCrypto.encrypt(ByteArrayInputStream("sensitive page".toByteArray(StandardCharsets.UTF_8)), encrypted)
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.documentDao().insert(DocumentEntity(documentId, "Backup test", "test.txt", "text/plain", encrypted.absolutePath, 1, processingState = ProcessingState.PROCESSED, createdAt = now, updatedAt = now))
            database.documentPageDao().insertAll(listOf(DocumentPageEntity(documentId, 0, encrypted.absolutePath, "OCR test", "test.txt", "text/plain")))
        }
    }

    @After
    fun tearDown() = runBlocking {
        database.withTransaction {
            database.documentPageDao().deleteForDocument(documentId)
            database.documentDao().deleteById(documentId)
        }
        FileCrypto.deleteRecursively(documentRoot)
        context.cacheDir.resolve("share").listFiles().orEmpty().filter { it.name.startsWith("backup-test") }.forEach(FileCrypto::deleteRecursively)
    }

    @Test
    fun backupCryptoRoundTripAndWrongPasswordAreRejected() {
        val source = context.cacheDir.resolve("share/backup-test-source.bin").apply { parentFile?.mkdirs(); writeText("payload") }
        val encrypted = context.cacheDir.resolve("share/backup-test-encrypted.bin").apply { createNewFile() }
        val encryptedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", encrypted)
        BackupCrypto.encryptFile(source, context, encryptedUri, password.toCharArray())
        val restored = context.cacheDir.resolve("share/backup-test-restored.bin")
        BackupCrypto.decryptToFile(context, encryptedUri, restored, password.toCharArray())
        assertEquals("payload", restored.readText())
        assertThrows(Exception::class.java) {
            BackupCrypto.decryptToFile(context, encryptedUri, context.cacheDir.resolve("share/backup-test-wrong.bin"), "wrong password".toCharArray())
        }
        source.delete(); encrypted.delete(); restored.delete()
    }

    @Test
    fun newBackupsRejectShortPasswords() {
        runBlocking {
            val backup = context.cacheDir.resolve("share/backup-test-short-password.pfb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            try {
                BackupService(context).create(backupUri, "short")
                fail("A new backup must reject a short password")
            } catch (_: IllegalArgumentException) {
                // Expected policy rejection.
            } finally {
                backup.delete()
            }
        }
    }

    @Test
    fun portableBackupRestoreRoundTripPreservesPageAndOcr() {
        runBlocking {
            val backup = context.cacheDir.resolve("share/backup-test-roundtrip.pfb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            val service = BackupService(context)
            service.create(backupUri, password)

            database.withTransaction {
                database.documentPageDao().deleteForDocument(documentId)
                database.documentDao().deleteById(documentId)
            }
            FileCrypto.deleteRecursively(documentRoot)
            service.restore(backupUri, password)

            val restored = database.documentDao().getById(documentId)
            assertNotNull(restored)
            assertEquals("OCR test", database.documentPageDao().getForDocument(documentId).single().ocrText)
            val plain = context.cacheDir.resolve("share/backup-test-plain.bin")
            FileCrypto.decryptToTemp(File(restored!!.encryptedPath), plain)
            assertEquals("sensitive page", plain.readText())
            plain.delete()
        }
    }

    @Test
    fun corruptedPortableBackupDoesNotChangeDatabase() {
        runBlocking {
            val backup = context.cacheDir.resolve("share/backup-test-corrupt.pfb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            BackupService(context).create(backupUri, password)
            val bytes = backup.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x7f).toByte()
            backup.writeBytes(bytes)
            assertThrows(Exception::class.java) { runBlocking { BackupService(context).restore(backupUri, password) } }
            assertNotNull(database.documentDao().getById(documentId))
        }
    }
}
