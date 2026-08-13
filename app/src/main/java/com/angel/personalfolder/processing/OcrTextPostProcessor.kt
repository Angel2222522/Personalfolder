package com.angel.personalfolder.processing

import java.text.Normalizer

/**
 * Conservative cleanup applied after OCR. It fixes Unicode noise and a common
 * Greek OCR failure where visually identical Latin capitals are mixed into an
 * otherwise Greek token. Pure English/Latin tokens are intentionally left alone.
 */
object OcrTextPostProcessor {
    private val latinToGreekUppercase = mapOf(
        'A' to 'Α',
        'B' to 'Β',
        'E' to 'Ε',
        'H' to 'Η',
        'I' to 'Ι',
        'K' to 'Κ',
        'M' to 'Μ',
        'N' to 'Ν',
        'O' to 'Ο',
        'P' to 'Ρ',
        'T' to 'Τ',
        'X' to 'Χ',
        'Y' to 'Υ',
        'Z' to 'Ζ'
    )

    fun normalizeNativePdfText(text: String): String = normalizeUnicodeAndWhitespace(text)

    fun normalizeOcrText(text: String): String {
        val normalized = normalizeUnicodeAndWhitespace(text)
        return MIXED_TOKEN.replace(normalized) { match -> repairMixedGreekToken(match.value) }
    }

    fun isUsableNativePdfText(text: String): Boolean {
        val normalized = normalizeNativePdfText(text)
        if (normalized.isBlank()) return false
        val nonWhitespace = normalized.count { !it.isWhitespace() }
        if (nonWhitespace == 0) return false
        val meaningful = normalized.count { it.isLetterOrDigit() }
        val replacementCharacters = normalized.count { it == '\uFFFD' }
        val controls = normalized.count { Character.isISOControl(it) && it != '\n' && it != '\t' }
        return meaningful >= MIN_NATIVE_MEANINGFUL_CHARS &&
            meaningful.toDouble() / nonWhitespace >= MIN_NATIVE_MEANINGFUL_RATIO &&
            replacementCharacters == 0 &&
            controls == 0
    }

    private fun normalizeUnicodeAndWhitespace(text: String): String {
        val canonical = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')

        val cleaned = buildString(canonical.length) {
            canonical.forEach { character ->
                when {
                    character == '\n' || character == '\t' -> append(character)
                    Character.isISOControl(character) -> append(' ')
                    else -> append(character)
                }
            }
        }

        return cleaned
            .lines()
            .joinToString("\n") { line -> line.replace(HORIZONTAL_SPACE, " ").trim() }
            .replace(EXCESS_BLANK_LINES, "\n\n")
            .trim()
    }

    private fun repairMixedGreekToken(token: String): String {
        val greekCount = token.count(::isGreekLetter)
        if (greekCount == 0) return token

        val latinLetters = token.filter(::isLatinLetter)
        if (latinLetters.isEmpty()) return token
        if (latinLetters.any { it !in latinToGreekUppercase }) return token

        // At least two Greek letters makes the intent strong enough to repair
        // look-alike Latin capitals without touching genuine English words.
        if (greekCount < 2) return token
        return token.map { latinToGreekUppercase[it] ?: it }.joinToString("")
    }

    private fun isGreekLetter(character: Char): Boolean =
        character.isLetter() && Character.UnicodeBlock.of(character) in GREEK_BLOCKS

    private fun isLatinLetter(character: Char): Boolean =
        character.isLetter() && Character.UnicodeBlock.of(character) in LATIN_BLOCKS

    private val GREEK_BLOCKS = setOf(
        Character.UnicodeBlock.GREEK,
        Character.UnicodeBlock.GREEK_EXTENDED
    )

    private val LATIN_BLOCKS = setOf(
        Character.UnicodeBlock.BASIC_LATIN,
        Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
        Character.UnicodeBlock.LATIN_EXTENDED_A,
        Character.UnicodeBlock.LATIN_EXTENDED_B
    )

    private val MIXED_TOKEN = Regex("[\\p{L}\\p{M}]{2,}")
    private val HORIZONTAL_SPACE = Regex("[\\t \\u00A0\\u2007\\u202F]+")
    private val EXCESS_BLANK_LINES = Regex("\\n{3,}")

    private const val MIN_NATIVE_MEANINGFUL_CHARS = 8
    private const val MIN_NATIVE_MEANINGFUL_RATIO = 0.45
}
