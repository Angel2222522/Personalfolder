package com.angel.personalfolder.processing

import java.text.Normalizer

/**
 * Conservative cleanup applied after OCR. It fixes Unicode noise and a common
 * Greek OCR failure where visually identical Latin capitals are mixed into an
 * otherwise Greek token. Pure English/Latin tokens are intentionally left alone.
 *
 * Keep corrections deterministic: this class may repair a known OCR spelling or
 * script confusion, but it must never invent missing document content.
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
        val scriptRepaired = MIXED_TOKEN.replace(normalized) { match -> repairMixedGreekToken(match.value) }
        return repairKnownAdministrativeOcrPatterns(scriptRepaired)
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

        // A longer mixed token with only visually-confusable Latin capitals is
        // overwhelmingly likely to be an OCR script error (e.g. AΔEIA). Short
        // identifiers remain untouched unless they contain multiple Greek letters.
        val strongGreekIntent = greekCount >= 2 || token.length >= 5
        if (!strongGreekIntent) return token
        return token.map { latinToGreekUppercase[it] ?: it }.joinToString("")
    }

    private fun repairKnownAdministrativeOcrPatterns(text: String): String {
        var repaired = GREEK_REPUBLIC_HEADING.replace(text, "ΕΛΛΗΝΙΚΗ ΔΗΜΟΚΡΑΤΙΑ")

        // OCR often reads the Greek ordinal omicron after a school number as a
        // degree/quote symbol. This rule is intentionally limited to school types.
        repaired = SCHOOL_ORDINAL.replace(repaired) { match ->
            "${match.groupValues[1]}ο ${match.groupValues[2]}"
        }

        // These are recurring OCR transliterations of standard Greek field words,
        // not document-specific names or values. Exact-token replacement avoids
        // rewriting arbitrary English text.
        ADMIN_FIELD_CORRECTIONS.forEach { (pattern, replacement) ->
            repaired = pattern.replace(repaired, replacement)
        }
        return repaired
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

    private val GREEK_REPUBLIC_HEADING = Regex(
        """\b(?:EAAHNIKH|ΕΑΛΗΝΙΚΗ|ΕΛΛΗΝΙΚΗ)\s+(?:AHMOKPATIA|ΑΗΜΟΚΡΑΤΙΑ|ΔΗΜΟΚΡΑΤΙΑ)\b""",
        RegexOption.IGNORE_CASE
    )
    private val SCHOOL_ORDINAL = Regex(
        """\b(\d{1,3})\s*[°º”″\"]\s*(Γυμνάσιο|Λύκειο|Δημοτικό)\b""",
        RegexOption.IGNORE_CASE
    )
    private val ADMIN_FIELD_CORRECTIONS = listOf(
        Regex("""\bNatpwvupo\b""", RegexOption.IGNORE_CASE) to "Πατρώνυμο",
        Regex("""\bMntpwvuypo\b""", RegexOption.IGNORE_CASE) to "Μητρώνυμο",
        Regex("""\baitnon\b""", RegexOption.IGNORE_CASE) to "αίτηση"
    )

    private val MIXED_TOKEN = Regex("[\\p{L}\\p{M}]{2,}")
    private val HORIZONTAL_SPACE = Regex("[\\t \\u00A0\\u2007\\u202F]+")
    private val EXCESS_BLANK_LINES = Regex("\\n{3,}")

    private const val MIN_NATIVE_MEANINGFUL_CHARS = 8
    private const val MIN_NATIVE_MEANINGFUL_RATIO = 0.45
}
