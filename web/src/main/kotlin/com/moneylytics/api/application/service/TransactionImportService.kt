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
            if (accountRepository.findByIban(iban, command.userId) == null) {
                accountRepository.save(Account(iban = iban, name = name), command.userId)
            }
        }
        val count = transactionRepository.saveAll(command.transactions, command.userId)
        command.accountBalances.forEach { (iban, balance) ->
            accountRepository.updateBalance(iban, command.userId, balance.amount, balance.date)
        }
        return count
    }
}
