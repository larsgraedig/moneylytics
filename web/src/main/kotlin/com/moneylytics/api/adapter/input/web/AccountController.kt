package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping
    suspend fun getAccounts(
        @AuthenticationPrincipal principal: UserDetails,
    ): AccountsResponse {
        val accounts =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(principal.username)
                getAccountsUseCase.getAccounts(userId)
            }
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
