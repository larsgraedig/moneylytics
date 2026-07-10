package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.TransactionType
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class TransactionQueryService(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
) : GetTransactionsUseCase,
    UpdateTransactionCategoryUseCase,
    UpdateTransactionCommentUseCase,
    UpdateTransactionAccountingDateUseCase,
    EnrichTransactionUseCase {
    override fun getTransactions(query: GetTransactionsQuery): List<Transaction> {
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.userId, query.accountIban)
        return transactions
            .let { list ->
                when (query.type) {
                    TransactionType.INCOME -> list.filter { it.amount > BigDecimal.ZERO }
                    TransactionType.EXPENSES -> list.filter { it.amount < BigDecimal.ZERO }
                    TransactionType.ALL -> list
                }
            }.let { list -> if (query.uncategorized) list.filter { it.category == null } else list }
            .let { list -> query.category?.let { cat -> list.filter { it.category == cat } } ?: list }
            .let { list -> query.subcategory?.let { sub -> list.filter { it.subcategory == sub } } ?: list }
            .let { list -> query.categoryGroup?.let { grp -> list.filter { it.categoryGroup == grp } } ?: list }
            .let { list ->
                query.excludeCollectionId?.let { collectionId ->
                    val assigned = transactionRepository.findAssignedTransactionIdsByCollectionId(collectionId)
                    list.filter { it.id !in assigned }
                } ?: list
            }.let { list ->
                query.excludeBudgetId?.let { budgetId ->
                    val assigned = budgetRepository.findAssignedTransactionIdsByBudgetId(budgetId, query.userId)
                    list.filter { it.id !in assigned }
                } ?: list
            }
    }

    override fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
        categoryGroup: String?,
    ): Transaction? = transactionRepository.updateCategory(id, userId, category, subcategory, categoryGroup)

    override fun updateComment(
        id: Long,
        userId: Long,
        comment: String?,
    ): Transaction? = transactionRepository.updateComment(id, userId, comment)

    override fun updateAccountingDate(
        id: Long,
        userId: Long,
        accountingDate: LocalDate,
    ): Transaction? = transactionRepository.updateAccountingDate(id, userId, accountingDate)

    override fun enrichByFingerprint(
        fingerprint: String,
        userId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    ) = transactionRepository.enrichByFingerprint(fingerprint, userId, purpose, counterpartyName, counterpartyIban, null)
}
