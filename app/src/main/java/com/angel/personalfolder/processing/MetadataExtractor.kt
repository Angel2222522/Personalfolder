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
    val json: String
)

object MetadataExtractor {
    private val dateRegex = Regex("""\b(\d{1,2}[./-]\d{1,2}[./-]\d{4}|\d{4}[./-]\d{1,2}[./-]\d{1,2})\b""")
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
        val lines = normalized.lines().map { it.trim() }.filter { it.length >= 3 }
        val title = lines.firstOrNull()?.take(100)?.ifBlank { fallbackTitle } ?: fallbackTitle
        val folded = foldGreek(normalized)
        val category = categoryRules.entries.firstOrNull { (_, words) -> words.any { folded.contains(foldGreek(it)) } }?.key
            ?: "Άλλα"
        val provider = lines.firstOrNull { line ->
            val value = foldGreek(line)
            listOf("υπουργ", "δημ", "ααδε", "gov", "δεη", "τραπεζ", "οργανισμ").any(value::contains)
        }?.take(120).orEmpty()
        val dates = dateRegex.findAll(normalized).mapNotNull { normalizeDate(it.value) }.distinct().toList()
        val expiry = dates.firstOrNull { date ->
            val index = normalized.indexOf(date)
            val context = normalized.substring(
                (index - 50).coerceAtLeast(0),
                (index + date.length + 50).coerceAtMost(normalized.length)
            ).lowercase()
            listOf("λήξ", "έως", "μέχρι", "ισχύει", "expiry", "valid").any(context::contains)
        } ?: dates.lastOrNull()
        val issued = dates.firstOrNull { it != expiry }
        val protocol = protocolRegex.find(folded)?.groupValues?.getOrNull(1)
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
            append("\"confidence\":\"suggested\"")
            append('}')
        }
        return ExtractedMetadata(title, category, provider, issued, expiry, protocol, keywords, json)
    }

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
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(Locale.ROOT, character.code))
                } else {
                    append(character)
                }
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
}
