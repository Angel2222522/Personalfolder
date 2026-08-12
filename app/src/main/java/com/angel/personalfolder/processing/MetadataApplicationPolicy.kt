package com.angel.personalfolder.processing

import com.angel.personalfolder.data.DocumentEntity
import com.angel.personalfolder.data.MetadataConfidence

/** Applies OCR candidates without turning unconfirmed values into facts. */
object MetadataApplicationPolicy {
    fun apply(document: DocumentEntity, metadata: ExtractedMetadata): DocumentEntity {
        val expiryIsAuthoritative = MetadataConfidence.isAuthoritative(metadata.expiryConfidence)
        return document.copy(
            title = if (document.titleManuallyEdited) document.title else metadata.title,
            category = if (document.categoryManuallyEdited) document.category else metadata.category,
            ocrText = document.ocrText,
            provider = if (document.providerManuallyEdited) document.provider else metadata.provider,
            issuedDate = if (document.issuedDateManuallyEdited) document.issuedDate else metadata.issuedDate,
            expiryDate = if (document.expiryDateManuallyEdited) document.expiryDate else metadata.expiryDate.takeIf { expiryIsAuthoritative },
            protocolNumber = if (document.protocolNumberManuallyEdited) document.protocolNumber else metadata.protocolNumber,
            expiryDateSuggestion = if (document.expiryDateManuallyEdited || expiryIsAuthoritative) null else metadata.expiryDate,
            expiryDateSuggestionConfidence = if (document.expiryDateManuallyEdited || expiryIsAuthoritative) MetadataConfidence.NONE else metadata.expiryConfidence,
            titleConfidence = if (document.titleManuallyEdited) MetadataConfidence.MANUAL else metadata.titleConfidence,
            categoryConfidence = if (document.categoryManuallyEdited) MetadataConfidence.MANUAL else metadata.categoryConfidence,
            providerConfidence = if (document.providerManuallyEdited) MetadataConfidence.MANUAL else metadata.providerConfidence,
            issuedDateConfidence = if (document.issuedDateManuallyEdited) MetadataConfidence.MANUAL else metadata.issuedConfidence,
            expiryDateConfidence = if (document.expiryDateManuallyEdited) MetadataConfidence.MANUAL else metadata.expiryConfidence,
            protocolNumberConfidence = if (document.protocolNumberManuallyEdited) MetadataConfidence.MANUAL else metadata.protocolConfidence,
            metadataManuallyEdited = document.titleManuallyEdited ||
                document.categoryManuallyEdited ||
                document.providerManuallyEdited ||
                document.issuedDateManuallyEdited ||
                document.expiryDateManuallyEdited ||
                document.protocolNumberManuallyEdited
        )
    }
}
