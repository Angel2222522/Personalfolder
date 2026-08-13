package com.angel.personalfolder.data

/** UI selections are scoped to the currently visible filtered list. */
object DocumentSelectionPolicy {
    fun retainVisible(selectedIds: Set<String>, visibleIds: Collection<String>): Set<String> =
        selectedIds.intersect(visibleIds.toSet())
}
