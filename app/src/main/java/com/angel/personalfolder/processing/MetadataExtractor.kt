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
        // A protocol label may start its OCR line or appear in a parallel
        // right-hand column after a large horizontal gap. Requiring one of
        // those two positions avoids treating narrative legal citations as the
        // document's own protocol number. Mixed Greek/Latin initial letters are
        // accepted because OCR commonly confuses visually identical glyphs.
        """(?:^|[ \t]{2,})(?:(?:[ΑAαa]ριθ(?:μ(?:ός|ος)?)?\.?|[ΑAαa]ρ\.?)[ \t]*(?:[ΠPπp]ρωτ(?:οκ(?:ό|ο)λλου)?\.?)[ \t]*[:#№-]?[ \t]*([\p{L}\d](?:[\p{L}\d]|[ \t]*[./_\-][ \t]*[\p{L}\d]){1,79})|protocol(?:[ \t]+(?:no|number)\.?)?[ \t]*[:#№-]?[ \t]*([\p{L}\d](?:[\p{L}\d]|[ \t]*[./_\-][ \t]*[\p{L}\d]){1,79}))""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )
    private val datelineContextRegex = Regex("""^[\p{L}\p{M} .΄'’\-]{2,50}[:,]\s*$""")
    private val schoolProviderAnchor = Regex(
        """(?<![\p{L}\d])\d{1,3}\s*(?:ο|ου|o|0)?\s*(?:Γυμνάσιο|Γυμνασίου|Γυμνασιο|Γυμνασιου|Λύκειο|Λυκείου|Λυκειο|Λυκειου|(?:Δημοτικό|Δημοτικού|Δημοτικο|Δημοτικου)\s*(?:Σχολείο|Σχολείου|Σχολειο|Σχολειου))(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )
    private val schoolProviderStop = Regex(
        """\s+(?:με|που|όπου|όπως|για)\s+|[,;|]""",
        RegexOption.IGNORE_CASE
    )
    private val documentTypeLabelRegex = Regex(
        """^(?:ΕΙΔΟΣ\s+ΠΑΡΑΣΤΑΤΙΚΟΥ|ΤΥΠΟΣ\s+ΕΓΓΡΑΦΟΥ|DOCUMENT\s+TYPE)\s*[:\-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val providerParallelFieldRegex = Regex(
        """(?:εδρα\s*:|αρ\.?\s*αδειας\s*:|αρ\.?\s*πρωτ(?:\.|οκ(?:ολλου)?)?\s*:|αριθ\.?\s*πρωτ(?:\.|οκ(?:ολλου)?)?\s*:|αρ\.?\s*φακελου\s*:|ε\.?\s*κ\.?\s*α\.?\s*:|αρ\.?\s*ταυτ\.?\s*στοιχειου\s*:|αριθμος\s+βεβαιωσης\s*:|κωδικος\s+ηλεκτρονικης\s+πληρωμης|iban\s*:?)"""
    )
    private val bankPairRegex = Regex(
        """\b([\p{L}][\p{L}\d._-]{2,})\s+bank\b""",
        RegexOption.IGNORE_CASE
    )
    private val compoundBankRegex = Regex(
        """\b[\p{L}\d._-]{3,}bank\b""",
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

    fun extract(text: String, fallbackTitle: String, supplementalText: String = ""): ExtractedMetadata {
        val primaryText = text.replace("\u0000", " ").trim()
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

        val titleCandidate = lines.mapIndexedNotNull { index, line ->
            val candidateValue = extractSpecificTitle(line)
            titleScore(candidateValue, index)?.let { score -> TitleCandidate(candidateValue.take(100), score, index) }
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

        val providerCandidate = lines.mapIndexedNotNull { index, _ ->
            val candidateValue = extractSpecificProvider(lines, index)
            providerScore(candidateValue)?.let { score -> ProviderCandidate(candidateValue.take(MAX_PROVIDER_LENGTH), score, index) }
        }.maxWithOrNull(compareBy<ProviderCandidate> { it.score }.thenBy { -it.index })
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
                DateMatch(canonical = canonical, range = match.range, labelContext = labelContextBeforeDate(evidenceText, match.range))
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

        val issuedCandidate = dateCandidates.filter { it.issuedScore > 0 }
            .maxWithOrNull(compareBy<DateCandidate> { it.issuedScore }.thenBy { -it.match.range.first })
        val expiryCandidate = dateCandidates.filter { it.expiryScore > 0 }
            .maxWithOrNull(compareBy<DateCandidate> { it.expiryScore }.thenBy { it.match.range.first })

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

        val protocolMatch = protocolRegex.find(evidenceText)
        val protocol = protocolMatch?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank)?.let(::normalizeProtocol)
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

    private fun extractSpecificTitle(line: String): String {
        documentTypeLabelRegex.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        val pipeParts = line.split('|').map(String::trim).filter(String::isNotBlank)
        if (pipeParts.size > 1 && containsDocumentTitleMarker(pipeParts.first())) return pipeParts.first()
        return line
    }

    private fun containsDocumentTitleMarker(value: String): Boolean {
        val folded = foldGreek(value)
        return STRONG_TITLE_TERMS.any(folded::contains) || GENERIC_TITLE_TERMS.any(folded::contains)
    }

    private fun extractSpecificProvider(lines: List<String>, index: Int): String {
        val clean = stripParallelProviderFields(lines[index])
        if (clean.isBlank()) return ""
        val folded = foldGreek(clean)

        if (folded.contains("μοναδα υγειας")) {
            val colon = clean.indexOf(':')
            val base = if (colon >= 0) clean.substring(colon + 1).trim() else clean
            return joinProviderContinuations(lines, index, base, 2)
        }

        val migrationStart = folded.indexOf("διευθυνση αλλοδαπων")
        if (migrationStart >= 0) {
            val base = clean.substring(migrationStart).trim()
            return joinProviderContinuations(lines, index, base, 3)
        }

        schoolProviderAnchor.find(clean)?.let { anchor ->
            val candidate = clean.substring(anchor.range.first)
            val stop = schoolProviderStop.find(candidate, anchor.value.length)?.range?.first ?: candidate.length
            return candidate.substring(0, stop).replace(Regex("""\s+"""), " ").trim()
        }

        if (folded.contains("ληξιαρχειο")) return clean

        if (PROVIDER_LEGAL_ENTITY_TERMS.any(folded::contains)) {
            val parts = mutableListOf<String>()
            if (index > 0) {
                val previous = stripParallelProviderFields(lines[index - 1])
                if (isUppercaseProviderContinuation(previous) &&
                    PROVIDER_HEADER_NOISE.none(foldGreek(previous)::contains)
                ) {
                    parts += previous
                }
            }
            parts += clean
            return parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        }

        extractBankBrand(clean)?.let { return it }
        return clean
    }

    private fun joinProviderContinuations(lines: List<String>, index: Int, base: String, maxNextLines: Int): String {
        val parts = mutableListOf<String>()
        if (base.isNotBlank()) parts += base
        var consumed = 0
        var cursor = index + 1
        while (cursor < lines.size && consumed < maxNextLines) {
            val candidate = stripParallelProviderFields(lines[cursor])
            if (!isUppercaseProviderContinuation(candidate)) break
            parts += candidate
            consumed += 1
            cursor += 1
        }
        return parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
    }

    private fun stripParallelProviderFields(line: String): String {
        val folded = foldGreek(line)
        val cutAt = providerParallelFieldRegex.find(folded)?.range?.first ?: line.length
        return line.take(cutAt)
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|', '·')
    }

    private fun isUppercaseProviderContinuation(line: String): Boolean {
        if (line.isBlank() || line.length > 90 || line.contains(':')) return false
        val folded = foldGreek(line)
        if (PROVIDER_HEADER_NOISE.any(folded::contains)) return false
        val letters = line.filter(Char::isLetter)
        if (letters.isEmpty()) return false
        val uppercase = letters.count(Char::isUpperCase)
        return uppercase.toDouble() / letters.length >= 0.75
    }

    private fun extractBankBrand(line: String): String? {
        bankPairRegex.find(line)?.let { match ->
            val first = match.groupValues.getOrNull(1).orEmpty()
            if (first.isNotBlank()) return "$first Bank"
        }
        return compoundBankRegex.findAll(line)
            .map { it.value.removePrefix("www.").removePrefix("WWW.") }
            .firstOrNull { value -> foldGreek(value) !in setOf("bank", "banking", "ebanking", "e-banking") }
    }

    private fun titleScore(line: String, index: Int): Int? {
        val value = foldGreek(line)
        if (value.isBlank()) return null
        var score = 0
        when {
            STRONG_TITLE_TERMS.any(value::contains) -> score += 12
            GENERIC_TITLE_TERMS.any(value::contains) -> score += 6
        }
        if (value == "αποφαση" || value == "αιτηση - αναφορα" || value == "αιτηση-αναφορα") score += 8
        if (TITLE_METADATA_NOISE.any(value::contains)) score -= 8
        if (listOf(
                "ελληνικη δημοκρατια",
                "hellenic republic",
                "υπουργειο",
                "ministry",
                "περιφερειακη διευθυνση",
                "γενικη γραμματεια"
            ).any(value::contains)
        ) score -= 10
        if (line.length in 5..80) score += 1
        if (index < 30) score += 1
        return score.takeIf { it >= MIN_TITLE_SCORE }
    }

    private fun providerScore(line: String): Int? {
        val value = foldGreek(line)
        if (value.isBlank()) return null
        if (PROVIDER_NON_ISSUER_TERMS.any(value::contains)) return null
        if (value.contains("ελληνικη δημοκρατια") || value == "δημοκρατια") return 1
        if (value.contains("διευθυνση αλλοδαπων") && (value.contains("μεταναστε") || value.contains("migration"))) return 12
        if (value.contains("ληξιαρχειο")) return 11
        if (value.contains("μοναδα ψυχικης υγειας") || value.contains("νοσηλευτικη") || value.contains("νοσηλευτικ")) return 10
        if (PROVIDER_LEGAL_ENTITY_TERMS.any(value::contains)) return 10
        if (schoolProviderAnchor.containsMatchIn(line)) return 9
        if (extractBankBrand(line) != null || value.startsWith("τραπεζα ")) return 8
        if (listOf("υπουργειο", "ministry").any(value::contains)) return 6
        if (listOf("διευθυνση", "υπηρεσια", "γενικη γραμματεια", "directorate").any(value::contains)) return 5
        if (listOf("δημος", "ααδε", "εφκα", "οργανισμος", "νοσοκομειο").any(value::contains)) return 4
        if (listOf("gov.gr", "δεη").any(value::contains)) return 3
        if (listOf("γυμνασι", "λυκει", "σχολει").any(value::contains)) return 2
        return null
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
                "ληξη", "εκδοση", "δημιουργ", "γεννη", "πρωτ", "μητρω", "προθεσ",
                "valid", "expiry", "issued", "created"
            ).any(folded::contains)
        ) return 0
        val words = folded.split(Regex("""\s+""")).filter(String::isNotBlank)
        return if (words.size in 1..4) DATELINE_SCORE else 0
    }

    private fun labelContextBeforeDate(text: String, range: IntRange): String {
        val lineStart = text.lastIndexOf('\n', (range.first - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val linePrefix = text.substring(lineStart, range.first)
        val previousDate = dateRegex.findAll(linePrefix).lastOrNull()
        val localPrefix = if (previousDate == null) linePrefix else linePrefix.substring(previousDate.range.last + 1)
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
    private data class DateCandidate(val match: DateMatch, val issuedScore: Int, val issuedSource: String, val expiryScore: Int)

    private val STRONG_TITLE_TERMS = listOf(
        "βεβαιωση σπουδων",
        "βεβαιωση φοιτησης",
        "ιατρικη βεβαιωση",
        "πιστοποιητικο σπουδων",
        "ληξιαρχικη πραξη γεννησης",
        "δελτιο παραγγελιας",
        "καθημερινος λογαριασμος",
        "τραπεζικος λογαριασμος",
        "account statement",
        "bank statement",
        "e-statement",
        "residence permit",
        "residence card"
    )
    private val GENERIC_TITLE_TERMS = listOf(
        "βεβαιωση", "πιστοποιητικο", "αδεια", "αποφαση", "αιτηση", "συμβαση", "γνωματευση", "παραστατικο",
        "certificate", "permit", "statement"
    )
    private val TITLE_METADATA_NOISE = listOf(
        "αρ. αδειας", "αρ αδειας", "αριθμος αδειας", "κωδικος εγγραφου", "κωδικος ηλεκτρονικης πληρωμης",
        "στοιχεια πελατη", "στοιχεια παραστατικου", "στοιχεια πολιτη", "στοιχεια ιατρου", "iban", "δικαιουχοι"
    )
    private val PROVIDER_LEGAL_ENTITY_TERMS = listOf(
        "ανωνυμη εταιρεια",
        "μονοπροσωπη ανωνυμη",
        "μονοπροσωπη αε"
    )
    private val PROVIDER_NON_ISSUER_TERMS = listOf(
        "διευθυνση κατοικιας",
        "ηλεκτρονικη σας διευθυνση",
        "ταχυδρομικη διευθυνση",
        "διευθυνση email"
    )
    private val PROVIDER_HEADER_NOISE = listOf(
        "αρ. αδειας",
        "αρ αδειας",
        "αρ.πρωτ",
        "αρ πρωτ",
        "αρ. φακελου",
        "αρ φακελου",
        "στοιχεια",
        "ημερομηνια",
        "κωδικος",
        "τηλ.",
        "ταχ.δ/νση"
    )

    private const val STRONG_TITLE_SCORE = 10
    private const val MIN_TITLE_SCORE = 6
    private const val STRONG_KEYWORD_SCORE = 5
    private const val REGULAR_KEYWORD_SCORE = 4
    private const val DATELINE_SCORE = 3
    private const val LABEL_CONTEXT_LIMIT = 120
    private const val MAX_PROVIDER_LENGTH = 180
    private const val MAX_METADATA_EVIDENCE_CHARS = 16_000
}
