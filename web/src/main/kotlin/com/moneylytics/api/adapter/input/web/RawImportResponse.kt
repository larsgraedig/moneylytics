package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal

enum class RowStatus { NEW, DUPLICATE, INVALID, PREVIOUSLY_IGNORED }

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
    val unknownAccount: Boolean = false,
    val counterpartyIban: String? = null,
)

data class RawPreviewError(
    val column: String,
    val value: String,
    val message: String,
)

data class CamtPreviewResponse(
    val rows: List<RawPreviewRow>,
    val accounts: List<CamtAccountInfo>,
)

data class CamtAccountInfo(
    val iban: String,
    val suggestedName: String,
)

data class CamtImportRequest(
    val accountNames: Map<String, String>,
    val toImport: List<CamtTransactionImport>,
    val toIgnore: List<String>,
)

data class CamtTransactionImport(
    val fingerprint: String,
    val bookingDate: String,
    val valueDate: String,
    val amount: java.math.BigDecimal,
    val currency: String,
    val category: String,
    val subcategory: String,
    val accountIban: String,
    val purpose: String?,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
)

data class CategoriesResponse(
    val categories: List<CategoryGroupResponse>,
)

data class CategoryGroupResponse(
    val name: String,
    val subcategories: List<String>,
)
