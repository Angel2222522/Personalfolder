package com.angel.personalfolder.processing

import com.angel.personalfolder.data.MetadataConfidence
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/**
 * Refines structural metadata patterns that are difficult to resolve from a
 * single OCR line. The rules stay conservative: they may promote explicit
 * document evidence, but they never infer a field from unrelated numbers.
 */
object MetadataEvidenceRefiner {
    fun refine(metadata: ExtractedMetadata, primaryText: String): ExtractedMetadata {
        val evidence = primaryText.replace("\u0000", " ").take(MAX_REFINEMENT_EVIDENCE_CHARS)
        val foldedTitle = fold(metadata.title)
        val isReferenceApplication = NON_EXPIRING_REFERENCE_DOCUMENT_TERMS.any(foldedTitle::contains)

        val shortIssued = if (metadata.issuedDate == null) extractExplicitShortIssueDate(evidence) else null
        val issuedDate = metadata.issuedDate ?: shortIssued
        val issuedConfidence = if (metadata.issuedDate != null) {
            metadata.issuedConfidence
        } else if (shortIssued != null) {
            MetadataConfidence.HIGH
        } else {
            metadata.issuedConfidence
        }
        val issuedProvenance = if (metadata.issuedDate != null) {
            metadata.issuedProvenance
        } else if (shortIssued != null) {
            "issued-explicit-short-date"
        } else {
            metadata.issuedProvenance
        }

        val validityExpiry = if (!isReferenceApplication && metadata.expiryDate == null) {
            extractExplicitValidityRangeExpiry(evidence)
        } else {
            null
        }
        val expiryDate = when {
            isReferenceApplication -> null
            metadata.expiryDate != null -> metadata.expiryDate
            else -> validityExpiry
        }
        val expiryConfidence = when {
            isReferenceApplication -> MetadataConfidence.NONE
            metadata.expiryDate != null -> metadata.expiryConfidence
            validityExpiry != null -> MetadataConfidence.HIGH
            else -> metadata.expiryConfidence
        }
        val expiryProvenance = when {
            isReferenceApplication && metadata.expiryDate != null -> "suppressed-referenced-expiry"
            isReferenceApplication -> "none"
            metadata.expiryDate != null -> metadata.expiryProvenance
            validityExpiry != null -> "expiry-validity-range"
            else -> metadata.expiryProvenance
        }

        val stackedProtocol = if (metadata.protocolNumber == null) extractStackedProtocol(evidence) else null
        val protocolNumber = metadata.protocolNumber ?: stackedProtocol
        val protocolConfidence = if (metadata.protocolNumber != null) {
            metadata.protocolConfidence
        } else if (stackedProtocol != null) {
            MetadataConfidence.MEDIUM
        } else {
            metadata.protocolConfidence
        }
        val protocolProvenance = if (metadata.protocolNumber != null) {
            metadata.protocolProvenance
        } else if (stackedProtocol != null) {
            "protocol-stacked-field"
        } else {
            metadata.protocolProvenance
        }

        // Some registry exports flatten a header table into separate labels and
        // values, leaving the raw issuer as only "ΛΗΞΙΑΡΧΕΙΟ". In that narrow
        // case, recover a nearby all-uppercase office qualifier after the birth
        // record heading. This is structural evidence, not a place-name list.
        val registryQualifier = if (fold(metadata.provider).trim() == "ληξιαρχειο") {
            extractRegistryOfficeQualifier(evidence, metadata.title)
        } else {
            null
        }
        val provider = registryQualifier?.let { "${metadata.provider.trim()} $it" } ?: metadata.provider
        val providerConfidence = if (registryQualifier != null) MetadataConfidence.HIGH else metadata.providerConfidence
        val providerProvenance = if (registryQualifier != null) "registry-office-layout" else metadata.providerProvenance

        // A bank account statement is financial, not a utility bill merely
        // because its title contains the generic word "λογαριασμός". Require a
        // bank issuer plus account/statement evidence before promoting it.
        val financialAccountStatement = shouldPromoteFinancialAccountCategory(provider, metadata.title, evidence)
        val category = if (financialAccountStatement) "Οικονομικά" else metadata.category
        val categoryConfidence = if (financialAccountStatement) MetadataConfidence.HIGH else metadata.categoryConfidence

        val refined = metadata.copy(
            category = category,
            provider = provider,
            issuedDate = issuedDate,
            expiryDate = expiryDate,
            protocolNumber = protocolNumber,
            categoryConfidence = categoryConfidence,
            providerConfidence = providerConfidence,
            issuedConfidence = issuedConfidence,
            expiryConfidence = expiryConfidence,
            protocolConfidence = protocolConfidence,
            providerProvenance = providerProvenance,
            issuedProvenance = issuedProvenance,
            expiryProvenance = expiryProvenance,
            protocolProvenance = protocolProvenance
        )
        return refined.copy(json = buildJson(refined))
    }

