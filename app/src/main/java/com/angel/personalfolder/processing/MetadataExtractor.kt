package com.angel.personalfolder.processing

import com.angel.personalfolder.data.MetadataConfidence
import java.text.Normalizer
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
        // Horizontal whitespace only: \s also matches newlines and previously let
        // a protocol capture spill into the next OCR line.
        """^[ \t]*(?:αριθμ(?:ός|ος)?\.?[ \t]+πρωτ(?:οκ(?:ό|ο)λλου)?\.?|αρ\.?[ \t]*πρωτ(?:οκ(?:ό|ο)λλου)?\.?|protocol(?:[ \t]+(?:no|number)\.?)?)[ \t]*[:#№-]?[ \t]*([\p{L}\d][\p{L}\d./_\- \t]{1,119})[ \t]*$""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )
    private val datelineContextRegex = Regex("""^[\p{L}\p{M} .΄'’\-]{2,50}[:,]\s*$""")
    private val schoolProviderAnchor = Regex(
        """\b\d{1,3}\s*[οo0]?\s*(?:Γυμνάσιο|Λύκειο|Δημοτικό)\b""",
        RegexOption.IGNORE_CASE
    )
    private val schoolProviderStop = Regex(
        """\s+(?:με|που|όπου|όπως|για)\s+|[,;|]""",
        RegexOption.IGNORE_CASE
    )

    private val categoryRules = listOf(
        CategoryRule("Ταυτότητα / προσωπικά", listOf("διαβατήριο", "ταυτότητα", "passport", "personal number", "προσωπικός αριθμός", "άδεια οδήγησης")),
        CategoryRule(
            "Μετανάστευση / άδειες",
            listOf(
                "άδεια διαμονής",
                "άδεια παραμονής",
                "τίτλος διαμονής",
                "δελτίο διαμονής",
                "κάρτα διαμονής",
                "residence permit",
                "residence card",
                "residence document",
                "residence title",
                "permit type",
                "τύπος άδειας",
                "είδος άδειας",
                "μετανάστευση",
                "migration",
                "visa",
                "ασύλου",
                "πολίτης τρίτης χώρας",
                "πολιτών τρίτων χωρών",
                "δεύτερης γενιάς",
                "second generation",
                "ενιαίου τύπου",
                "υπουργείο μετανάστευσης",
                "ministry of migration"
            )
        ),
        CategoryRule("Κατοικία", listOf("μισθωτήριο", "μίσθωση", "ενοίκιο", "κατοικία", "μισθωτής", "διεύθυνση κατοικίας")),
        CategoryRule("Δημόσιες υπηρεσίες", listOf("gov.gr", "ααδε", "εφκα", "δήμος", "δημόσια υπηρεσία", "δημόσιο", "αίτηση", "βεβαίωση")),
        CategoryRule("Εργασία", listOf("σύμβαση εργασίας", "εργασία", "εργοδότης", "μισθός", "ένσημα", "employment")),
        CategoryRule("Οικονομικά", listOf("τράπεζα", "φορολογία", "παράβολο", "πληρωμή", "iban", "φορολογική δήλωση")),
        CategoryRule("Λογαριασμοί", listOf("λογαριασμός", "δεη", "ρεύμα", "ύδρευση", "φυσικό αέριο", "τηλεφωνία", "internet bill")),
        CategoryRule("Υγεία", listOf("ιατρός", "νοσοκομείο", "διάγνωση", "συνταγή", "υγεία", "health")),
        CategoryRule("Συμβόλαια", listOf("σύμβαση", "όροι", "συμφωνητικό", "contract", "μίσθωση"))
    )

    private val strongIssuedKeywords = listOf(
        "ημερομηνία δημιουργίας",
        "ημ δημιουργίας",
        "ημερομηνία έκδοσης",
        "ημ έκδοσης",
        "creation date",
        "created on",
        "issue date",
        "issued on"
    )
    private val issuedKeywords = listOf(
        "δημιουργία",
        "δημιουργήθηκε",
        "έκδοση",
        "εκδόθηκε",
        "created",
        "creation",
        "issued"
    )
    private val strongExpiryKeywords = listOf(
        "ημερομηνία λήξης",
        "ημ λήξης",
        "ισχύει έως",
        "ισχύς έως",
        "ισχύει μέχρι",
        "valid until",
        "valid through",
        "expiry date"
    )
    private val expiryKeywords = listOf(
        "λήξη",
        "λήγει",
        "expires",
        "expiry",
        "expiration"
    )

    /**
     * supplementalText is secondary OCR evidence (for example a sparse-layout
     * pass). It can help title/provider/date/protocol extraction, but category
     * scoring intentionally stays on the primary visible OCR text so duplicate
     * helper text cannot inflate or distort classification.
     */
    fun extract(text: String, fallbackTitle: String, supplementalText: String = ""): ExtractedMetadata {
        val primaryText = text.replace("\u0000", " ").trim()

        // Metadata describes the document itself, not every attachment that may
        // be concatenated behind it. Bound extraction to the leading evidence so
        // a long dossier cannot inherit a category, protocol or date from a late
        // supporting document. The full OCR body remains stored/searchable.
        val metadataPrimaryText = primaryText.take(MAX_METADATA_EVIDENCE_CHARS)
        val metadataSupplementalText = supplementalText.replace("\u0000", " ").trim().take(MAX_METADATA_EVIDENCE_CHARS)
        val evidenceText = buildString {
            append(metadataPrimaryText)
            if (metadataSupplementalText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(metadataSupplementalText)
            }
        }
        val lines = evidenceText.lines().map(String::trim).filter { it.length >= 3 }

        // Do not assume that the first OCR line is the document title. Greek
        // official documents commonly start with state/ministerial hierarchy.
        // Prefer an actual document-type heading; if OCR missed it, a meaningful
        // filename-derived fallback is safer than promoting "Ελληνική Δημοκρατία".
        val titleCandidate = lines.mapIndexedNotNull { index, line ->
            titleScore(line, index)?.let { score -> TitleCandidate(line.take(100), score, index) }
        }.maxWithOrNull(compareBy<TitleCandidate> { it.score }.thenBy { -it.index })
        val fallbackTitleScore = titleScore(fallbackTitle, 0)
        val title = titleCandidate?.value
            ?: fallbackTitle.takeIf { fallbackTitleScore != null }
            ?: lines.firstOrNull()?.take(100)?.ifBlank { fallbackTitle }
            ?: fallbackTitle
        val titleConfidence = when {
            titleCandidate?.score?.let { it >= STRONG_TITLE_SCORE } == true -> MetadataConfidence.HIGH
            titleCandidate != null || fallbackTitleScore != null -> MetadataConfidence.MEDIUM
            lines.isEmpty() -> MetadataConfidence.UNKNOWN
            else -> MetadataConfidence.LOW
        }
        val folded = foldGreek(metadataPrimaryText)

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
            val candidateValue = extractSpecificProvider(line)
            providerScore(candidateValue)?.let { score -> ProviderCandidate(candidateValue.take(120), score, index) }
        }.maxWithOrNull(compareBy<ProviderCandidate> { it.score }.thenByDescending { it.value.length }.thenBy { it.index })
        val provider = providerCandidate?.value.orEmpty()
        val providerConfidence = when {
            providerCandidate == null -> MetadataConfidence.UNKNOWN
            providerCandidate.score >= 5 -> MetadataConfidence.HIGH
            providerCandidate.score >= 3 -> MetadataConfidence.MEDIUM
            else -> MetadataConfidence.LOW
        }
        val providerProvenance = providerCandidate?.let { "issuer-marker:${it.score}" } ?: "none"

        val dateMatches = dateRegex.findAll(evidenceText).mapNotNull { match ->
            normalizeDate(match.value)?.let { canonical ->
                DateMatch(
                    canonical = canonical,
                    range = match.range,
                    labelContext = labelContextBeforeDate(evidenceText, match.range)
                )
            }
        }.toList()
        val dateCandidates = dateMatches.map { match ->
            val explicitIssuedScore = keywordScore(match.labelContext, strongIssuedKeywords, issuedKeywords)
            val datelineScore = if (explicitIssuedScore == 0) datelineIssuedScore(match.labelContext) else 0
            DateCandidate(
                match = match,
                issuedScore = maxOf(explicitIssuedScore, datelineScore),
                issuedSource = when {
                    explicitIssuedScore > 0 -> "keyword"
                    datelineScore > 0 -> "dateline"
                    else -> "none"
                },
                expiryScore = keywordScore(match.labelContext, strongExpiryKeywords, expiryKeywords)
            )
        }

        val issuedCandidate = dateCandidates
            .filter { it.issuedScore > 0 }
            .maxWithOrNull(compareBy<DateCandidate> { it.issuedScore }.thenBy { -it.match.range.first })
        val expiryCandidate = dateCandidates
            .filter { it.expiryScore > 0 }
            .maxWithOrNull(compareBy<DateCandidate> { it.expiryScore }.thenBy { it.match.range.first })

        // Dates are metadata only when evidence belongs to that concrete OCR
        // date. A standard official dateline such as "Θεσσαλονίκη: 11-08-2026"
        // counts as medium-confidence issue-date evidence. It never creates an
        // expiry date, and unrelated/unlabelled dates are still ignored.
        val issued = issuedCandidate?.match?.canonical
        val expiry = expiryCandidate?.match?.canonical
        val issuedConfidence = when {
            issuedCandidate == null -> MetadataConfidence.NONE
            issuedCandidate.issuedScore >= STRONG_KEYWORD_SCORE -> MetadataConfidence.HIGH
            else -> MetadataConfidence.MEDIUM
        }
        val expiryConfidence = when {
            expiryCandidate == null -> MetadataConfidence.NONE
            expiryCandidate.expiryScore >= STRONG_KEYWORD_SCORE -> MetadataConfidence.HIGH
            else -> MetadataConfidence.MEDIUM
        }
        val issuedProvenance = issuedCandidate?.let { "issued-${it.issuedSource}:${it.issuedScore}" } ?: "none"
        val expiryProvenance = expiryCandidate?.let { "expiry-keyword:${it.expiryScore}" } ?: "none"

        // Protocol values are matched against the original bounded evidence so
        // their Greek letters/case are preserved. Late supporting attachments are
        // deliberately outside this scope.
        val protocolMatch = protocolRegex.find(evidenceText)
        val protocol = protocolMatch?.groupValues?.getOrNull(1)?.let(::normalizeProtocol)
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

    private fun extractSpecificProvider(line: String): String {
        val anchor = schoolProviderAnchor.find(line) ?: return line
        val candidate = line.substring(anchor.range.first)
        val stop = schoolProviderStop.find(candidate, anchor.value.length)?.range?.first ?: candidate.length
        return candidate.substring(0, stop).trim()
    }

    private fun titleScore(line: String, index: Int): Int? {
        val value = foldGreek(line)
        if (value.isBlank()) return null
        var score = 0
        if (listOf(
                "βεβαιωση σπουδων",
                "βεβαιωση φοιτησης",
                "πιστοποιητικο σπουδων",
                "residence permit",
                "residence card"
            ).any(value::contains)
        ) {
            score += 12
        } else if (listOf(
                "βεβαιωση",
                "πιστοποιητικο",
                "αδεια",
                "αποφαση",
                "αιτηση",
                "συμβαση",
                "γνωματευση",
                "certificate",
                "permit"
            ).any(value::contains)
        ) {
            score += 6
        }
        if (listOf(
                "ελληνικη δημοκρατια",
                "hellenic republic",
                "υπουργειο",
                "ministry",
                "περιφερειακη διευθυνση",
                "γενικη γραμματεια"
            ).any(value::contains)
        ) {
            score -= 10
        }
        if (line.length in 5..80) score += 1
        if (index < 30) score += 1
        return score.takeIf { it >= MIN_TITLE_SCORE }
    }

    private fun providerScore(line: String): Int? {
        val value = foldGreek(line)
        if (value.contains("ελληνικη δημοκρατια") || value == "δημοκρατια") return 1
        var score = 0
        // Prefer the concrete issuing institution over a generic parent heading.
        // This prevents a school certificate from being attributed merely to the
        // ministry when the school itself is present in OCR text.
        if (listOf("γυμνασιο", "λυκειο", "σχολειο", "πανεπιστημιο", "university", "school").any(value::contains)) score += 7
        if (listOf("υπουργειο", "ministry").any(value::contains)) score += 5
        if (listOf("διευθυνση", "υπηρεσια", "γενικη γραμματεια", "directorate").any(value::contains)) score += 4
        if (listOf("δημος", "ααδε", "εφκα", "οργανισμος", "νοσοκομειο").any(value::contains)) score += 3
        if (listOf("gov.gr", "δεη", "τραπεζ").any(value::contains)) score += 2
        return score.takeIf { it > 0 }
    }

    private fun keywordScore(context: String, strongKeywords: List<String>, regularKeywords: List<String>): Int {
        val value = foldGreek(context)
        return when {
            strongKeywords.any { value.contains(foldGreek(it)) } -> STRONG_KEYWORD_SCORE
            regularKeywords.any { value.contains(foldGreek(it)) } -> REGULAR_KEYWORD_SCORE
            else -> 0
        }
    }

    private fun datelineIssuedScore(context: String): Int {
        val value = context.trim()
        if (!datelineContextRegex.matches(value)) return 0
        val folded = foldGreek(value).trim().trimEnd(':', ',')
        if (folded.isBlank()) return 0
        if (listOf(
                "ληξη",
                "εκδοση",
                "δημιουργ",
                "γεννη",
                "πρωτ",
                "μητρω",
                "προθεσ",
                "valid",
                "expiry",
                "issued",
                "created"
            ).any(folded::contains)
        ) return 0
        val words = folded.split(Regex("""\s+""")).filter(String::isNotBlank)
        return if (words.size in 1..4) DATELINE_SCORE else 0
    }

    /**
     * A label belongs to the date that follows it. For multiple dates on one
     * OCR line, only the text since the previous date is considered, so
     * "Δημιουργία: 01/01/2024 | Λήξη: 01/01/2025" maps each label correctly.
     * If the date starts its line, a label-only previous line is also accepted.
     */
    private fun labelContextBeforeDate(text: String, range: IntRange): String {
        val lineStart = text.lastIndexOf('\n', (range.first - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val linePrefix = text.substring(lineStart, range.first)
        val previousDate = dateRegex.findAll(linePrefix).lastOrNull()
        val localPrefix = if (previousDate == null) {
            linePrefix
        } else {
            linePrefix.substring(previousDate.range.last + 1)
        }
        if (localPrefix.any(Char::isLetterOrDigit)) return localPrefix.takeLast(LABEL_CONTEXT_LIMIT)
        if (lineStart == 0) return localPrefix.takeLast(LABEL_CONTEXT_LIMIT)

        val previousLineEnd = (lineStart - 1).coerceAtLeast(0)
        val previousLineStart = text.lastIndexOf('\n', (previousLineEnd - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val previousLine = text.substring(previousLineStart, previousLineEnd)
        return if (dateRegex.containsMatchIn(previousLine)) {
            localPrefix.takeLast(LABEL_CONTEXT_LIMIT)
        } else {
            (previousLine + " " + localPrefix).takeLast(LABEL_CONTEXT_LIMIT)
        }
    }

    private fun normalizeProtocol(raw: String): String? {
        val compact = raw.trim()
            .replace(Regex("""\s*([./_\-])\s*""")) { match -> match.groupValues[1] }
            .replace(Regex("""\s+"""), "")
        return compact.takeIf(::isValidProtocol)
    }

    private fun isValidProtocol(value: String): Boolean {
        val clean = value.trim('.', ':', '#', '-', '_', '/')
        return clean.length >= 3 && clean.any(Char::isDigit) && clean.all {
            it.isLetterOrDigit() || it == '.' || it == '/' || it == '_' || it == '-'
        }
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
    private data class TitleCandidate(val value: String, val score: Int, val index: Int)
    private data class ProviderCandidate(val value: String, val score: Int, val index: Int)
    private data class DateMatch(val canonical: String, val range: IntRange, val labelContext: String)
    private data class DateCandidate(
        val match: DateMatch,
        val issuedScore: Int,
        val issuedSource: String,
        val expiryScore: Int
    )

    private const val STRONG_TITLE_SCORE = 10
    private const val MIN_TITLE_SCORE = 6
    private const val STRONG_KEYWORD_SCORE = 5
    private const val REGULAR_KEYWORD_SCORE = 4
    private const val DATELINE_SCORE = 3
    private const val LABEL_CONTEXT_LIMIT = 120
    private const val MAX_METADATA_EVIDENCE_CHARS = 16_000
}
