package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.AccountBalance
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.Transaction

data class ImportTransactionsResult(
    val importedCount: Int,
    val importId: Long,
)

fun interface ImportTransactionsUseCase {
    fun importTransactions(command: ImportTransactionsCommand): ImportTransactionsResult
}

data class ImportTransactionsCommand(
    val transactions: List<Transaction>,
    val accountNames: Map<String, String>,
    val organizationId: Long,
    val accountBalances: Map<String, AccountBalance> = emptyMap(),
    val filename: String,
    val checksum: String,
    val fileType: ImportFileType,
)
