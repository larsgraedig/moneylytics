package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteAccountUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SaveAccountUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val saveAccountUseCase: SaveAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
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
        return AccountsResponse(accounts.map { AccountResponse(iban = it.iban, name = it.name, lastTransactionDate = it.latestTransactionDate?.toString()) })
    }

    @PostMapping
    suspend fun createAccount(
        @RequestBody request: SaveAccountRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<AccountResponse> {
        if (request.iban.isBlank()) return ResponseEntity.badRequest().build()
        val account =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(principal.username)
                saveAccountUseCase.saveAccount(request.iban.trim(), request.name.trim(), userId)
            }
        return ResponseEntity.ok(AccountResponse(iban = account.iban, name = account.name))
    }

    @PutMapping("/{iban}")
    suspend fun updateAccount(
        @PathVariable iban: String,
        @RequestBody request: UpdateAccountRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<AccountResponse> {
        val account =
            withContext(Dispatchers.IO) {
                val userId = resolveUserUseCase.resolveUser(principal.username)
                saveAccountUseCase.saveAccount(iban, request.name.trim(), userId)
            }
        return ResponseEntity.ok(AccountResponse(iban = account.iban, name = account.name))
    }

    @DeleteMapping("/{iban}")
    suspend fun deleteAccount(
        @PathVariable iban: String,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> {
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            deleteAccountUseCase.deleteAccount(iban, userId)
        }
        return ResponseEntity.noContent().build()
    }
}

data class AccountsResponse(
    val accounts: List<AccountResponse>,
)

data class AccountResponse(
    val iban: String,
    val name: String,
    val lastTransactionDate: String? = null,
)

data class SaveAccountRequest(
    val iban: String,
    val name: String,
)

data class UpdateAccountRequest(
    val name: String,
)
