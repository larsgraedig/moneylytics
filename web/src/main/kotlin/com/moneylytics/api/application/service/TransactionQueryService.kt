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

    override fun getTransactions(query: GetTransactionsQuery): List<Transaction> =
        transactionRepository.findByBookingDateBetween(query.from, query.to)
}
