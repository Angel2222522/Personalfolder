package com.angel.personalfolder.security

import android.content.Context
import java.io.File

/** Stable on-device layout for encrypted document sources. */
object DocumentStorage {
    const val ROOT_DIRECTORY = "documents"

    fun root(context: Context): File = context.filesDir.resolve(ROOT_DIRECTORY)

    fun documentDirectory(context: Context, documentId: String): File =
        root(context).resolve(documentId)

    fun pageFile(context: Context, documentId: String, pageIndex: Int): File =
        documentDirectory(context, documentId).resolve("page_$pageIndex.pf")

    fun isPrivateDocumentFile(context: Context, file: File): Boolean {
        val documentRoot = runCatching { root(context).canonicalFile }.getOrNull() ?: return false
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return candidate != documentRoot && candidate.toPath().startsWith(documentRoot.toPath())
    }
}
