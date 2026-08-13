package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupSizePolicyTest {
    @Test
    fun supportsTheDocumentLimitWithoutUsingDifferentRestoreLimit() {
        BackupSizePolicy.requireEntrySize(BackupSizePolicy.MAX_ENTRY_BYTES)
        BackupSizePolicy.requirePayloadSize(BackupSizePolicy.MAX_PAYLOAD_BYTES)
        BackupSizePolicy.requireArchiveSize(BackupSizePolicy.MAX_ARCHIVE_BYTES)
        BackupSizePolicy.requireManifestSize(BackupSizePolicy.MAX_MANIFEST_BYTES)
        assertEquals(BackupSizePolicy.MAX_PAYLOAD_BYTES + 32L * 1024 * 1024, BackupSizePolicy.MAX_ARCHIVE_BYTES)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEntryAboveLimit() {
        BackupSizePolicy.requireEntrySize(BackupSizePolicy.MAX_ENTRY_BYTES + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsArchiveAboveLimit() {
        BackupSizePolicy.requireArchiveSize(BackupSizePolicy.MAX_ARCHIVE_BYTES + 1)
    }

    @Test
    fun acceptsTheSameDocumentShapeUsedByCreateAndRestore() {
        val document = DocumentEntity(
            id = "doc-1",
            title = "Έγγραφο",
            originalFileName = "source.pdf",
            mimeType = "application/pdf",
            encryptedPath = "/private/doc-1/page_0.pf",
            pageCount = 2,
            ocrText = "κείμενο",
            createdAt = 1L,
            updatedAt = 1L
        )
        val pages = listOf(
            DocumentPageEntity("doc-1", 0, document.encryptedPath, "σελίδα 1", "source.pdf", "application/pdf"),
            DocumentPageEntity("doc-1", 1, document.encryptedPath, "σελίδα 2", "source.pdf", "application/pdf")
        )

        BackupSizePolicy.requireDocumentShapes(listOf(document), pages)
    }

    @Test
    fun rejectsPageThatWouldBeLostDuringRestore() {
        val document = DocumentEntity(
            id = "doc-1",
            title = "Έγγραφο",
            originalFileName = "source.png",
            mimeType = "image/png",
            encryptedPath = "/private/doc-1/page_0.pf",
            pageCount = 1,
            createdAt = 1L,
            updatedAt = 1L
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupSizePolicy.requireDocumentShapes(
                listOf(document),
                listOf(DocumentPageEntity("unknown", 0, "/private/unknown/page_0.pf"))
            )
        }
    }

    @Test
    fun rejectsOversizedSourceTextInsteadOfSilentlyShorteningIt() {
        val document = DocumentEntity(
            id = "doc-1",
            title = "Έγγραφο",
            originalFileName = "source.png",
            mimeType = "image/png",
            encryptedPath = "/private/doc-1/page_0.pf",
            pageCount = 1,
            createdAt = 1L,
            updatedAt = 1L
        )
        val page = DocumentPageEntity(
            documentId = document.id,
            pageIndex = 0,
            encryptedPath = document.encryptedPath,
            ocrText = "x".repeat(LibraryLimits.MAX_DOCUMENT_OCR_CHARS + 1),
            sourceFileName = "source.png",
            mimeType = "image/png"
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupSizePolicy.requireDocumentShapes(listOf(document), listOf(page))
        }
    }

    @Test
    fun rejectsTextThatWouldBeTruncatedDuringRestore() {
        val oversizedTitle = "x".repeat(LibraryLimits.MAX_DOCUMENT_TITLE_CHARS + 1)
        val document = DocumentEntity(
            id = "doc-1",
            title = oversizedTitle,
            originalFileName = "source.png",
            mimeType = "image/png",
            encryptedPath = "/private/doc-1/page_0.pf",
            pageCount = 1,
            createdAt = 1L,
            updatedAt = 1L
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupSizePolicy.requireTextShapes(
                documents = listOf(document),
                pages = emptyList(),
                cases = emptyList(),
                events = emptyList(),
                checklist = emptyList(),
                reminders = emptyList()
            )
        }
    }
}
