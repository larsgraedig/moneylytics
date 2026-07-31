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
    val accountBalances: Map<String, CamtAccountBalance> = emptyMap(),
)

data class CamtAccountBalance(
    val amount: BigDecimal,
    val date: String,
)

data class CamtAccountInfo(
    val iban: String,
    val suggestedName: String,
)

data class TransactionEnrichRequest(
    val fingerprint: String,
    val purpose: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
)

data class CamtImportRequest(
    val accountNames: Map<String, String>,
    val toImport: List<CamtTransactionImport>,
    val toIgnore: List<String>,
    val toEnrich: List<TransactionEnrichRequest> = emptyList(),
    val accountBalances: Map<String, CamtAccountBalance> = emptyMap(),
)

data class CamtTransactionImport(
    val fingerprint: String,
    val bookingDate: String,
    val valueDate: String,
    val amount: java.math.BigDecimal,
    val currency: String,
    val category: String,
    val group: String,
    val accountIban: String,
    val purpose: String?,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
    val subcategory: String? = null,
)

data class CategoriesResponse(
    val categories: List<CategoryNodeResponse>,
)

data class CategoryNodeResponse(
    val id: Long,
    val name: String,
    val children: List<CategoryNodeResponse>,
)
