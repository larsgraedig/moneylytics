package com.moneylytics.api.adapter.input.web

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Component
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ParsedRawRow(
    val rowNumber: Int,
    val bookingDate: LocalDate?,
    val valueDate: LocalDate?,
    val bookingDateRaw: String,
    val valueDateRaw: String,
    val counterparty: String,
    val purpose: String,
    val amount: BigDecimal?,
    val amountRaw: String,
    val currency: String,
    val accountIban: String,
    val accountName: String,
    val errors: List<MlpRowError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

data class MlpRowError(
    val column: String,
    val value: String,
    val message: String,
)

sealed interface MlpRawParseResult {
    data class FormatError(
        val message: String,
    ) : MlpRawParseResult

    data class Success(
        val rows: List<ParsedRawRow>,
    ) : MlpRawParseResult
}

@Component
class MlpRawCsvParser {
    companion object {
        private val REQUIRED_COLUMNS =
            setOf(
                "IBAN Auftragskonto",
                "Bezeichnung Auftragskonto",
                "Buchungstag",
                "Valutadatum",
                "Betrag",
                "Waehrung",
            )
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }

    fun parse(csvContent: String): MlpRawParseResult {
        val apacheFormat =
            CSVFormat.DEFAULT
                .builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()

        val content = csvContent.trimStart('﻿')
        val csvParser = CSVParser.parse(StringReader(content), apacheFormat)
        val headers = csvParser.headerNames.map { it.trim() }.toSet()

        val missing = REQUIRED_COLUMNS - headers
        if (missing.isNotEmpty()) {
            return MlpRawParseResult.FormatError(
                "Missing required columns: ${missing.joinToString(", ")}",
            )
        }

        val hasCounterparty = "Name Zahlungsbeteiligter" in headers
        val hasPurpose = "Verwendungszweck" in headers

        val rows = mutableListOf<ParsedRawRow>()
        for ((index, record) in csvParser.withIndex()) {
            val rowNumber = index + 2
            val errors = mutableListOf<MlpRowError>()

            val bookingDateRaw = record["Buchungstag"]
            val valueDateRaw = record["Valutadatum"]
            val amountRaw = record["Betrag"]
            val currency = record["Waehrung"]
            val accountIban = record["IBAN Auftragskonto"]
            val accountName = record["Bezeichnung Auftragskonto"]
            val counterparty = if (hasCounterparty) record["Name Zahlungsbeteiligter"] else ""
            val purpose = if (hasPurpose) record["Verwendungszweck"] else ""

            val bookingDate = parseDate(bookingDateRaw, "Buchungstag", errors)
            val valueDate = parseDate(valueDateRaw, "Valutadatum", errors)
            val amount = parseAmount(amountRaw, "Betrag", errors)

            if (accountIban.isBlank()) {
                errors.add(MlpRowError("IBAN Auftragskonto", accountIban, "Account IBAN must not be blank"))
            }

            rows.add(
                ParsedRawRow(
                    rowNumber = rowNumber,
                    bookingDate = bookingDate,
                    valueDate = valueDate,
                    bookingDateRaw = bookingDateRaw,
                    valueDateRaw = valueDateRaw,
                    counterparty = counterparty,
                    purpose = purpose,
                    amount = amount,
                    amountRaw = amountRaw,
                    currency = currency,
                    accountIban = accountIban,
                    accountName = accountName,
                    errors = errors,
                ),
            )
        }

        return MlpRawParseResult.Success(rows)
    }

    private fun parseDate(
        value: String,
        column: String,
        errors: MutableList<MlpRowError>,
    ): LocalDate? {
        if (value.isBlank()) {
            errors.add(MlpRowError(column, value, "Date must not be blank"))
            return null
        }
        return try {
            LocalDate.parse(value, DATE_FORMATTER)
        } catch (_: DateTimeParseException) {
            errors.add(MlpRowError(column, value, "Invalid date format, expected dd.MM.yyyy"))
            null
        }
    }

    private fun parseAmount(
        value: String,
        column: String,
        errors: MutableList<MlpRowError>,
    ): BigDecimal? {
        if (value.isBlank()) {
            errors.add(MlpRowError(column, value, "Amount must not be blank"))
            return null
        }
        return try {
            BigDecimal(value.replace(".", "").replace(",", "."))
        } catch (_: NumberFormatException) {
            errors.add(MlpRowError(column, value, "Invalid amount: $value"))
            null
        }
    }
}
