package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.domain.Account
import org.springframework.stereotype.Service

@Service
class AccountQueryService(
    private val accountRepository: AccountRepository,
) : GetAccountsUseCase {
    override fun getAccounts(userId: Long): List<Account> = accountRepository.findAll(userId)
}
