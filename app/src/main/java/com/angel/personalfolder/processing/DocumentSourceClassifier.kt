package com.angel.personalfolder.processing

/** Shared, conservative classification of stored document sources. */
object DocumentSourceClassifier {
    fun isPdf(mimeType: String, sourceFileName: String, documentMimeType: String = ""): Boolean =
        mimeType.equals("application/pdf", ignoreCase = true) ||
            sourceFileName.substringAfterLast('.', "").equals("pdf", ignoreCase = true) ||
            (sourceFileName.isBlank() && documentMimeType.equals("application/pdf", ignoreCase = true))
}
