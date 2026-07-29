package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

enum class TransactionType { ALL, INCOME, EXPENSES }

fun interface GetTransactionsUseCase {
    fun getTransactions(query: GetTransactionsQuery): List<Transaction>
}

data class GetTransactionsQuery(
    val from: LocalDate,
    val to: LocalDate,
    val organizationId: Long,
    val type: TransactionType = TransactionType.ALL,
    val accountIban: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val group: String? = null,
    val uncategorized: Boolean = false,
    val excludeCollectionId: Long? = null,
    val excludeBudgetId: Long? = null,
)
