package com.angel.personalfolder.processing

import java.text.Normalizer
import java.time.LocalDate
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
    private val dateRegex = Regex(
        """(?<!\d)(\d{1,2}[./-]\d{1,2}[./-]\d{4}|\d{4}[./-]\d{1,2}[./-]\d{1,2})(?!\d)"""
    )

    /**
     * OCR often turns a range such as 07/10-09-2013 into a false match for
     * 10-09-2013. The whole range is deliberately excluded instead of
     * guessing which part was intended to be a standalone date.
     */
    private val compositeDateRangeRegex = Regex(
        """(?<!\d)\d{1,2}[./]\d{1,2}\s*[-–]\s*\d{1,2}[./-]\d{4}(?!\d)"""
    )

    /** Requires an actual protocol label; "Αριθμός" on its own is not enough. */
    private val protocolLabelRegex = Regex(
        """(?:αριθμ(?:ος|ο)?\.?\s+πρωτοκολλ(?:ου|ο)?\.?|αριθμ(?:ος|ο)?\.?\s+πρωτ(?:οκ)?\.?|αρ\.?\s*πρωτ(?:οκ)?\.?|protocol(?:\s+(?:no|number))?)\s*[:#№-]?\s*""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val protocolValueRegex = Regex(
        """^[\p{L}\p{N}]+(?:\s*/\s*[\p{L}\p{N}]+(?:\.[\p{L}\p{N}]+)*|[._/-][\p{L}\p{N}]+)*"""
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
        val category = categoryRules.entries.firstOrNull { (_, words) ->
            words.any { folded.contains(foldGreek(it)) }
        }?.key ?: "Άλλα"
        val provider = extractProvider(lines)

        val excludedDateRanges = compositeDateRangeRegex.findAll(normalized).map { it.range }.toList()
        val dateMatches = dateRegex.findAll(normalized)
            .filterNot { match ->
                excludedDateRanges.any { excluded ->
                    excluded.first <= match.range.last && match.range.first <= excluded.last
                }
            }
            .mapNotNull { match ->
                normalizeDate(match.value)?.let { canonical ->
                    DateMatch(canonical, match.range, contextAround(normalized, match.range))
                }
            }
            .toList()

        val expiryCandidate = dateMatches
            .map { it to expiryScore(it.context) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<DateMatch, Int>> { it.second }.thenBy { it.first.range.first })
        val issuedCandidate = dateMatches
            .map { it to issuedScore(it.context) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<DateMatch, Int>> { it.second }.thenBy { -it.first.range.first })

        // There is intentionally no positional fallback here. An unlabeled
        // date is not safe enough to become an issue or expiry date.
        val expiry = expiryCandidate?.first?.canonical
        val expiryConfidence = when {
            expiryCandidate == null -> "none"
            expiryCandidate.second >= 4 -> "high"
            else -> "medium"
        }
        val issued = issuedCandidate?.first?.canonical
        val issuedConfidence = when {
            issuedCandidate == null -> "none"
            issuedCandidate.second >= 4 -> "high"
            else -> "medium"
        }
        val protocol = extractProtocolNumber(folded)
        val keywords = categoryRules.flatMap { (key, words) ->
            if (folded.contains(foldGreek(key))) listOf(key)
            else words.filter { folded.contains(foldGreek(it)) }
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
        return ExtractedMetadata(
            title,
            category,
            provider,
            issued,
            expiry,
            protocol,
            keywords,
            issuedConfidence,
            expiryConfidence,
            json
        )
    }

    private fun extractProvider(lines: List<String>): String {
        val candidates = lines.mapIndexedNotNull { index, line ->
            providerScore(foldGreek(line)).takeIf { it > 0 }?.let { score ->
                ProviderCandidate(index, score)
            }
        }
        val best = candidates.maxWithOrNull(compareBy<ProviderCandidate> { it.score }.thenBy { -it.index })
            ?: return ""
        val selected = mutableListOf(lines[best.index])
        var next = best.index + 1
        while (next < lines.size && selected.joinToString(" ").length < 120) {
            val nextLine = lines[next]
            val folded = foldGreek(nextLine)
            if (providerScore(folded) > 0 || !isHeaderContinuation(nextLine)) break
            selected += nextLine
            next++
        }
        return selected.joinToString(" ").replace(Regex("\\s+"), " ").trim().take(120)
    }

    private fun providerScore(value: String): Int = when {
        value.contains("υπουργ") -> 100
        value.contains("περιφερεια") || value.contains("διευθυν") -> 82
        value.contains("οργανισμ") -> 78
        value.contains("υπηρεσι") -> 72
        value.contains("σχολ") -> 68
        value.contains("δημ") && !value.contains("ελληνικη δημοκρατια") -> 62
        value.contains("ααδε") || value.contains("δεη") || value.contains("τραπεζ") -> 60
        value.contains("ελληνικη δημοκρατια") || value.contains("hellenic republic") -> 10
        else -> 0
    }

    private fun isHeaderContinuation(line: String): Boolean {
        val letters = line.filter { it.isLetter() }
        return letters.isNotEmpty() &&
            letters.length >= line.length / 3 &&
            line.length <= 90 &&
            line.any { it.isLetter() }
    }

    private fun extractProtocolNumber(text: String): String? {
        for (label in protocolLabelRegex.findAll(text)) {
            val remainder = text.substring(label.range.last + 1)
            val candidate = remainder.lineSequence()
                .map { it.trim().trimStart(':', '#', '№', '-') }
                .firstOrNull { it.isNotBlank() }
                ?: continue
            val value = protocolValueRegex.find(candidate)?.value?.trim() ?: continue
            if (value.any { it.isDigit() }) {
                return value.replace(Regex("\\s*/\\s*"), " /")
            }
        }
        return null
    }

    private fun expiryScore(context: String): Int {
        val value = foldGreek(context)
        return when {
            listOf(
                "ημερομηνια ληξης",
                "ημ ληξης",
                "ισχυει εως",
                "ισχυει μεχρι",
                "ληγει",
                "expiry date",
                "expiration date",
                "valid until",
                "expires"
            ).any(value::contains) -> 4
            listOf("expiry", "expiration", "ληξη", "εως", "μεχρι").any(value::contains) -> 3
            else -> 0
        }
    }

    private fun issuedScore(context: String): Int {
        val value = foldGreek(context)
        return when {
            listOf(
                "ημερομηνια εκδοσης",
                "ημ εκδοσης",
                "ημερομηνια εκδοθηκε",
                "date of issue",
                "issue date",
                "issued"
            ).any(value::contains) -> 4
            listOf("εκδοθηκε", "εκδοθεν", "dated").any(value::contains) -> 3
            else -> 0
        }
    }

    private fun contextAround(text: String, range: IntRange): String {
        val lineStart = text.lastIndexOf('\n', range.first).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', range.last).let { if (it < 0) text.length else it }
        val currentLine = text.substring(lineStart, lineEnd)
        val relativeStart = range.first - lineStart
        val relativeEnd = range.last - lineStart + 1
        val remainder = (currentLine.substring(0, relativeStart) + currentLine.substring(relativeEnd))
            .trim()
            .trim(':', '-', '–', '—', '(', ')', '[', ']')
            .trim()
        if (remainder.isNotBlank()) return currentLine

        val previousStart = text.lastIndexOf('\n', (lineStart - 2).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        val previousLine = text.substring(previousStart, (lineStart - 1).coerceAtLeast(previousStart)).trim()
        if (previousLine.isNotBlank() && (expiryScore(previousLine) > 0 || issuedScore(previousLine) > 0)) {
            return "$previousLine $currentLine"
        }
        val nextEnd = text.indexOf('\n', (lineEnd + 1).coerceAtMost(text.length)).let {
            if (it < 0) text.length else it
        }
        val nextLine = text.substring((lineEnd + 1).coerceAtMost(text.length), nextEnd).trim()
        if (nextLine.isNotBlank() && (expiryScore(nextLine) > 0 || issuedScore(nextLine) > 0)) {
            return "$currentLine $nextLine"
        }
        return currentLine
    }

    private fun jsonValue(value: String?): String = value?.let { "\"${jsonEscape(it)}\"" } ?: "null"

    private fun foldGreek(value: String): String = Normalizer.normalize(
        value.replace('\u00B5', 'μ').lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")

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
        val parts = raw.replace('/', '.').replace('-', '.').split('.')
        if (parts.size != 3 || parts.any { it.isBlank() || !it.all { character -> character.isDigit() } }) return null
        val (day, month, year) = if (parts[0].length == 4) {
            Triple(parts[2].toIntOrNull(), parts[1].toIntOrNull(), parts[0].toIntOrNull())
        } else {
            Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].toIntOrNull())
        }
        if (day == null || month == null || year == null || year !in 1900..2200) return null
        return runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()
    }

    private data class DateMatch(val canonical: String, val range: IntRange, val context: String)
    private data class ProviderCandidate(val index: Int, val score: Int)

}
