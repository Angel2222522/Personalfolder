package com.angel.personalfolder.processing

import com.angel.personalfolder.data.MetadataConfidence
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
    val issuedConfidence: String = MetadataConfidence.LOW,
    val expiryConfidence: String = MetadataConfidence.LOW,
    val json: String,
    val titleConfidence: String = MetadataConfidence.UNKNOWN,
    val categoryConfidence: String = MetadataConfidence.UNKNOWN,
    val providerConfidence: String = MetadataConfidence.UNKNOWN,
    val protocolConfidence: String = MetadataConfidence.UNKNOWN,
    val issuedProvenance: String = "unknown",
    val expiryProvenance: String = "unknown",
    val providerProvenance: String = "unknown",
    val protocolProvenance: String = "unknown"
)

/**
 * Extracts candidates without pretending that every OCR guess is a fact.
 * Values and their confidence/provenance travel together until persistence.
 */
object MetadataExtractor {
    private val dateRegex = Regex("""(?<!\d)(\d{1,2}[./-]\d{1,2}[./-]\d{4}|\d{4}[./-]\d{1,2}[./-]\d{1,2})(?!\d)""")
    private val protocolRegex = Regex(
        """^\s*(?:αριθμος[ \t]+πρωτοκολλου|αρ[ \t]*\.?[ \t]*πρωτ(?:οκολλου)?[ \t]*\.?|protocol(?:[ \t]+(?:no|number)[ \t]*\.?)?)[ \t]*[:#№-]?[ \t]*([a-zα-ω0-9][a-zα-ω0-9./_-]{1,119})(?=[ \t]|$)""",
        setOf(RegexOption.MULTILINE)
    )

    private val categoryRules = listOf(
        CategoryRule("Ταυτότητα / προσωπικά", listOf("διαβατήριο", "ταυτότητα", "passport", "personal number", "προσωπικός αριθμός", "άδεια οδήγησης")),
        CategoryRule("Μετανάστευση / άδειες", listOf("άδεια διαμονής", "μετανάστευση", "migration", "residence permit", "visa", "ασύλου")),
        CategoryRule("Κατοικία", listOf("μισθωτήριο", "μίσθωση", "ενοίκιο", "κατοικία", "μισθωτής", "διεύθυνση κατοικίας")),
        CategoryRule("Δημόσιες υπηρεσίες", listOf("gov.gr", "ααδε", "εφκα", "δήμος", "δημόσια υπηρεσία", "δημόσιο", "αίτηση", "βεβαίωση")),
        // «Σύμβαση» alone belongs to contracts; the more specific phrase
        // «σύμβαση εργασίας» wins for employment documents.
        CategoryRule("Εργασία", listOf("σύμβαση εργασίας", "εργασία", "εργοδότης", "μισθός", "ένσημα", "employment")),
        CategoryRule("Οικονομικά", listOf("τράπεζα", "φορολογία", "παράβολο", "πληρωμή", "iban", "φορολογική δήλωση")),
        CategoryRule("Λογαριασμοί", listOf("λογαριασμός", "δεη", "ρεύμα", "ύδρευση", "φυσικό αέριο", "τηλεφωνία", "internet bill")),
        CategoryRule("Υγεία", listOf("ιατρός", "νοσοκομείο", "διάγνωση", "συνταγή", "υγεία", "health")),
        CategoryRule("Συμβόλαια", listOf("σύμβαση", "όροι", "συμφωνητικό", "contract", "μίσθωση"))
    )

