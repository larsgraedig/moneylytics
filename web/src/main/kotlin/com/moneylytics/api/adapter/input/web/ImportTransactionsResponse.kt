package com.moneylytics.api.adapter.input.web

data class ImportSuccessResponse(
    val importedCount: Int,
    val importId: Long,
)

data class CsvValidationErrorsResponse(
    val errors: List<CsvValidationError>,
)
