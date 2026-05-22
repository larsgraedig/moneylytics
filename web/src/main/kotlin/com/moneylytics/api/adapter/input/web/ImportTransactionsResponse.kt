package com.moneylytics.api.adapter.input.web

data class ImportSuccessResponse(
    val importedCount: Int,
)

data class CsvValidationErrorsResponse(
    val errors: List<CsvValidationError>,
)
