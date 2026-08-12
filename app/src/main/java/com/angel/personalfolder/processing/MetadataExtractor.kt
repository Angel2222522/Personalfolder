package com.angel.personalfolder.processing

import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Locale

data class ExtractedMetadata(
    val title: String,
    val category: String,
    val provider: String,
    val issuedDate: String?,
    val expiryDate: String?,
    val protocolNumber: String?,
    val keywords: List<String>,
    val issuedConfidence: String = "low",
    val expiryConfidence: String = "low",
    val json: String
)

object MetadataExtractor {
    private val dateRegex = Regex("""(?<!\d)(\d{1,2}[./-]\d{1,2}[./-]\d{4}|\d{4}[./-]\d{1,2}[./-]\d{1,2})(?!\d)""")
    private val protocolRegex = Regex(
        """(?:αριθ(?:μος|μο)?\s*(?:πρωτοκολλου|αιτησης)?|αρ\.?\s*πρωτ(?:οκ)?|protocol|application)\s*[:#№-]?\s*([a-zα-ω0-9][a-zα-ω0-9./_-]{2,})"""
    )

    private val categoryRules = linkedMapOf(
        "Μετανάστευση / άδειες" to listOf("άδεια διαμονής", "μετανάστευση", "migration", "residence permit", "visa"),
        "Κατοικία" to listOf("μισθωτήριο", "μίσθωση", "ενοίκιο", "δεη", "ρεύμα", "κατοικία", "μισθωτής"),
        "Δημόσιες υπηρεσίες" to listOf("gov.gr", "ααδε", "εφκα", "δήμος", "δημόσια", "αίτηση", "βεβαίωση"),
        "Εργασία" to listOf("εργασία", "εργοδότης", "σύμβαση", "μισθός", "ασφάλιση", "ένσημα"),
        "Οικονομικά" to listOf("τράπεζα", "φορολογία", "παράβολο", "λογαριασμός", "πληρωμή"),
        "Υγεία" to listOf("ιατρός", "νοσοκομείο", "διάγνωση", "συνταγή", "υγεία"),
        "Συμβόλαια" to listOf("σύμβαση", "όροι", "συμφωνητικό", "contract")
    )

    fun extract(text: String, fallbackTitle: String): ExtractedMetadata {
        val normalized = text.replace("\u0000", " ").trim()
        val lines = normalized.lines().map(String::trim).filter { it.length >= 3 }
        val title = lines.firstOrNull()?.take(100)?.ifBlank { fallbackTitle } ?: fallbackTitle
        val folded = foldGreek(normalized)
        val category = categoryRules.entries.firstOrNull { (_, words) -> words.any { folded.contains(foldGreek(it)) } }?.key
            ?: "Άλλα"
        val provider = lines.firstOrNull { line ->
            val value = foldGreek(line)
            listOf("υπουργ", "δημος", "ααδε", "gov", "δεη", "τραπεζ", "οργανισμ").any(value::contains)
        }?.take(120).orEmpty()

        val dateMatches = dateRegex.findAll(normalized).mapNotNull { match ->
            normalizeDate(match.value)?.let { canonical ->
                DateMatch(canonical, match.range, contextAround(normalized, match.range))
            }
        }.toList()
        val expiryCandidate = dateMatches
            .map { it to expiryScore(it.context) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<DateMatch, Int>> { it.second }.thenBy { it.first.range.first })
        val issuedCandidate = dateMatches
            .map { it to issuedScore(it.context) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<DateMatch, Int>> { it.second }.thenBy { -it.first.range.first })

        // An unlabelled date is not evidence of expiry. Keeping it unknown is
        // safer than turning document order into a product assumption.
        val expiry = expiryCandidate?.first?.canonical
        val expiryConfidence = when {
            expiryCandidate != null && expiryCandidate.second >= 3 -> "high"
            expiryCandidate != null -> "medium"
            expiry != null -> "low"
            else -> "none"
        }
        val issued = issuedCandidate?.first?.canonical
            ?: dateMatches.firstOrNull { it.canonical != expiry }?.canonical
        val issuedConfidence = when {
            issuedCandidate != null && issuedCandidate.second >= 3 -> "high"
            issuedCandidate != null -> "medium"
            issued != null -> "low"
            else -> "none"
        }
        val protocol = protocolRegex.find(folded)?.groupValues?.getOrNull(1)?.take(120)
        val keywords = categoryRules.flatMap { (key, words) ->
            if (folded.contains(foldGreek(key))) listOf(key) else words.filter { folded.contains(foldGreek(it)) }
        }.distinct().take(12)
        val json = buildString {
            append('{')
            append("\"title\":\"").append(jsonEscape(title)).append("\",")
            append("\"category\":\"").append(jsonEscape(category)).append("\",")
            append("\"provider\":\"").append(jsonEscape(provider)).append("\",")
            append("\"issuedDate\":").append(jsonValue(issued)).append(',')
            append("\"expiryDate\":").append(jsonValue(expiry)).append(',')
            append("\"protocolNumber\":").append(jsonValue(protocol)).append(',')
            append("\"keywords\":\"").append(jsonEscape(keywords.joinToString(","))).append("\",")
            append("\"issuedConfidence\":\"").append(issuedConfidence).append("\",")
            append("\"expiryConfidence\":\"").append(expiryConfidence).append("\",")
            append("\"confidence\":\"suggested\"")
            append('}')
        }
        return ExtractedMetadata(title, category, provider, issued, expiry, protocol, keywords, issuedConfidence, expiryConfidence, json)
    }

    private fun expiryScore(context: String): Int {
        val value = foldGreek(context)
        return when {
            listOf("ημερομηνια ληξης", "ημ ληξης", "expiry", "expires", "expiration").any(value::contains) -> 4
            listOf("ληξ", "εως", "μεχρι", "ισχυει", "valid until", "valid").any(value::contains) -> 3
            else -> 0
        }
    }

    private fun issuedScore(context: String): Int {
        val value = foldGreek(context)
        return when {
            listOf("ημερομηνια εκδοσης", "ημ εκδοσης", "issued", "issue date").any(value::contains) -> 4
            listOf("εκδοθ", "εκδοση", "αποφαση", "dated").any(value::contains) -> 3
            else -> 0
        }
    }

    private fun contextAround(text: String, range: IntRange): String = text.substring(
        (range.first - CONTEXT_RADIUS).coerceAtLeast(0),
        (range.last + 1 + CONTEXT_RADIUS).coerceAtMost(text.length)
    )

    private fun jsonValue(value: String?): String = value?.let { "\"${jsonEscape(it)}\"" } ?: "null"

    private fun foldGreek(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (character.code < 0x20) append("\\u%04x".format(Locale.ROOT, character.code)) else append(character)
            }
        }
    }

    private fun normalizeDate(raw: String): String? {
        val input = raw.replace('/', '.').replace('-', '.')
        val formats = listOf("dd.MM.yyyy", "d.M.yyyy", "yyyy.MM.dd", "yyyy.M.d")
        for (format in formats) {
            runCatching {
                val parsed = SimpleDateFormat(format, Locale.ROOT).apply { isLenient = false }.parse(input)
                if (parsed != null) return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(parsed)
            }
        }
        return null
    }

    private data class DateMatch(val canonical: String, val range: IntRange, val context: String)

    private const val CONTEXT_RADIUS = 80
}
