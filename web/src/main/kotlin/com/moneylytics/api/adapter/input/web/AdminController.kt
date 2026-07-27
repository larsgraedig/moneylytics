package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ListUsersUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.config.ImpersonationWebFilter.Companion.IMPERSONATED_USER_ID_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/admin")
class AdminController(
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase,
    private val listUsersUseCase: ListUsersUseCase,
) {
    @PostMapping("/recurring/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun triggerRecurringSync() =
        withContext(Dispatchers.IO) {
            syncRecurringSeriesUseCase.syncForAllOrganizations()
        }

    @GetMapping("/users")
    suspend fun listUsers(): UsersResponse =
        withContext(Dispatchers.IO) {
            UsersResponse(listUsersUseCase.listUsers().map { it.externalId })
        }

    @PostMapping("/impersonate/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun impersonate(
        @PathVariable externalId: String,
        exchange: ServerWebExchange,
    ) {
        val exists = withContext(Dispatchers.IO) { listUsersUseCase.listUsers().any { it.externalId == externalId } }
        if (!exists) throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: $externalId")
        exchange.session.awaitSingle().attributes[IMPERSONATED_USER_ID_KEY] = externalId
    }

    @DeleteMapping("/impersonate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deimpersonate(exchange: ServerWebExchange) {
        exchange.session
            .awaitSingle()
            .attributes
            .remove(IMPERSONATED_USER_ID_KEY)
    }
}
