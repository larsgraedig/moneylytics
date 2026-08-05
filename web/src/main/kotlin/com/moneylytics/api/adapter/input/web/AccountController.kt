package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteAccountUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
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
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val saveAccountUseCase: SaveAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun getAccounts(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): AccountsResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val accounts =
            withContext(Dispatchers.IO) {
                getAccountsUseCase.getAccounts(organizationId)
            }
        return AccountsResponse(
            accounts.map {
                AccountResponse(id = it.id!!, iban = it.iban, name = it.name, lastTransactionDate = it.latestTransactionDate?.toString())
            },
        )
    }

    @PostMapping
    suspend fun createAccount(
        @RequestBody request: SaveAccountRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<AccountResponse> {
        if (request.iban.isBlank()) return ResponseEntity.badRequest().build()
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val account =
            withContext(Dispatchers.IO) {
                saveAccountUseCase.saveAccount(request.iban.trim(), request.name.trim(), organizationId)
            }
        return ResponseEntity.ok(AccountResponse(id = account.id!!, iban = account.iban, name = account.name))
    }

    @PutMapping("/{iban}")
    suspend fun updateAccount(
        @PathVariable iban: String,
        @RequestBody request: UpdateAccountRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<AccountResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val account =
            withContext(Dispatchers.IO) {
                saveAccountUseCase.saveAccount(iban, request.name.trim(), organizationId)
            }
        return ResponseEntity.ok(AccountResponse(id = account.id!!, iban = account.iban, name = account.name))
    }

    @DeleteMapping("/{iban}")
    suspend fun deleteAccount(
        @PathVariable iban: String,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            deleteAccountUseCase.deleteAccount(iban, organizationId)
        }
        return ResponseEntity.noContent().build()
    }
}

data class AccountsResponse(
    val accounts: List<AccountResponse>,
)

data class AccountResponse(
    val id: Long,
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
