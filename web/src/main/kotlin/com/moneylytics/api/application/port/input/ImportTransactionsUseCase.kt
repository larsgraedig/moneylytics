package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

fun interface ImportTransactionsUseCase {
    fun importTransactions(command: ImportTransactionsCommand): Int
}

data class ImportTransactionsCommand(
    val transactions: List<Transaction>,
)
