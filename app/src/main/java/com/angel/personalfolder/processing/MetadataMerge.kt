package com.angel.personalfolder.processing

import com.angel.personalfolder.data.DocumentEntity
import java.time.LocalDate

/**
 * Selects the metadata value that is safe to persist after an OCR pass.
 * Keeping this rule outside the worker makes the persistence contract
 * independently testable and prevents the reminder/database values from
 * diverging.
 */
object MetadataMerge {
    private val storedExpiryRegex = Regex(
        """\"expiryDate\"\s*:\s*(?:\"([^\"]*)\"|null)"""
    )

    fun safeExpiry(metadata: ExtractedMetadata): String? = metadata.expiryDate
        ?.takeIf { metadata.expiryConfidence == "high" || metadata.expiryConfidence == "medium" }
        ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }

    fun expiryForOcr(document: DocumentEntity, metadata: ExtractedMetadata): String? {
        return if (isManualExpiry(document)) document.expiryDate else safeExpiry(metadata)
    }

    fun expiryWhenOcrHasNoText(document: DocumentEntity): String? =
        document.expiryDate.takeIf { isManualExpiry(document) }

    /**
     * V2.0.0 had one broad manual flag but no per-field flag. For those old
     * rows, a changed expiry compared with the last stored OCR suggestion is
     * evidence of a manual correction. If that evidence is absent, the new
     * conservative behavior clears the old automatic value on reprocessing.
     */
    fun isManualExpiry(document: DocumentEntity): Boolean {
        if (document.expiryDateManuallyEdited) return true
        if (!document.metadataManuallyEdited) return false
        val storedMatch = storedExpiryRegex.find(document.extractedMetadataJson) ?: return false
        val previousSuggestion = storedMatch.groupValues.getOrNull(1)?.ifBlank { null }
        return previousSuggestion != document.expiryDate
    }
}
