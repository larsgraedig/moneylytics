package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.AccountBalance
import com.moneylytics.api.domain.Transaction
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Component
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class CsvTransactionParser {
    fun parse(csvContent: String): CsvParseResult {
        val apacheFormat =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()

        val csvParser = CSVParser.parse(StringReader(csvContent), apacheFormat)
        val headers = csvParser.headerNames.map { it.trim() }.toSet()

        val matchedFormat =
            CsvFormat.entries.firstOrNull { fmt ->
                fmt.config.requiredColumns.all { it in headers }
            } ?: return unrecognizedFormatError(headers)

        return parseWithFormat(csvParser, matchedFormat.config, headers)
    }

    private fun unrecognizedFormatError(headers: Set<String>): CsvParseResult.Invalid {
        // Report the missing columns of the closest-matching known format so the
        // error message is actionable (e.g. "Missing required columns for MLP Banking: Valutadatum").
        val bestMatch =
            CsvFormat.entries.maxBy { fmt ->
                fmt.config.requiredColumns.count { it in headers }
            }
        val missing = bestMatch.config.requiredColumns - headers
        return CsvParseResult.Invalid(
            listOf(
                CsvValidationError(
                    row = 0,
                    column = missing.joinToString(", "),
                    value = "",
                    message = "Missing required columns for ${bestMatch.config.name}: ${missing.joinToString(", ")}",
                ),
            ),
        )
    }

    private fun parseWithFormat(
        csvParser: CSVParser,
        config: CsvFormatConfig,
        headers: Set<String>,
    ): CsvParseResult {
        val dateFormatter = DateTimeFormatter.ofPattern(config.datePattern)
        val transactions = mutableListOf<Transaction>()
        val accountNames = mutableMapOf<String, String>()
        val errors = mutableListOf<CsvValidationError>()
        val hasAccountNameColumn = config.accountName != null && config.accountName in headers
        val hasPurposeColumn = config.purpose != null && config.purpose in headers
        val hasCategoryColumn = config.category != null && config.category in headers
        val hasSubcategoryColumn = config.subcategory != null && config.subcategory in headers
        val hasAccountBalanceColumn = config.accountBalance != null && config.accountBalance in headers
        // tracks the latest balance seen per IBAN: iban → (bookingDate, balance)
        val latestBalanceByIban = mutableMapOf<String, Pair<LocalDate, BigDecimal>>()

        for ((index, record) in csvParser.withIndex()) {
            val rowNumber = index + 2 // header is row 1, data starts at row 2

            val bookingDate =
                parseDate(record[config.bookingDate], config.bookingDate, rowNumber, dateFormatter, config.datePattern, errors)
            val valueDate = parseDate(record[config.valueDate], config.valueDate, rowNumber, dateFormatter, config.datePattern, errors)
            val amount = parseAmount(record[config.amount], config.amount, rowNumber, errors)

            if (bookingDate != null && valueDate != null && amount != null) {
                val accountIban = record[config.accountIban]
                val accountName = if (hasAccountNameColumn) record[config.accountName!!] else accountIban
                accountNames[accountIban] = accountName
                val purpose = if (hasPurposeColumn) record[config.purpose!!].takeIf { it.isNotBlank() } else null
                transactions.add(
                    Transaction(
                        category = if (hasCategoryColumn) record[config.category!!].takeIf { it.isNotBlank() } else null,
                        subcategory = if (hasSubcategoryColumn) record[config.subcategory!!].takeIf { it.isNotBlank() } else null,
                        bookingDate = bookingDate,
                        valueDate = valueDate,
                        accountingDate = bookingDate,
                        amount = amount,
                        currency = record[config.currency],
                        accountIban = accountIban,
                        purpose = purpose,
                    ),
                )

                if (hasAccountBalanceColumn) {
                    val balanceRaw = record[config.accountBalance!!]
                    if (balanceRaw.isNotBlank()) {
                        val balance = parseBalanceAmount(balanceRaw)
                        if (balance != null) {
                            val existing = latestBalanceByIban[accountIban]
                            if (existing == null || bookingDate >= existing.first) {
                                latestBalanceByIban[accountIban] = bookingDate to balance
                            }
                        }
                    }
                }
            }
        }

        return if (errors.isEmpty()) {
            val accountBalances =
                latestBalanceByIban.mapValues { (_, pair) -> AccountBalance(amount = pair.second, date = pair.first) }
            CsvParseResult.Valid(transactions, accountNames, accountBalances)
        } else {
            CsvParseResult.Invalid(errors)
        }
    }

    private fun parseDate(
        value: String,
        column: String,
        row: Int,
        formatter: DateTimeFormatter,
        pattern: String,
        errors: MutableList<CsvValidationError>,
    ): LocalDate? {
        if (value.isBlank()) {
            errors.add(CsvValidationError(row = row, column = column, value = value, message = "Date must not be blank"))
            return null
        }
        return try {
            LocalDate.parse(value, formatter)
        } catch (e: DateTimeParseException) {
            errors.add(
                CsvValidationError(
                    row = row,
                    column = column,
                    value = value,
                    message = "Invalid date format, expected $pattern",
                ),
            )
            null
        }
    }

    private fun parseBalanceAmount(value: String): BigDecimal? =
        try {
            BigDecimal(value.replace(".", "").replace(",", "."))
        } catch (_: NumberFormatException) {
            null
        }

    private fun parseAmount(
        value: String,
        column: String,
        row: Int,
        errors: MutableList<CsvValidationError>,
    ): BigDecimal? {
        if (value.isBlank()) {
            errors.add(CsvValidationError(row = row, column = column, value = value, message = "Amount must not be blank"))
            return null
        }
        return try {
            // Both formats use comma as the decimal separator.
            // A leading dot (thousands separator in German format) is stripped first
            // so that "-1.234,56" and "-86,11" and "-400" all parse correctly.
            BigDecimal(value.replace(".", "").replace(",", "."))
        } catch (e: NumberFormatException) {
            errors.add(
                CsvValidationError(
                    row = row,
                    column = column,
                    value = value,
                    message = "Invalid amount: $value",
                ),
            )
            null
        }
    }
}
