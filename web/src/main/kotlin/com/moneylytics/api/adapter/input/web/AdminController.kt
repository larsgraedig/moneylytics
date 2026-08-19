package com.moneylytics.api.adapter.input.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.moneylytics.api.application.port.input.AdminManageOrgMembersUseCase
import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateTierUseCase
import com.moneylytics.api.application.port.input.ListTiersUseCase
import com.moneylytics.api.application.port.input.ListUsersUseCase
import com.moneylytics.api.application.port.input.ListUsersWithOrgsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SetTierStripePriceUseCase
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/admin")
class AdminController(
    private val syncRecurringSeriesUseCase: SyncRecurringSeriesUseCase,
    private val listUsersUseCase: ListUsersUseCase,
    private val listUsersWithOrgsUseCase: ListUsersWithOrgsUseCase,
    private val createOrganizationUseCase: CreateOrganizationUseCase,
    private val adminManageOrgMembersUseCase: AdminManageOrgMembersUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val listTiersUseCase: ListTiersUseCase,
    private val createTierUseCase: CreateTierUseCase,
    private val assignTierToUserUseCase: AssignTierToUserUseCase,
    private val setTierStripePriceUseCase: SetTierStripePriceUseCase,
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

    @PostMapping("/organizations/{orgId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun adminAddMember(
        @PathVariable orgId: Long,
        @RequestBody request: AdminMemberRequest,
    ) = withContext(Dispatchers.IO) {
        adminManageOrgMembersUseCase.adminAddMember(orgId, request.externalId, OrgRole.valueOf(request.role))
    }

    @DeleteMapping("/organizations/{orgId}/members/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun adminRemoveMember(
        @PathVariable orgId: Long,
        @PathVariable externalId: String,
    ) = withContext(Dispatchers.IO) {
        adminManageOrgMembersUseCase.adminRemoveMember(
            orgId,
            URLDecoder.decode(externalId, StandardCharsets.UTF_8),
        )
    }

    @DeleteMapping("/impersonate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deimpersonate(exchange: ServerWebExchange) {
        exchange.session
            .awaitSingle()
            .attributes
            .remove(IMPERSONATED_USER_ID_KEY)
    }

    @GetMapping("/users/tiers")
    suspend fun listUserTiers(): List<UserTierResponse> =
        withContext(Dispatchers.IO) {
            listUsersUseCase.listUsers().map { user ->
                UserTierResponse(
                    externalId = user.externalId,
                    tier = AdminTierInfo(id = user.tier.id, name = user.tier.name),
                )
            }
        }

    @GetMapping("/tiers")
    suspend fun listTiers(): List<TierResponse> =
        withContext(Dispatchers.IO) {
            listTiersUseCase.listTiers().map { TierResponse(it.id, it.name, it.description, it.active, it.isDefault, it.stripePriceId) }
        }

    @PostMapping("/tiers")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createTier(
        @RequestBody request: CreateTierRequest,
    ): TierResponse =
        withContext(Dispatchers.IO) {
            val tier = createTierUseCase.createTier(request.name, request.description, request.isDefault ?: false)
            TierResponse(tier.id, tier.name, tier.description, tier.active, tier.isDefault, tier.stripePriceId)
        }

    @PatchMapping("/tiers/{id}/stripe-price")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun setTierStripePrice(
        @PathVariable id: Long,
        @RequestBody request: SetStripePriceRequest,
    ) = withContext(Dispatchers.IO) {
        setTierStripePriceUseCase.setStripePrice(id, request.priceId)
    }

    @PostMapping("/users/{externalId}/tier")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun assignTier(
        @PathVariable externalId: String,
        @RequestBody request: AssignTierRequest,
    ) = withContext(Dispatchers.IO) {
        val userId = resolveUserUseCase.resolveUser(URLDecoder.decode(externalId, StandardCharsets.UTF_8))
        assignTierToUserUseCase.assignTierToUser(userId, request.tierId)
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

data class AdminMemberRequest(
    val externalId: String,
    val role: String,
)

data class TierResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val active: Boolean,
    @JsonProperty("isDefault") val isDefault: Boolean,
    val stripePriceId: String?,
)

data class SetStripePriceRequest(
    val priceId: String?,
)

data class CreateTierRequest(
    val name: String,
    val description: String? = null,
    val isDefault: Boolean? = false,
)

data class AssignTierRequest(
    val tierId: Long,
)

data class AdminTierInfo(
    val id: Long,
    val name: String,
)

data class UserTierResponse(
    val externalId: String,
    val tier: AdminTierInfo,
)
