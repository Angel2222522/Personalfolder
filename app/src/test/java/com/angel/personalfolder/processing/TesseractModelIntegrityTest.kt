package com.angel.personalfolder.processing

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TesseractModelIntegrityTest {
    @Test
    fun bundledGreekModelIsTheExpectedCompleteFile() {
        assertModel(
            name = "ell.traineddata",
            expectedSize = 1_419_514L,
            expectedSha256 = "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"
        )
    }

    @Test
    fun bundledEnglishModelIsTheExpectedCompleteFile() {
        assertModel(
            name = "eng.traineddata",
            expectedSize = 4_113_088L,
            expectedSha256 = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
        )
    }

    private fun assertModel(name: String, expectedSize: Long, expectedSha256: String) {
        val file = File("src/main/assets/tessdata", name)
        assertTrue("Δεν βρέθηκε το μοντέλο OCR: ${file.path}", file.isFile)
        assertEquals("Λανθασμένο μέγεθος για $name", expectedSize, file.length())
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("Λανθασμένο SHA-256 για $name", expectedSha256, actualSha256)
    }
}
