package com.moneylytics.api.adapter.input.web

data class CsvValidationError(
    val row: Int,
    val column: String,
    val value: String,
    val message: String,
)