    fun extract(text: String, fallbackTitle: String): ExtractedMetadata {
        val normalized = text.replace("\u0000", " ").trim()
        val lines = normalized.lines().map(String::trim).filter { it.length >= 3 }
        val title = lines.firstOrNull()?.take(100)?.ifBlank { fallbackTitle } ?: fallbackTitle
        val titleConfidence = if (lines.isEmpty()) MetadataConfidence.UNKNOWN else MetadataConfidence.MEDIUM
        val folded = foldGreek(normalized)

        val categoryCandidate = categoryRules.mapNotNull { rule ->
            val matches = rule.terms.map { term ->
                val foldedTerm = foldGreek(term)
                if (folded.contains(foldedTerm)) foldedTerm.length else 0
            }.filter { it > 0 }
            if (matches.isEmpty()) null else CategoryCandidate(rule.name, matches.sum(), matches.maxOrNull() ?: 0)
        }.maxWithOrNull(compareBy<CategoryCandidate> { it.score }.thenBy { it.longestTerm })
        val category = categoryCandidate?.name ?: "Άλλα"
        val categoryConfidence = when {
            categoryCandidate == null -> MetadataConfidence.UNKNOWN
            categoryCandidate.score >= 16 -> MetadataConfidence.HIGH
            else -> MetadataConfidence.MEDIUM
        }

        val providerCandidate = lines.mapIndexedNotNull { index, line ->
            providerScore(line)?.let { score -> ProviderCandidate(line.take(120), score, index) }
        }.maxWithOrNull(compareBy<ProviderCandidate> { it.score }.thenByDescending { it.value.length }.thenBy { it.index })
        val provider = providerCandidate?.value.orEmpty()
        val providerConfidence = when {
            providerCandidate == null -> MetadataConfidence.UNKNOWN
            providerCandidate.score >= 5 -> MetadataConfidence.HIGH
            providerCandidate.score >= 3 -> MetadataConfidence.MEDIUM
            else -> MetadataConfidence.LOW
        }
        val providerProvenance = providerCandidate?.let { "issuer-marker:${it.score}" } ?: "none"

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

        val expiry = expiryCandidate?.first?.canonical
            ?: if (expiryCandidate == null && dateMatches.size >= 2) dateMatches.last().canonical else null
        val expiryConfidence = when {
            expiryCandidate != null && expiryCandidate.second >= 4 -> MetadataConfidence.HIGH
            expiryCandidate != null -> MetadataConfidence.MEDIUM
            expiry != null -> MetadataConfidence.LOW
            else -> MetadataConfidence.NONE
        }
        val expiryProvenance = when {
            expiryCandidate != null -> "expiry-label:${expiryCandidate.second}"
            expiry != null -> "fallback:last-date"
            else -> "none"
        }
        val issued = issuedCandidate?.first?.canonical
            ?: dateMatches.firstOrNull { it.canonical != expiry }?.canonical
        val issuedConfidence = when {
            issuedCandidate != null && issuedCandidate.second >= 4 -> MetadataConfidence.HIGH
            issuedCandidate != null -> MetadataConfidence.MEDIUM
            issued != null -> MetadataConfidence.LOW
            else -> MetadataConfidence.NONE
        }
        val issuedProvenance = when {
            issuedCandidate != null -> "issued-label:${issuedCandidate.second}"
            issued != null -> "fallback:first-other-date"
            else -> "none"
        }

        val protocolMatch = protocolRegex.find(folded)
        val protocol = protocolMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf(::isValidProtocol)
        val protocolConfidence = if (protocol == null) MetadataConfidence.NONE else MetadataConfidence.HIGH
        val protocolProvenance = if (protocol == null) "none" else "protocol-label"
        val keywords = categoryRules.flatMap { (name, terms) ->
            if (folded.contains(foldGreek(name))) listOf(name)
            else terms.filter { folded.contains(foldGreek(it)) }
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
            append("\"titleConfidence\":\"").append(titleConfidence).append("\",")
            append("\"categoryConfidence\":\"").append(categoryConfidence).append("\",")
            append("\"providerConfidence\":\"").append(providerConfidence).append("\",")
            append("\"issuedConfidence\":\"").append(issuedConfidence).append("\",")
            append("\"expiryConfidence\":\"").append(expiryConfidence).append("\",")
            append("\"protocolConfidence\":\"").append(protocolConfidence).append("\",")
            append("\"issuedProvenance\":\"").append(jsonEscape(issuedProvenance)).append("\",")
            append("\"expiryProvenance\":\"").append(jsonEscape(expiryProvenance)).append("\",")
            append("\"providerProvenance\":\"").append(jsonEscape(providerProvenance)).append("\",")
            append("\"protocolProvenance\":\"").append(jsonEscape(protocolProvenance)).append("\",")
            append("\"confidence\":\"suggested\"")
            append('}')
        }
        return ExtractedMetadata(
            title = title,
            category = category,
            provider = provider,
            issuedDate = issued,
            expiryDate = expiry,
            protocolNumber = protocol,
            keywords = keywords,
            issuedConfidence = issuedConfidence,
            expiryConfidence = expiryConfidence,
            json = json,
            titleConfidence = titleConfidence,
            categoryConfidence = categoryConfidence,
            providerConfidence = providerConfidence,
            protocolConfidence = protocolConfidence,
            issuedProvenance = issuedProvenance,
            expiryProvenance = expiryProvenance,
            providerProvenance = providerProvenance,
            protocolProvenance = protocolProvenance
        )
    }

    private fun providerScore(line: String): Int? {
        val value = foldGreek(line)
        if (value.contains("ελληνικη δημοκρατια") || value == "δημοκρατια") return 1
        var score = 0
        if (listOf("υπουργειο", "ministry").any(value::contains)) score += 5
        if (listOf("διευθυνση", "υπηρεσια", "γενικη γραμματεια", "directorate").any(value::contains)) score += 4
        if (listOf("δημος", "ααδε", "εφκα", "οργανισμος", "νοσοκομειο").any(value::contains)) score += 3
        if (listOf("gov.gr", "δεη", "τραπεζ").any(value::contains)) score += 2
        return score.takeIf { it > 0 }
    }

    private fun expiryScore(context: String): Int {
        val value = foldGreek(context)
        return when {
            listOf("ημερομηνια ληξης", "ημ ληξης", "expiry", "expires", "expiration").any(value::contains) -> 4
            listOf("ισχυει εως", "valid until").any(value::contains) -> 4
            listOf("ληξ", "εως", "μεχρι", "ισχυει", "valid").any(value::contains) -> 3
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

    private fun isValidProtocol(value: String): Boolean {
        val clean = value.trim('.', ':', '#', '-', '_', '/')
        return clean.length >= 3 && clean.any(Char::isDigit) && clean.all {
            it.isLetterOrDigit() || it == '.' || it == '/' || it == '_' || it == '-'
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

    private data class CategoryRule(val name: String, val terms: List<String>)
    private data class CategoryCandidate(val name: String, val score: Int, val longestTerm: Int)
    private data class ProviderCandidate(val value: String, val score: Int, val index: Int)
    private data class DateMatch(val canonical: String, val range: IntRange, val context: String)

    private const val CONTEXT_RADIUS = 80
}
