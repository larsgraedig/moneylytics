package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.DeleteAccountUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.SaveAccountUseCase
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountQueryService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : GetAccountsUseCase,
    SaveAccountUseCase,
    DeleteAccountUseCase {
    override fun getAccounts(organizationId: Long): List<Account> {
        val latestDates = transactionRepository.latestTransactionDatesByOrganizationId(organizationId)
        return accountRepository.findAll(organizationId).map { it.copy(latestTransactionDate = latestDates[it.iban]) }
    }

    @Transactional
    override fun saveAccount(
        iban: String,
        name: String,
        organizationId: Long,
    ): Account {
        val existing = accountRepository.findByIban(iban, organizationId)
        return accountRepository
            .save(
                Account(iban = iban, name = name.ifBlank { iban }),
                organizationId,
            ).also {
                if (existing == null) {
                    // new account – no categories to seed
                }
            }
    }

    @Transactional
    override fun deleteAccount(
        iban: String,
        organizationId: Long,
    ) = accountRepository.delete(iban, organizationId)
}
