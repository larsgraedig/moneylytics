package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction
import java.time.LocalDate

fun interface GetTransactionsUseCase {
    fun getTransactions(query: GetTransactionsQuery): List<Transaction>
}

data class GetTransactionsQuery(
    val from: LocalDate,
    val to: LocalDate,
    val onlyNegative: Boolean = false,
    val accountIban: String? = null,
)
