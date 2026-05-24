package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal

enum class RowStatus { NEW, DUPLICATE, INVALID }

data class RawPreviewResponse(
    val rows: List<RawPreviewRow>,
)

data class RawPreviewRow(
    val rowNumber: Int,
    val status: RowStatus,
    val bookingDate: String?,
    val valueDate: String?,
    val counterparty: String,
    val purpose: String,
    val amount: BigDecimal?,
    val amountRaw: String,
    val currency: String,
    val accountIban: String,
    val accountName: String,
    val fingerprint: String?,
    val errors: List<RawPreviewError>,
)

data class RawPreviewError(
    val column: String,
    val value: String,
    val message: String,
)

data class ImportRawRequest(
    val accountIban: String,
    val accountName: String,
    val transactions: List<RawTransactionImport>,
)

data class RawTransactionImport(
    val bookingDate: String,
    val valueDate: String,
    val amount: BigDecimal,
    val currency: String,
    val category: String,
    val subcategory: String,
)

data class CategoriesResponse(
    val categories: List<CategoryGroupResponse>,
)

data class CategoryGroupResponse(
    val name: String,
    val subcategories: List<String>,
)
