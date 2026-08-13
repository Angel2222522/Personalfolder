package com.angel.personalfolder.processing

import com.angel.personalfolder.data.MetadataConfidence
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/**
 * Refines structural metadata patterns that are difficult to resolve from a
 * single OCR line. The rules stay conservative: they may promote explicit
 * document evidence, but they never infer a date from an unrelated number.
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

        val refined = metadata.copy(
            issuedDate = issuedDate,
            expiryDate = expiryDate,
            issuedConfidence = issuedConfidence,
            expiryConfidence = expiryConfidence,
            issuedProvenance = issuedProvenance,
            expiryProvenance = expiryProvenance
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
    private val NON_EXPIRING_REFERENCE_DOCUMENT_TERMS = listOf(
        "αιτηση",
        "αναφορα",
        "application",
        "request"
    )

    private const val TWO_DIGIT_YEAR_PIVOT = 69
    private const val MAX_REFINEMENT_EVIDENCE_CHARS = 16_000
}
