package com.angel.personalfolder.security

import android.net.Uri
import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private val MAGIC = "PFBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 120_000

    fun encryptFile(input: File, context: Context, destination: Uri, password: CharArray) {
        try {
            val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
            val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
            val key = key(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                writeHeader(output, salt, iv)
                CipherOutputStream(output, cipher).use { encrypted -> FileInputStream(input).use { it.copyTo(encrypted) } }
            } ?: error("Δεν ήταν δυνατή η δημιουργία του αντιγράφου.")
        } finally {
            password.fill('\u0000')
        }
    }

    fun decryptToFile(context: Context, source: Uri, destination: File, password: CharArray, maxBytes: Long = MAX_DECRYPTED_BYTES) {
        val temporary = File(destination.parentFile ?: destination.absoluteFile.parentFile!!, ".${destination.name}.${System.nanoTime()}.part")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                val salt = ByteArray(SALT_SIZE)
                val iv = ByteArray(IV_SIZE)
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Δεν αναγνωρίζεται το αρχείο αντιγράφου." }
                input.readFully(salt)
                input.readFully(iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
                temporary.parentFile?.mkdirs()
                CipherInputStream(input, cipher).use { decrypted ->
                    FileOutputStream(temporary).use { output -> decrypted.copyLimitedTo(output, maxBytes) }
                }
            } ?: error("Δεν ήταν δυνατή η ανάγνωση του αντιγράφου.")
            require(temporary.renameTo(destination)) { "Δεν ήταν δυνατή η ολοκλήρωση του αντιγράφου." }
        } finally {
            temporary.delete()
            password.fill('\u0000')
        }
    }

    private fun writeHeader(output: OutputStream, salt: ByteArray, iv: ByteArray) {
        output.write(MAGIC)
        output.write(salt)
        output.write(iv)
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = read(target, offset, target.size - offset)
            require(count >= 0) { "Ατελές αντίγραφο ασφαλείας." }
            offset += count
        }
    }

    private fun InputStream.copyLimitedTo(output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
            output.write(buffer, 0, read)
        }
    }

    /** Matches the application's aggregate backup payload budget plus zip overhead. */
    private const val MAX_DECRYPTED_BYTES = 2L * 1024 * 1024 * 1024 + 32L * 1024 * 1024
}
