package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ActivateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.ManageOrganizationMembersUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.service.UserAlreadyExistsException
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

@RestController
@RequestMapping("/organizations")
class OrganizationController(
    private val getOrganizationsUseCase: GetOrganizationsUseCase,
    private val createOrganizationUseCase: CreateOrganizationUseCase,
    private val manageOrganizationMembersUseCase: ManageOrganizationMembersUseCase,
    private val activateOrganizationUseCase: ActivateOrganizationUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping
    suspend fun getOrganizations(
        @AuthenticationPrincipal principal: UserDetails,
    ): List<OrganizationResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        return withContext(Dispatchers.IO) {
            getOrganizationsUseCase.getOrganizations(userId).map {
                OrganizationResponse(id = it.organization.id, name = it.organization.name, role = it.role.name)
            }
        }
    }

    @PostMapping
    suspend fun createOrganization(
        @RequestBody request: CreateOrganizationRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OrganizationResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val org = withContext(Dispatchers.IO) { createOrganizationUseCase.createOrganization(request.name, userId) }
        return ResponseEntity.ok(OrganizationResponse(id = org.id, name = org.name, role = OrgRole.OWNER.name))
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun activate(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ) {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val session = exchange.session.awaitSingle()
        activateOrganizationUseCase.activateOrganization(id, userId, session)
    }

    @GetMapping("/{id}/members")
    suspend fun getMembers(
        @PathVariable id: Long,
    ): List<MemberResponse> =
        withContext(Dispatchers.IO) {
            manageOrganizationMembersUseCase.getMembers(id).map {
                MemberResponse(userId = it.userId, email = it.email, role = it.role.name)
            }
        }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun addMember(
        @PathVariable id: Long,
        @RequestBody request: AddMemberRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ) {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        try {
            withContext(Dispatchers.IO) {
                manageOrganizationMembersUseCase.addMember(
                    organizationId = id,
                    email = request.email,
                    password = request.password,
                    role = OrgRole.valueOf(request.role),
                    requestingUserId = userId,
                )
            }
        } catch (e: UserAlreadyExistsException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
        }
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun removeMember(
        @PathVariable id: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ) {
        val requestingUserId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        withContext(Dispatchers.IO) {
            manageOrganizationMembersUseCase.removeMember(
                organizationId = id,
                targetUserId = userId,
                requestingUserId = requestingUserId,
            )
        }
    }

    @PatchMapping("/{id}/members/{userId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun updateMemberRole(
        @PathVariable id: Long,
        @PathVariable userId: Long,
        @RequestBody request: UpdateRoleRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ) {
        val requestingUserId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        withContext(Dispatchers.IO) {
            manageOrganizationMembersUseCase.updateMemberRole(
                organizationId = id,
                targetUserId = userId,
                role = OrgRole.valueOf(request.role),
                requestingUserId = requestingUserId,
            )
        }
    }
}

data class OrganizationResponse(
    val id: Long,
    val name: String,
    val role: String,
)

data class CreateOrganizationRequest(
    val name: String,
)

data class MemberResponse(
    val userId: Long,
    val email: String,
    val role: String,
)

data class AddMemberRequest(
    val email: String,
    val password: String,
    val role: String,
)

data class UpdateRoleRequest(
    val role: String,
)
