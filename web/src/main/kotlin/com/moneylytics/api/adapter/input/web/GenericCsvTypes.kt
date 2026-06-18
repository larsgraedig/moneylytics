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

data class GenericCsvPreviewRow(
    val rowIndex: Int,
    val date: String,
    val amount: Double,
    val currency: String,
    val accountIban: String,
    val purpose: String?,
    val fingerprint: String,
    val status: RowStatus,
    val unknownAccount: Boolean,
    val mappedCategory: String?,
    val mappedSubcategory: String?,
)

data class GenericRowToImport(
    val date: String,
    val amount: Double,
    val currency: String,
    val accountIban: String,
    val purpose: String?,
    val category: String,
    val subcategory: String,
)
