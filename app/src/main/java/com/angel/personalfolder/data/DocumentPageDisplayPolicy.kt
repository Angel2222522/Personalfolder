package com.angel.personalfolder.data

/** A page indicator and bitmap may be shown together only when their indices match. */
object DocumentPageDisplayPolicy {
    fun canDisplay(requestedPageIndex: Int, renderedPageIndex: Int?): Boolean =
        renderedPageIndex == requestedPageIndex
}
