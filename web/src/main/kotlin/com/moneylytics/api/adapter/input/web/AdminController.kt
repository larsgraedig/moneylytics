package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.ListUsersUseCase
import com.moneylytics.api.application.port.input.ListUsersWithOrgsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.config.ImpersonationWebFilter.Companion.IMPERSONATED_USER_ID_KEY
import com.moneylytics.api.domain.OrgRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val listUsersWithOrgsUseCase: ListUsersWithOrgsUseCase,
    private val createOrganizationUseCase: CreateOrganizationUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @PostMapping("/recurring/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun triggerRecurringSync() =
        withContext(Dispatchers.IO) {
            syncRecurringSeriesUseCase.syncForAllOrganizations()
        }

    @GetMapping("/users")
    suspend fun listUsers(): AdminUsersResponse =
        withContext(Dispatchers.IO) {
            val result = listUsersWithOrgsUseCase.listUsersWithOrgs()
            AdminUsersResponse(
                organizations = result.organizations.map { AdminOrgGroupDto(it.id, it.name, it.members) },
                unorganized = result.unorganized,
            )
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

    @PostMapping("/organizations")
    suspend fun createOrganization(
        @RequestBody request: CreateOrganizationRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OrganizationResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val org = withContext(Dispatchers.IO) { createOrganizationUseCase.createOrganization(request.name, userId) }
        return ResponseEntity.ok(OrganizationResponse(id = org.id, name = org.name, role = OrgRole.OWNER.name))
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

data class AdminOrgGroupDto(
    val id: Long,
    val name: String,
    val members: List<String>,
)

data class AdminUsersResponse(
    val organizations: List<AdminOrgGroupDto>,
    val unorganized: List<String>,
)
