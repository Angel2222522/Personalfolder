package com.angel.personalfolder.data

/** Rebuilds the local FTS mirror when even one document row is missing. */
object FtsRepairPolicy {
    fun requiresRebuild(
        documentCount: Int,
        ftsCount: Long,
        documentIds: Set<String>,
        ftsIds: Set<String>
    ): Boolean = documentCount.toLong() != ftsCount || documentIds != ftsIds
}
