package com.moneylytics.api.adapter.input.web

enum class AmountFormat { GERMAN, ENGLISH }

data class DetectionData(
    val result: CsvDetectionResult,
    val fingerprint: String,
)

data class CsvDetectionResult(
    val fingerprint: String,
    val delimiter: String,
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val suggestions: CsvColumnSuggestions,
    val detectedDateFormat: String?,
    val detectedAmountFormat: AmountFormat,
    val savedMapping: GenericCsvMapping?,
)

data class CsvColumnSuggestions(
    val date: String?,
    val amount: String?,
    val currency: String?,
    val purpose: String?,
    val accountIban: String?,
    val category: String?,
    val subcategory: String?,
)

data class GenericCsvMapping(
    val delimiter: String,
    val dateColumn: String,
    val dateFormat: String,
    val amountColumn: String,
    val amountFormat: AmountFormat,
    val purposeColumn: String?,
    val categoryColumn: String?,
    val subcategoryColumn: String?,
    val accountIbanColumn: String?,
    val currencyColumn: String?,
    val fixedAccountIban: String?,
    val fixedCurrency: String,
)
