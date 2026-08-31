package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.BudgetTransactionSummary
import com.moneylytics.api.domain.CollectionSummary
import com.moneylytics.api.domain.TransactionGroupSummary
import java.math.BigDecimal
import java.time.LocalDate

data class GetImportTransactionsQuery(
    val importId: Long,
    val organizationId: Long,
)

data class ImportTransactionItem(
    val id: Long,
    val bookingDate: LocalDate,
    val counterpartyName: String?,
    val purpose: String?,
    val amount: BigDecimal,
    val currency: String,
    val excluded: Boolean,
    val collections: List<CollectionSummary>,
    val budgetLinks: List<BudgetTransactionSummary>,
    val groups: List<TransactionGroupSummary>,
    val parentId: Long?,
    val isVirtual: Boolean,
    val category: String?,
    val subcategory: String?,
    val group: String?,
    val suggestedCategoryId: Long?,
)

interface GetImportTransactionsUseCase {
    fun getImportTransactions(query: GetImportTransactionsQuery): List<ImportTransactionItem>

    fun getImportFileTransactions(
        fileId: Long,
        importId: Long,
        organizationId: Long,
    ): List<ImportTransactionItem>
}
