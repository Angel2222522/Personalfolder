package com.angel.personalfolder.processing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TesseractModelIntegrityTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedGreekModelIsTheExpectedCompleteFile() {
        assertModel(
            name = "ell.traineddata",
            expectedSize = 1_419_514L,
            expectedSha256 = "4fba8a0b461038d51f1c20d043d4f2ac38c4e778f1b90830847f7bd8fa3ba726"
        )
    }

    @Test
    fun packagedEnglishModelIsTheExpectedCompleteFile() {
        assertModel(
            name = "eng.traineddata",
            expectedSize = 4_113_088L,
            expectedSha256 = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
        )
    }

    private fun assertModel(name: String, expectedSize: Long, expectedSha256: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val assetPath = "tessdata/$name"
        val exists = runCatching {
            targetContext.assets.open(assetPath).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    digest.update(buffer, 0, count)
                }
            }
            true
        }.getOrDefault(false)
        assertTrue("Δεν βρέθηκε το asset OCR: $assetPath", exists)
        assertEquals("Λανθασμένο μέγεθος για $name", expectedSize, size)
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("Λανθασμένο SHA-256 για $name", expectedSha256, actualSha256)
    }
}
