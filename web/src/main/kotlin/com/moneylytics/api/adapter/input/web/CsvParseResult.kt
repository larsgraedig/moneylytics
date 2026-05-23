package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.Transaction

sealed interface CsvParseResult {
    data class Valid(
        val transactions: List<Transaction>,
        val accountNames: Map<String, String>,
    ) : CsvParseResult

    data class Invalid(
        val errors: List<CsvValidationError>,
    ) : CsvParseResult
}
