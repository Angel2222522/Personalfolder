package com.angel.personalfolder.processing

import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.MetadataConfidence
import java.text.Normalizer
import java.util.Locale

/** Applies OCR candidates without turning unconfirmed values into facts. */
object MetadataApplicationPolicy {
    fun apply(document: DocumentEntity, metadata: ExtractedMetadata): DocumentEntity {
        val expiryBelongsToDocument = expiryCandidateBelongsToDocument(metadata)
        val acceptedExpiry = metadata.expiryDate.takeIf { expiryBelongsToDocument }
        val acceptedExpiryConfidence = if (expiryBelongsToDocument) {
            metadata.expiryConfidence
        } else {
            MetadataConfidence.NONE
        }
        val expiryIsAuthoritative = MetadataConfidence.isAuthoritative(acceptedExpiryConfidence)

        return document.copy(
            title = if (document.titleManuallyEdited) document.title else metadata.title,
            category = if (document.categoryManuallyEdited) document.category else metadata.category,
            ocrText = document.ocrText,
            provider = if (document.providerManuallyEdited) document.provider else metadata.provider,
            issuedDate = if (document.issuedDateManuallyEdited) document.issuedDate else metadata.issuedDate,
            expiryDate = if (document.expiryDateManuallyEdited) document.expiryDate else acceptedExpiry.takeIf { expiryIsAuthoritative },
            protocolNumber = if (document.protocolNumberManuallyEdited) document.protocolNumber else metadata.protocolNumber,
            expiryDateSuggestion = if (
                document.expiryDateManuallyEdited ||
                expiryIsAuthoritative ||
                acceptedExpiry == null
            ) {
                null
            } else {
                acceptedExpiry
            },
            expiryDateSuggestionConfidence = if (
                document.expiryDateManuallyEdited ||
                expiryIsAuthoritative ||
                acceptedExpiry == null
            ) {
                MetadataConfidence.NONE
            } else {
                acceptedExpiryConfidence
            },
            titleConfidence = if (document.titleManuallyEdited) MetadataConfidence.MANUAL else metadata.titleConfidence,
            categoryConfidence = if (document.categoryManuallyEdited) MetadataConfidence.MANUAL else metadata.categoryConfidence,
            providerConfidence = if (document.providerManuallyEdited) MetadataConfidence.MANUAL else metadata.providerConfidence,
            issuedDateConfidence = if (document.issuedDateManuallyEdited) MetadataConfidence.MANUAL else metadata.issuedConfidence,
            expiryDateConfidence = if (document.expiryDateManuallyEdited) MetadataConfidence.MANUAL else acceptedExpiryConfidence,
            protocolNumberConfidence = if (document.protocolNumberManuallyEdited) MetadataConfidence.MANUAL else metadata.protocolConfidence,
            metadataManuallyEdited = document.titleManuallyEdited ||
                document.categoryManuallyEdited ||
                document.providerManuallyEdited ||
                document.issuedDateManuallyEdited ||
                document.expiryDateManuallyEdited ||
                document.protocolNumberManuallyEdited
        )
    }

    /**
     * Applications and reports often quote the expiry of another object (for
     * example an existing permit). That referenced date must never become the
     * expiry of the application itself. Manual user values remain untouched.
     */
    private fun expiryCandidateBelongsToDocument(metadata: ExtractedMetadata): Boolean {
        if (metadata.expiryDate == null) return true
        val foldedTitle = fold(metadata.title)
        return NON_EXPIRING_REFERENCE_DOCUMENT_TERMS.none(foldedTitle::contains)
    }

    private fun fold(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")

    private val NON_EXPIRING_REFERENCE_DOCUMENT_TERMS = listOf(
        "αιτηση",
        "αναφορα",
        "application",
        "request"
    )
}
