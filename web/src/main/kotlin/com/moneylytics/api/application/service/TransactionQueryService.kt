package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class TransactionQueryService(
    private val transactionRepository: TransactionRepository,
) : GetTransactionsUseCase,
    UpdateTransactionCategoryUseCase,
    UpdateTransactionCommentUseCase,
    UpdateTransactionAccountingDateUseCase {
    override fun getTransactions(query: GetTransactionsQuery): List<Transaction> {
        val transactions =
            if (query.onlyNegative) {
                transactionRepository.findNegativeByAccountingDateBetween(query.from, query.to, query.userId, query.accountIban)
            } else {
                transactionRepository.findByAccountingDateBetween(query.from, query.to, query.userId, query.accountIban)
            }
        return transactions
            .let { list -> query.category?.let { cat -> list.filter { it.category == cat } } ?: list }
            .let { list -> query.subcategory?.let { sub -> list.filter { it.subcategory == sub } } ?: list }
    }

    override fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
    ): Transaction? = transactionRepository.updateCategory(id, userId, category, subcategory)

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
}
