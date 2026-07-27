package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Service

@Service
class TransactionImportService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ImportTransactionsUseCase {
    override fun importTransactions(command: ImportTransactionsCommand): Int {
        command.accountNames.forEach { (iban, name) ->
            if (accountRepository.findByIban(iban, command.organizationId) == null) {
                accountRepository.save(Account(iban = iban, name = name), command.organizationId)
            }
        }
        val count = transactionRepository.saveAll(command.transactions, command.organizationId)
        command.accountBalances.forEach { (iban, balance) ->
            accountRepository.updateBalance(iban, command.organizationId, balance.amount, balance.date)
        }
        return count
    }
}
