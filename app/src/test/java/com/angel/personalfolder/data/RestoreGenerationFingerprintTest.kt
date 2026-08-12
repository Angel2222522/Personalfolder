package com.angel.personalfolder.data

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class RestoreGenerationFingerprintTest {
    @Test
    fun metadataChangeChangesDatabaseGenerationFingerprintEvenWhenIdStaysTheSame() {
        val base = document(title = "Παλιά τιμή")
        val changed = base.copy(title = "Νέα τιμή")

        val first = fingerprint(base)
        val second = fingerprint(changed)

        assertNotNull(first)
        assertNotEquals(first, second)
    }

    @Test
    fun rowOrderingDoesNotChangeDatabaseGenerationFingerprint() {
        val first = document("doc-1", "Ένα")
        val second = document("doc-2", "Δύο")

        assertEquals(
            fingerprint(first, second),
            fingerprint(second, first)
        )
    }

    @Test
    fun filesystemContentChangeChangesFilesystemGenerationFingerprint() {
        val root = Files.createTempDirectory("personal-folder-generation-").toFile()
        try {
            val file = root.resolve("doc-1/page_0.pf").apply {
                parentFile.mkdirs()
                writeText("first generation")
            }
            val first = RestoreGenerationFingerprint.filesystemOf(root)
            file.writeText("second generation")
            val second = RestoreGenerationFingerprint.filesystemOf(root)

            assertNotNull(first)
            assertNotEquals(first, second)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fingerprint(vararg documents: DocumentEntity): String =
        RestoreGenerationFingerprint.of(
            documents = documents.toList(),
            pages = emptyList(),
            cases = emptyList(),
            relations = emptyList(),
            events = emptyList(),
            checklist = emptyList(),
            reminders = emptyList()
        )

    private fun document(id: String = "doc-1", title: String): DocumentEntity =
        DocumentEntity(
            id = id,
            title = title,
            originalFileName = "$id.pdf",
            mimeType = "application/pdf",
            encryptedPath = "/data/data/com.angel.personalfolder/files/documents/$id/page_0.pf",
            pageCount = 1,
            createdAt = 1L,
            updatedAt = 2L
        )
}
