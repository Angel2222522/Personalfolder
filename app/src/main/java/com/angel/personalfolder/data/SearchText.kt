package com.angel.personalfolder.data

import java.text.Normalizer
import java.util.Locale

/** The single normalization rule shared by FTS indexing and user queries. */
object SearchText {
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String?): String = value.orEmpty()
        .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)
        .replace(whitespace, " ")
        .trim()
}
