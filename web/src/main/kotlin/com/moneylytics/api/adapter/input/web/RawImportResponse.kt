package com.moneylytics.api.adapter.input.web

import java.math.BigDecimal
import java.util.UUID

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
    val suggestedCategoryId: Long? = null,
    val sourceFilename: String? = null,
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
    val previewToken: UUID,
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

data class CamtImportByTokenRequest(
    val previewToken: UUID,
    val excludedRowIndices: List<Int> = emptyList(),
    val toIgnore: List<String> = emptyList(),
    val toEnrich: List<TransactionEnrichRequest> = emptyList(),
    val accountNames: Map<String, String>,
    val accountBalances: Map<String, CamtAccountBalance> = emptyMap(),
)

data class CategoriesResponse(
    val categories: List<CategoryNodeResponse>,
)

data class CategoryNodeResponse(
    val id: Long,
    val name: String,
    val children: List<CategoryNodeResponse>,
)

data class CategoryStatsResponse(
    val items: List<CategoryStatItemResponse>,
)

data class CategoryStatItemResponse(
    val categoryId: Long,
    val totalCount: Long,
    val periodCount: Long,
)

data class DeleteCategoryErrorResponse(
    val reason: String,
)
