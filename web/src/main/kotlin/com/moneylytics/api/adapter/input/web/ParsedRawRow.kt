package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal
import java.time.LocalDate

data class ParsedRawRow(
    val rowNumber: Int,
    val bookingDate: LocalDate?,
    val valueDate: LocalDate?,
    val bookingDateRaw: String,
    val valueDateRaw: String,
    val counterparty: String,
    val counterpartyIban: String?,
    val purpose: String,
    val amount: BigDecimal?,
    val amountRaw: String,
    val currency: String,
    val accountIban: String,
    val accountName: String,
    val errors: List<ParsedRawError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class ParsedRawError(
    val column: String,
    val value: String,
    val message: String,
)
