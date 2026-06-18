package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.DeleteAccountUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.SaveAccountUseCase
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountQueryService(
    private val accountRepository: AccountRepository,
) : GetAccountsUseCase,
    SaveAccountUseCase,
    DeleteAccountUseCase {
    override fun getAccounts(userId: Long): List<Account> = accountRepository.findAll(userId)

    @Transactional
    override fun saveAccount(
        iban: String,
        name: String,
        userId: Long,
    ): Account {
        val existing = accountRepository.findByIban(iban, userId)
        return accountRepository
            .save(
                Account(iban = iban, name = name.ifBlank { iban }),
                userId,
            ).also {
                if (existing == null) {
                    // new account – no categories to seed
                }
            }
    }

    @Transactional
    override fun deleteAccount(
        iban: String,
        userId: Long,
    ) = accountRepository.delete(iban, userId)
}
