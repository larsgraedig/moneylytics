package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.output.TransactionRepository
import org.springframework.stereotype.Service

@Service
class TransactionImportService(
    private val transactionRepository: TransactionRepository,
) : ImportTransactionsUseCase {

    override fun importTransactions(command: ImportTransactionsCommand): Int =
        transactionRepository.saveAll(command.transactions)
}
