package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AcceptSuggestionUseCase
import com.moneylytics.api.application.port.input.RejectSuggestionUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.domain.CategorizationRequestedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/transactions")
class CategorySuggestionController(
    private val acceptSuggestionUseCase: AcceptSuggestionUseCase,
    private val rejectSuggestionUseCase: RejectSuggestionUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @PostMapping("/suggestions")
    suspend fun triggerSuggestions(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            eventPublisher.publishEvent(CategorizationRequestedEvent(organizationId))
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{id}/suggestion/accept")
    suspend fun acceptSuggestion(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val updated =
            withContext(Dispatchers.IO) {
                acceptSuggestionUseCase.accept(id, organizationId)
            }
        return if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}/suggestion")
    suspend fun rejectSuggestion(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        val updated =
            withContext(Dispatchers.IO) {
                rejectSuggestionUseCase.reject(id, organizationId)
            }
        return if (updated != null) ResponseEntity.ok(updated.toItem()) else ResponseEntity.notFound().build()
    }
}
