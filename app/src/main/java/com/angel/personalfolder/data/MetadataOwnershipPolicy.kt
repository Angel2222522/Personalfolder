package com.angel.personalfolder.data

data class MetadataFieldChanges(
    val title: Boolean,
    val category: Boolean,
    val provider: Boolean,
    val issuedDate: Boolean,
    val expiryDate: Boolean,
    val protocolNumber: Boolean
)

data class MetadataFieldOwnership(
    val title: Boolean,
    val category: Boolean,
    val provider: Boolean,
    val issuedDate: Boolean,
    val expiryDate: Boolean,
    val protocolNumber: Boolean
) {
    val any: Boolean get() = title || category || provider || issuedDate || expiryDate || protocolNumber
}

/** Merges user ownership per field; editing one field never claims the others. */
object MetadataOwnershipPolicy {
    fun merge(current: DocumentEntity, changes: MetadataFieldChanges): MetadataFieldOwnership =
        MetadataFieldOwnership(
            title = current.titleManuallyEdited || changes.title,
            category = current.categoryManuallyEdited || changes.category,
            provider = current.providerManuallyEdited || changes.provider,
            issuedDate = current.issuedDateManuallyEdited || changes.issuedDate,
            expiryDate = current.expiryDateManuallyEdited || changes.expiryDate,
            protocolNumber = current.protocolNumberManuallyEdited || changes.protocolNumber
        )
}
