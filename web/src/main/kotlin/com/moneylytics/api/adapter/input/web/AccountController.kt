package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetAccountsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val getAccountsUseCase: GetAccountsUseCase,
) {
    @GetMapping
    suspend fun getAccounts(): AccountsResponse {
        val accounts = withContext(Dispatchers.IO) { getAccountsUseCase.getAccounts() }
        return AccountsResponse(accounts.map { AccountResponse(iban = it.iban, name = it.name) })
    }
}

data class AccountsResponse(
    val accounts: List<AccountResponse>,
)

data class AccountResponse(
    val iban: String,
    val name: String,
)
