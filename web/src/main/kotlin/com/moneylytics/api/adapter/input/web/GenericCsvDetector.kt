package com.moneylytics.api.adapter.input.web

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class GenericCsvDetector {
    companion object {
        private val DELIMITER_CANDIDATES = listOf(';', ',', '\t', '|')

        private val DATE_PATTERNS =
            listOf(
                "dd.MM.yyyy",
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd.MM.yy",
                "d.M.yyyy",
                "yyyy-MM-dd HH:mm:ss",
                "dd.MM.yyyy HH:mm:ss",
            )

        private val GERMAN_AMOUNT = Regex("""^-?[\d.]*\d,\d{1,2}$""")
        private val ENGLISH_AMOUNT = Regex("""^-?[\d,]*\d\.\d{1,2}$""")
    }

    fun detect(content: String): CsvDetectionResult {
        val delimiter = detectDelimiter(content)
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return empty(delimiter)

        val headers = splitLine(lines[0], delimiter)
        val dataLines = lines.drop(1).take(10)
        val sampleRows = dataLines.take(5).map { splitLine(it, delimiter) }

        val columnValues: Map<String, List<String>> =
            headers
                .mapIndexed { i, h ->
                    h to
                        dataLines.mapNotNull { line ->
                            val parts = splitLine(line, delimiter)
                            parts.getOrNull(i)?.trim()?.takeIf { it.isNotBlank() }
                        }
                }.toMap()

        val dateFormat = detectDateFormat(columnValues)
        val amountFormat = detectAmountFormat(columnValues)
        val suggestions = suggestColumns(headers, columnValues)

        return CsvDetectionResult(
            delimiter = delimiter.toString(),
            headers = headers,
            sampleRows = sampleRows,
            suggestions = suggestions,
            detectedDateFormat = dateFormat,
            detectedAmountFormat = amountFormat,
        )
    }

    private fun detectDelimiter(content: String): Char {
        val lines = content.lines().filter { it.isNotBlank() }.take(8)
        return DELIMITER_CANDIDATES.maxByOrNull { delimiter ->
            val counts = lines.map { it.split(delimiter).size }
            val avg = counts.average()
            if (avg <= 1.0) {
                0.0
            } else {
                val variance = counts.sumOf { (it - avg) * (it - avg) } / counts.size
                avg - variance * 0.5
            }
        } ?: ','
    }

    private fun splitLine(
        line: String,
        delimiter: Char,
    ): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun detectDateFormat(columnValues: Map<String, List<String>>): String? {
        for (pattern in DATE_PATTERNS) {
            val formatter =
                try {
                    DateTimeFormatter.ofPattern(pattern)
                } catch (_: Exception) {
                    continue
                }
            for ((_, values) in columnValues) {
                val nonBlank = values.filter { it.isNotBlank() }
                if (nonBlank.isEmpty()) continue
                val matches =
                    nonBlank.count { value ->
                        try {
                            LocalDate.parse(value.take(10), formatter)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                if (matches >= (nonBlank.size * 0.6).toInt() && matches > 0) return pattern
            }
        }
        return null
    }

    private fun detectAmountFormat(columnValues: Map<String, List<String>>): AmountFormat {
        var germanScore = 0
        var englishScore = 0
        for ((_, values) in columnValues) {
            for (v in values) {
                val t = v.trim()
                if (GERMAN_AMOUNT.matches(t)) germanScore++
                if (ENGLISH_AMOUNT.matches(t)) englishScore++
            }
        }
        return if (germanScore >= englishScore) AmountFormat.GERMAN else AmountFormat.ENGLISH
    }

    private fun suggestColumns(
        headers: List<String>,
        columnValues: Map<String, List<String>>,
    ): CsvColumnSuggestions {
        fun find(vararg hints: String) = headers.firstOrNull { h -> hints.any { hint -> h.lowercase().contains(hint) } }

        // Prefer the column whose values actually look like dates for the date suggestion
        val dateSuggestion =
            headers.firstOrNull { h ->
                val values = columnValues[h]?.filter { it.isNotBlank() } ?: return@firstOrNull false
                DATE_PATTERNS.any { pattern ->
                    val fmt =
                        try {
                            DateTimeFormatter.ofPattern(pattern)
                        } catch (_: Exception) {
                            return@any false
                        }
                    values.take(3).all { v ->
                        try {
                            LocalDate.parse(v.take(10), fmt)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            } ?: find("buchungst", "datum", "date", "valuta", "buchungs")

        val amountSuggestion =
            headers.firstOrNull { h ->
                val values = columnValues[h]?.filter { it.isNotBlank() } ?: return@firstOrNull false
                values.take(3).all { v ->
                    GERMAN_AMOUNT.matches(v.trim()) ||
                        ENGLISH_AMOUNT.matches(v.trim()) ||
                        v.trim().toBigDecimalOrNull() != null
                }
            } ?: find("betrag", "amount", "wert", "summe", "umsatz")

        return CsvColumnSuggestions(
            date = dateSuggestion,
            amount = amountSuggestion,
            currency = find("währung", "currency", "cur", "waehrung"),
            purpose =
                find(
                    "verwendung",
                    "zweck",
                    "purpose",
                    "buchungstext",
                    "beschreibung",
                    "text",
                    "description",
                    "note",
                    "memo",
                    "empfänger",
                    "auftraggeber",
                ),
            accountIban = find("iban", "konto", "account", "kontonummer"),
            category = find("kategorie", "category"),
            subcategory = find("unterkategorie", "subcategory"),
        )
    }

    private fun empty(delimiter: Char) =
        CsvDetectionResult(
            delimiter = delimiter.toString(),
            headers = emptyList(),
            sampleRows = emptyList(),
            suggestions = CsvColumnSuggestions(null, null, null, null, null, null, null),
            detectedDateFormat = null,
            detectedAmountFormat = AmountFormat.GERMAN,
        )
}
