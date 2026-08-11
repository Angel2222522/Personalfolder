package com.angel.personalfolder.processing

import org.json.JSONObject
import java.text.SimpleDateFormat
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
        """(?i)(?:αριθ(?:μός|μο)?\s*(?:πρωτοκόλλου|αίτησης)?|αρ\.?\s*πρωτ(?:οκ)?|protocol|application)\s*[:#№-]?\s*([A-ZΑ-Ω0-9][A-ZΑ-Ω0-9./_-]{2,})"""
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
        val lower = normalized.lowercase(Locale.ROOT)
        val category = categoryRules.entries.firstOrNull { (_, words) -> words.any(lower::contains) }?.key
            ?: "Άλλα"
        val provider = lines.firstOrNull { line ->
            val value = line.lowercase(Locale.ROOT)
            listOf("υπουργ", "δήμ", "ααδε", "gov", "δεη", "τράπεζ", "οργανισμ").any(value::contains)
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
        val protocol = protocolRegex.find(normalized)?.groupValues?.getOrNull(1)
        val keywords = categoryRules.flatMap { (key, words) ->
            if (lower.contains(key.lowercase())) listOf(key) else words.filter(lower::contains)
        }.distinct().take(12)
        val json = JSONObject().apply {
            put("title", title)
            put("category", category)
            put("provider", provider)
            put("issuedDate", issued ?: JSONObject.NULL)
            put("expiryDate", expiry ?: JSONObject.NULL)
            put("protocolNumber", protocol ?: JSONObject.NULL)
            put("keywords", keywords.joinToString(","))
            put("confidence", "suggested")
        }.toString()
        return ExtractedMetadata(title, category, provider, issued, expiry, protocol, keywords, json)
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
