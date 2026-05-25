package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service

@Service
class TransactionQueryService(
    private val transactionRepository: TransactionRepository,
) : GetTransactionsUseCase {
    override fun getTransactions(query: GetTransactionsQuery): List<Transaction> {
        val transactions =
            if (query.onlyNegative) {
                transactionRepository.findNegativeByBookingDateBetween(query.from, query.to, query.userId, query.accountIban)
            } else {
                transactionRepository.findByBookingDateBetween(query.from, query.to, query.userId, query.accountIban)
            }
        return transactions
            .let { list -> query.category?.let { cat -> list.filter { it.category == cat } } ?: list }
            .let { list -> query.subcategory?.let { sub -> list.filter { it.subcategory == sub } } ?: list }
    }
}
