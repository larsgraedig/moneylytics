package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.Transaction
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Component
class GenericCsvParser {
    companion object {
        private const val DATE_PARSE_PREFIX_LENGTH = 10
    }

    fun preview(
        content: String,
        mapping: GenericCsvMapping,
    ): List<GenericCsvPreviewRow> {
        val delimiter = mapping.delimiter.firstOrNull() ?: ','
        val dateFormatter = DateTimeFormatter.ofPattern(mapping.dateFormat)
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headers = splitLine(lines[0], delimiter).map { it.trim() }

        fun idx(col: String?) = col?.let { headers.indexOf(it).takeIf { i -> i >= 0 } }

        val dateIdx = idx(mapping.dateColumn) ?: return emptyList()
        val amountIdx = idx(mapping.amountColumn) ?: return emptyList()
        val purposeIdx = idx(mapping.purposeColumn)
        val categoryIdx = idx(mapping.categoryColumn)
        val groupIdx = idx(mapping.groupColumn)
        val subcategoryIdx = idx(mapping.subcategoryColumn)
        val ibanIdx = idx(mapping.accountIbanColumn)
        val currencyIdx = idx(mapping.currencyColumn)
        val counterpartyNameIdx = idx(mapping.counterpartyNameColumn)
        val counterpartyIbanIdx = idx(mapping.counterpartyIbanColumn)

        val counts = mutableMapOf<String, Int>()
        return lines.drop(1).mapIndexedNotNull { index, line ->
            val parts = splitLine(line, delimiter)

            fun get(i: Int?) = i?.let { parts.getOrNull(it)?.trim() } ?: ""

            val date =
                try {
                    LocalDate.parse(get(dateIdx).take(DATE_PARSE_PREFIX_LENGTH), dateFormatter)
                } catch (e: Exception) {
                    logger.warn(e) { "Skipping CSV row ${index + 2}: cannot parse date '${get(dateIdx).take(DATE_PARSE_PREFIX_LENGTH)}'" }
                    return@mapIndexedNotNull null
                }
            val amountDecimal = parseAmount(get(amountIdx), mapping.amountFormat)
            if (amountDecimal == null) {
                logger.warn { "Skipping CSV row ${index + 2}: cannot parse amount '${get(amountIdx)}'" }
                return@mapIndexedNotNull null
            }
            val iban = ibanIdx?.let { get(it).ifBlank { null } } ?: mapping.fixedAccountIban?.ifBlank { null } ?: "IMPORTED"
            val currency = currencyIdx?.let { get(it).ifBlank { null } } ?: mapping.fixedCurrency.ifBlank { "EUR" }

            val raw = "$iban|$date|$date|${amountDecimal.stripTrailingZeros().toPlainString()}|$currency"
            val n = (counts.getOrDefault(raw, 0) + 1).also { counts[raw] = it }
            val fingerprint = sha256(if (n == 1) raw else "$raw:${n - 1}")

            GenericCsvPreviewRow(
                rowIndex = index + 2,
                date = date.toString(),
                amount = amountDecimal.toDouble(),
                currency = currency,
                accountIban = iban,
                purpose = get(purposeIdx).ifBlank { null },
                fingerprint = fingerprint,
                status = RowStatus.NEW,
                unknownAccount = false,
                mappedCategory = get(categoryIdx).ifBlank { null },
                mappedGroup = get(groupIdx).ifBlank { null },
                mappedSubcategory = get(subcategoryIdx).ifBlank { null },
                counterpartyName = get(counterpartyNameIdx).ifBlank { null },
                counterpartyIban = get(counterpartyIbanIdx).ifBlank { null },
            )
        }
    }

    fun parse(
        content: String,
        mapping: GenericCsvMapping,
    ): Pair<List<Transaction>, Map<String, String>> {
        val delimiter = mapping.delimiter.firstOrNull() ?: ','
        val dateFormatter = DateTimeFormatter.ofPattern(mapping.dateFormat)
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList<Transaction>() to emptyMap()

        val headers = splitLine(lines[0], delimiter).map { it.trim() }

        fun idx(col: String?) = col?.let { headers.indexOf(it).takeIf { i -> i >= 0 } }

        val dateIdx = idx(mapping.dateColumn) ?: return emptyList<Transaction>() to emptyMap()
        val amountIdx = idx(mapping.amountColumn) ?: return emptyList<Transaction>() to emptyMap()
        val purposeIdx = idx(mapping.purposeColumn)
        val categoryIdx = idx(mapping.categoryColumn)
        val groupIdx = idx(mapping.groupColumn)
        val subcategoryIdx = idx(mapping.subcategoryColumn)
        val ibanIdx = idx(mapping.accountIbanColumn)
        val currencyIdx = idx(mapping.currencyColumn)
        val counterpartyNameIdx = idx(mapping.counterpartyNameColumn)
        val counterpartyIbanIdx = idx(mapping.counterpartyIbanColumn)

        val transactions = mutableListOf<Transaction>()
        val accountNames = mutableMapOf<String, String>()

        for (line in lines.drop(1)) {
            val parts = splitLine(line, delimiter)

            fun get(i: Int?) = i?.let { parts.getOrNull(it)?.trim() } ?: ""

            val dateStr = get(dateIdx)
            val amountStr = get(amountIdx)

            val date =
                try {
                    LocalDate.parse(dateStr.take(DATE_PARSE_PREFIX_LENGTH), dateFormatter)
                } catch (_: Exception) {
                    null
                }
            val amount = parseAmount(amountStr, mapping.amountFormat)

            if (date == null || amount == null) {
                logger.warn { "Skipping CSV row: date='$dateStr', amount='$amountStr'" }
                continue
            }

            val iban =
                ibanIdx?.let { get(it).ifBlank { null } }
                    ?: mapping.fixedAccountIban?.ifBlank { null }
                    ?: "IMPORTED"
            val currency =
                currencyIdx?.let { get(it).ifBlank { null } }
                    ?: mapping.fixedCurrency.ifBlank { "EUR" }

            accountNames.putIfAbsent(iban, iban)

            transactions.add(
                Transaction(
                    category = get(categoryIdx).ifBlank { "Sonstiges" },
                    subcategory = get(subcategoryIdx).ifBlank { null },
                    group = get(groupIdx).ifBlank { "Sonstiges" },
                    bookingDate = date,
                    valueDate = date,
                    accountingDate = date,
                    amount = amount,
                    currency = currency,
                    accountIban = iban,
                    purpose = get(purposeIdx).ifBlank { null },
                    counterpartyName = get(counterpartyNameIdx).ifBlank { null },
                    counterpartyIban = get(counterpartyIbanIdx).ifBlank { null },
                ),
            )
        }

        return transactions to accountNames
    }

    private fun parseAmount(
        value: String,
        format: AmountFormat,
    ): BigDecimal? {
        if (value.isBlank()) return null
        val normalized =
            when (format) {
                AmountFormat.GERMAN -> value.replace(".", "").replace(",", ".")
                AmountFormat.ENGLISH -> value.replace(",", "")
            }
        return normalized.toBigDecimalOrNull()
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
}
