package com.angel.personalfolder.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Encrypts document bytes at rest with a device-keystore protected AES key. */
object FileCrypto {
    private const val KEY_ALIAS = "personal_folder_document_key"
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'F'.code.toByte(), 'D'.code.toByte(), 1)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
    }

    fun encryptUri(context: Context, uri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { encrypt(it, destination) }
            ?: error("Δεν ήταν δυνατή η ανάγνωση του αρχείου.")
    }

    fun encrypt(input: InputStream, destination: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        FileOutputStream(destination).use { rawOut ->
            rawOut.write(MAGIC)
            rawOut.write(cipher.iv.size)
            rawOut.write(cipher.iv)
            CipherOutputStream(rawOut, cipher).use { encryptedOut -> input.copyTo(encryptedOut) }
        }
    }

    fun decryptToTemp(source: File, destination: File): File {
        destination.parentFile?.mkdirs()
        FileInputStream(source).use { rawIn ->
            val magic = ByteArray(MAGIC.size)
            rawIn.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Μη έγκυρο αρχείο Προσωπικού Φακέλου." }
            val ivSize = rawIn.read()
            require(ivSize in 12..16) { "Μη έγκυρη κεφαλίδα κρυπτογραφημένου αρχείου." }
            val iv = ByteArray(ivSize)
            rawIn.readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
            }
            CipherInputStream(rawIn, cipher).use { decryptedIn ->
                FileOutputStream(destination).use { decryptedIn.copyTo(it) }
            }
        }
        return destination
    }

    fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }

    private fun InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            require(read >= 0) { "Ατελές κρυπτογραφημένο αρχείο." }
            offset += read
        }
    }
}
