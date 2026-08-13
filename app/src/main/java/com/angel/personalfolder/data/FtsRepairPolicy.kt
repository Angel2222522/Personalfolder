package com.angel.personalfolder.data

import java.security.MessageDigest

data class SearchIndexRow(
    val documentId: String,
    val title: String,
    val originalFileName: String,
    val ocrText: String,
    val provider: String,
    val category: String,
    val tags: String,
    val protocolNumber: String?
)

/** Rebuilds the local FTS mirror when identity or indexed content diverges. */
object FtsRepairPolicy {
    fun requiresRebuild(
        documentCount: Int,
        ftsCount: Long,
        documentIds: Set<String>,
        ftsIds: Set<String>,
        documentContentFingerprint: String? = null,
        ftsContentFingerprint: String? = null
    ): Boolean = documentCount.toLong() != ftsCount ||
        documentIds != ftsIds ||
        (documentContentFingerprint != null && documentContentFingerprint != ftsContentFingerprint)

    fun contentFingerprint(rows: Iterable<SearchIndexRow>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun update(value: String?) {
            val encoded = (value ?: "<null>").toByteArray(Charsets.UTF_8)
            digest.update(encoded.size.toString().toByteArray(Charsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(encoded)
        }
        rows.sortedBy { it.documentId }.forEach { row ->
            update(row.documentId)
            update(row.title)
            update(row.originalFileName)
            update(row.ocrText)
            update(row.provider)
            update(row.category)
            update(row.tags)
            update(row.protocolNumber)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
