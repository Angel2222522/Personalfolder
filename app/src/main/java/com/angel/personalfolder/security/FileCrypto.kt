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

    private fun key(requireExisting: Boolean = false): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (requireExisting) throw KeyUnavailableException()
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

    fun encryptUri(context: Context, uri: Uri, destination: File): Long {
        ensureKeyAvailableForNewDocument(context)
        destination.parentFile?.mkdirs()
        return context.contentResolver.openInputStream(uri)?.use { encrypt(it, destination) }
            ?: error("Δεν ήταν δυνατή η ανάγνωση του αρχείου.")
    }

    /**
     * A missing Keystore alias is recoverable only before the first encrypted
     * document exists. Generating a replacement key afterwards would create a
     * mixed-key library and make the old ciphertext permanently unreadable.
     */
    fun ensureKeyAvailableForNewDocument(context: Context) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.getKey(KEY_ALIAS, null) is SecretKey) return
        val documentsRoot = context.filesDir.resolve("documents")
        if (containsEncryptedFile(documentsRoot)) throw KeyUnavailableException()
        key()
    }

    fun encrypt(input: InputStream, destination: File, maxBytes: Long = MAX_DOCUMENT_BYTES): Long {
        require(maxBytes > 0) { "Μη έγκυρο όριο μεγέθους." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val temporary = File(destination.parentFile ?: destination.absoluteFile.parentFile!!, ".${destination.name}.${System.nanoTime()}.part")
        var copied = 0L
        try {
            FileOutputStream(temporary).use { rawOut ->
                rawOut.write(MAGIC)
                rawOut.write(cipher.iv.size)
                rawOut.write(cipher.iv)
                CipherOutputStream(rawOut, cipher).use { encryptedOut -> copied = input.copyLimitedTo(encryptedOut, maxBytes) }
            }
            require(temporary.renameTo(destination)) { "Δεν ήταν δυνατή η ολοκλήρωση της κρυπτογράφησης." }
        } finally {
            temporary.delete()
        }
        return copied
    }

    fun decryptToTemp(source: File, destination: File): File {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile ?: destination.absoluteFile.parentFile!!, ".${destination.name}.${System.nanoTime()}.part")
        try {
            FileInputStream(source).use { rawIn ->
                val magic = ByteArray(MAGIC.size)
                rawIn.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Μη έγκυρο αρχείο Προσωπικού Φακέλου." }
                val ivSize = rawIn.read()
                require(ivSize in 12..16) { "Μη έγκυρη κεφαλίδα κρυπτογραφημένου αρχείου." }
                val iv = ByteArray(ivSize)
                rawIn.readFully(iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key(requireExisting = true), javax.crypto.spec.GCMParameterSpec(128, iv))
                }
                CipherInputStream(rawIn, cipher).use { decryptedIn ->
                    FileOutputStream(temporary).use { decryptedIn.copyTo(it) }
                }
            }
            require(temporary.renameTo(destination)) { "Δεν ήταν δυνατή η ολοκλήρωση της αποκρυπτογράφησης." }
        } finally {
            temporary.delete()
        }
        return destination
    }

    fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }

    fun isPrivateDocumentFile(context: Context, file: File): Boolean {
        val root = context.filesDir.resolve("documents").canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return candidate != root && candidate.toPath().startsWith(root.toPath())
    }

    private fun InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            require(read >= 0) { "Ατελές κρυπτογραφημένο αρχείο." }
            offset += read
        }
    }

    private fun InputStream.copyLimitedTo(output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Το αρχείο είναι υπερβολικά μεγάλο." }
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun containsEncryptedFile(directory: File): Boolean {
        return directory.listFiles().orEmpty().any { file ->
            if (file.isDirectory) containsEncryptedFile(file) else file.isFile && file.name.endsWith(".pf")
        }
    }

    private const val MAX_DOCUMENT_BYTES = 512L * 1024 * 1024

    class KeyUnavailableException : IllegalStateException("Το κλειδί κρυπτογράφησης της συσκευής δεν είναι διαθέσιμο. Τα υπάρχοντα έγγραφα δεν μπορούν να αποκρυπτογραφηθούν.")
}