    private fun extractExplicitShortIssueDate(text: String): String? {
        val folded = fold(text)
        return shortIssueDateRegex.find(folded)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::normalizeShortDate)
    }

    private fun extractExplicitValidityRangeExpiry(text: String): String? {
        val folded = fold(text)
        val match = validityRangeRegex.find(folded) ?: return null
        return match.groupValues.getOrNull(2)?.let(::normalizeFourDigitDate)
    }

    private fun extractStackedProtocol(text: String): String? {
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val foldedLines = lines.map(::fold)
        for (index in lines.indices) {
            if (!stackedProtocolLabelRegex.matches(foldedLines[index])) continue
            for (offset in 1..MAX_STACKED_PROTOCOL_LOOKAHEAD) {
                val candidate = lines.getOrNull(index + offset) ?: break
                val foldedCandidate = foldedLines[index + offset]
                if (parallelMetadataLabelRegex.matches(foldedCandidate)) continue
                if (fourDigitDateWholeLineRegex.matches(candidate)) continue
                normalizeProtocolCandidate(candidate)?.let { return it }
            }
        }
        return null
    }

    private fun extractRegistryOfficeQualifier(text: String, title: String): String? {
        val lines = text.lines().map(String::trim).filter { it.length >= 2 }
        if (lines.isEmpty()) return null
        val foldedTitle = fold(title)
        val titleIndex = lines.indexOfFirst { line ->
            val foldedLine = fold(line)
            foldedLine.contains("ληξιαρχικη πραξη γεννησης") ||
                (foldedTitle.isNotBlank() && foldedLine == foldedTitle)
        }
        if (titleIndex < 0) return null
        return lines.asSequence()
            .drop(titleIndex + 1)
            .take(MAX_REGISTRY_QUALIFIER_LOOKAHEAD)
            .firstOrNull(::isRegistryOfficeQualifier)
    }

    private fun isRegistryOfficeQualifier(value: String): Boolean {
        if (value.length !in MIN_REGISTRY_QUALIFIER_LENGTH..MAX_REGISTRY_QUALIFIER_LENGTH) return false
        if (value.any(Char::isDigit) || ':' in value || '/' in value || '@' in value) return false
        val letters = value.filter(Char::isLetter)
        if (letters.length < MIN_REGISTRY_QUALIFIER_LETTERS) return false
        val uppercaseRatio = letters.count(Char::isUpperCase).toDouble() / letters.length
        if (uppercaseRatio < MIN_REGISTRY_UPPERCASE_RATIO) return false
        val folded = fold(value)
        if (REGISTRY_QUALIFIER_NOISE.any(folded::contains)) return false
        return true
    }

    private fun shouldPromoteFinancialAccountCategory(provider: String, title: String, text: String): Boolean {
        val foldedProvider = fold(provider)
        val foldedTitle = fold(title)
        val foldedText = fold(text)
        val bankIssuer = foldedProvider.contains("bank") || foldedProvider.contains("τραπεζ")
        if (!bankIssuer) return false
        val accountHeading = foldedTitle.contains("λογαριασμ") || foldedTitle.contains("statement")
        val bankStructure = foldedText.contains("iban") && foldedText.contains("bic")
        return accountHeading || bankStructure
    }

    private fun normalizeProtocolCandidate(raw: String): String? {
        val compact = raw.trim()
            .replace(Regex("""\s*([./_\-])\s*""")) { match -> match.groupValues[1] }
            .replace(Regex("""\s+"""), "")
            .trim('.', ':', '#', '-', '_', '/')
        if (compact.length !in MIN_PROTOCOL_LENGTH..MAX_PROTOCOL_LENGTH) return null
        if (compact.none(Char::isDigit)) return null
        if (compact.any { !it.isLetterOrDigit() && it !in ". /_-" }) return null
        return compact
    }

    private fun normalizeShortDate(raw: String): String? {
        val parts = raw.replace('.', '/').replace('-', '/').split('/')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val shortYear = parts[2].toIntOrNull() ?: return null
        val year = if (shortYear <= TWO_DIGIT_YEAR_PIVOT) 2000 + shortYear else 1900 + shortYear
        return localDateOrNull(year, month, day)?.toString()
    }

    private fun normalizeFourDigitDate(raw: String): String? {
        val parts = raw.replace('.', '/').replace('-', '/').split('/')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return localDateOrNull(year, month, day)?.toString()
    }

    private fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    private fun buildJson(metadata: ExtractedMetadata): String = buildString {
        append('{')
        append("\"title\":\"").append(jsonEscape(metadata.title)).append("\",")
        append("\"category\":\"").append(jsonEscape(metadata.category)).append("\",")
        append("\"provider\":\"").append(jsonEscape(metadata.provider)).append("\",")
        append("\"issuedDate\":").append(jsonValue(metadata.issuedDate)).append(',')
        append("\"expiryDate\":").append(jsonValue(metadata.expiryDate)).append(',')
        append("\"protocolNumber\":").append(jsonValue(metadata.protocolNumber)).append(',')
        append("\"keywords\":\"").append(jsonEscape(metadata.keywords.joinToString(","))).append("\",")
        append("\"titleConfidence\":\"").append(metadata.titleConfidence).append("\",")
        append("\"categoryConfidence\":\"").append(metadata.categoryConfidence).append("\",")
        append("\"providerConfidence\":\"").append(metadata.providerConfidence).append("\",")
        append("\"issuedConfidence\":\"").append(metadata.issuedConfidence).append("\",")
        append("\"expiryConfidence\":\"").append(metadata.expiryConfidence).append("\",")
        append("\"protocolConfidence\":\"").append(metadata.protocolConfidence).append("\",")
        append("\"issuedProvenance\":\"").append(jsonEscape(metadata.issuedProvenance)).append("\",")
        append("\"expiryProvenance\":\"").append(jsonEscape(metadata.expiryProvenance)).append("\",")
        append("\"providerProvenance\":\"").append(jsonEscape(metadata.providerProvenance)).append("\",")
        append("\"protocolProvenance\":\"").append(jsonEscape(metadata.protocolProvenance)).append("\",")
        append("\"confidence\":\"suggested\"")
        append('}')
    }

    private fun jsonValue(value: String?): String = value?.let { "\"${jsonEscape(it)}\"" } ?: "null"

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

    private fun fold(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")

    private val shortIssueDateRegex = Regex(
        """(?:ημερ\.?\s*εκδοσης|ημ/νια\s*εκδοσης|ημερομηνια\s*εκδοσης|issue\s+date)\s*[:\-]?\s*(\d{1,2}[./-]\d{1,2}[./-]\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE
    )
    private val validityRangeRegex = Regex(
        """(?:ισχυος|ισχυει|ισχυς)\s+(?:απο\s+)?(\d{1,2}[./-]\d{1,2}[./-]\d{4})\s+(?:εως|μεχρι)\s+(\d{1,2}[./-]\d{1,2}[./-]\d{4})""",
        RegexOption.IGNORE_CASE
    )
    private val stackedProtocolLabelRegex = Regex(
        """^(?:(?:[αa]ριθ(?:μ(?:ος)?)?\.?|[αa]ρ\.?)\s*πρωτ(?:οκ(?:ολλου)?)?\.?)\s*:\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val parallelMetadataLabelRegex = Regex(
        """^(?:[αa]ρ\.?\s*φακελου|ε\.?\s*κ\.?\s*α\.?|[αa]ρ\.?\s*αδειας|ημερομηνια|ημ/νια)\s*:?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val fourDigitDateWholeLineRegex = Regex(
        """^\d{1,2}[./-]\d{1,2}[./-]\d{4}$"""
    )
    private val NON_EXPIRING_REFERENCE_DOCUMENT_TERMS = listOf(
        "αιτηση",
        "αναφορα",
        "application",
        "request"
    )
    private val REGISTRY_QUALIFIER_NOISE = listOf(
        "στοιχεια",
        "χαρακτηριστικο",
        "κωδικος",
        "τηλεφωνο",
        "σελιδα",
        "ελληνικη δημοκρατια",
        "ληξιαρχειο",
        "δημος",
        "νομος",
        "διευθυνση",
        "δ/νση"
    )

    private const val TWO_DIGIT_YEAR_PIVOT = 69
    private const val MAX_REFINEMENT_EVIDENCE_CHARS = 16_000
    private const val MAX_STACKED_PROTOCOL_LOOKAHEAD = 10
    private const val MIN_PROTOCOL_LENGTH = 3
    private const val MAX_PROTOCOL_LENGTH = 80
    private const val MAX_REGISTRY_QUALIFIER_LOOKAHEAD = 10
    private const val MIN_REGISTRY_QUALIFIER_LENGTH = 3
    private const val MAX_REGISTRY_QUALIFIER_LENGTH = 50
    private const val MIN_REGISTRY_QUALIFIER_LETTERS = 3
    private const val MIN_REGISTRY_UPPERCASE_RATIO = 0.75
}
