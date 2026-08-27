package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.ActivateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateInvitationUseCase
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.ListPendingInvitationsUseCase
import com.moneylytics.api.application.port.input.ManageOrganizationMembersUseCase
import com.moneylytics.api.application.port.input.OnboardOrganizationUseCase
import com.moneylytics.api.application.port.input.OrganizationLogoUseCase
import com.moneylytics.api.application.port.input.RequireOrgRoleUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.OrgRole
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
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
import java.time.Instant

private val logger = KotlinLogging.logger {}

private val ALLOWED_LOGO_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
private const val MAX_LOGO_SIZE_BYTES = 2 * 1024 * 1024

@RestController
@RequestMapping("/organizations")
class OrganizationController(
    private val getOrganizationsUseCase: GetOrganizationsUseCase,
    private val manageOrganizationMembersUseCase: ManageOrganizationMembersUseCase,
    private val activateOrganizationUseCase: ActivateOrganizationUseCase,
    private val requireOrgRoleUseCase: RequireOrgRoleUseCase,
    private val createInvitationUseCase: CreateInvitationUseCase,
    private val listPendingInvitationsUseCase: ListPendingInvitationsUseCase,
    private val onboardOrganizationUseCase: OnboardOrganizationUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val organizationLogoUseCase: OrganizationLogoUseCase,
) {
    @GetMapping
    suspend fun getOrganizations(
        @AuthenticationPrincipal principal: UserDetails,
    ): List<OrganizationResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        return withContext(Dispatchers.IO) {
            getOrganizationsUseCase.getOrganizations(userId).map {
                OrganizationResponse(
                    id = it.organization.id,
                    name = it.organization.name,
                    role = it.role.name,
                    logoUrl = it.organization.logoUrl,
                )
            }
        }
    }

    @GetMapping("/{id}/logo", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    suspend fun getLogo(
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val logo =
            withContext(Dispatchers.IO) { organizationLogoUseCase.getLogo(id) }
                ?: return ResponseEntity.notFound().build()
        val (data, contentType) = logo
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(data)
    }

    @PostMapping("/{id}/logo")
    suspend fun uploadLogo(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val multipart = exchange.multipartData.awaitSingle()
        val filePart =
            multipart["file"]?.firstOrNull() as? FilePart
                ?: return ResponseEntity.badRequest().build()
        val contentType =
            filePart.headers().contentType?.toString()
                ?: throw ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type required")
        if (contentType !in ALLOWED_LOGO_CONTENT_TYPES) {
            throw ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type: $contentType")
        }
        val buffer = DataBufferUtils.join(filePart.content()).awaitSingle()
        val bytes = ByteArray(buffer.readableByteCount())
        buffer.read(bytes)
        DataBufferUtils.release(buffer)
        if (bytes.size > MAX_LOGO_SIZE_BYTES) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Logo must be at most 2 MB")
        }
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        withContext(Dispatchers.IO) { organizationLogoUseCase.uploadLogo(id, bytes, contentType, userId) }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/logo")
    suspend fun deleteLogo(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        withContext(Dispatchers.IO) { organizationLogoUseCase.deleteLogo(id, userId) }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/onboard")
    suspend fun onboard(
        @RequestBody request: CreateOrganizationRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OrganizationResponse> {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val org = withContext(Dispatchers.IO) { onboardOrganizationUseCase.onboardOrganization(request.name, userId) }
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

    @GetMapping("/{id}/invitations")
    suspend fun listInvitations(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): List<PendingInvitationResponse> {
        val requestingUserId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        return withContext(Dispatchers.IO) {
            requireOrgRoleUseCase.requireOrgRole(id, requestingUserId, OrgRole.ADMIN)
            listPendingInvitationsUseCase.listPendingInvitations(id).map {
                PendingInvitationResponse(
                    email = it.email,
                    role = it.role.name,
                    token = it.token,
                    expiresAt = it.expiresAt,
                )
            }
        }
    }

    @PostMapping("/{id}/invitations")
    suspend fun createInvitation(
        @PathVariable id: Long,
        @RequestBody request: CreateInvitationRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<InvitationResponse> {
        val requestingUserId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val invitation =
            withContext(Dispatchers.IO) {
                requireOrgRoleUseCase.requireOrgRole(id, requestingUserId, OrgRole.ADMIN)
                createInvitationUseCase.createInvitation(
                    organizationId = id,
                    email = request.email,
                    role = OrgRole.valueOf(request.role),
                    createdByUserId = requestingUserId,
                )
            }
        return ResponseEntity.ok(
            InvitationResponse(
                token = invitation.token,
                link = "/invite/${invitation.token}",
                expiresAt = invitation.expiresAt,
            ),
        )
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun removeMember(
        @PathVariable id: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ) {
        val requestingUserId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        try {
            withContext(Dispatchers.IO) {
                manageOrganizationMembersUseCase.removeMember(
                    organizationId = id,
                    targetUserId = userId,
                    requestingUserId = requestingUserId,
                )
            }
        } catch (e: IllegalStateException) {
            logger.warn { "Forbidden organization action: ${e.message}" }
            throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message, e)
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
        try {
            withContext(Dispatchers.IO) {
                manageOrganizationMembersUseCase.updateMemberRole(
                    organizationId = id,
                    targetUserId = userId,
                    role = OrgRole.valueOf(request.role),
                    requestingUserId = requestingUserId,
                )
            }
        } catch (e: IllegalStateException) {
            logger.warn { "Forbidden organization action: ${e.message}" }
            throw ResponseStatusException(HttpStatus.FORBIDDEN, e.message, e)
        }
    }
}

data class OrganizationResponse(
    val id: Long,
    val name: String,
    val role: String,
    val logoUrl: String? = null,
)

data class CreateOrganizationRequest(
    val name: String,
)

data class MemberResponse(
    val userId: Long,
    val email: String,
    val role: String,
)

data class CreateInvitationRequest(
    val email: String,
    val role: String,
)

data class InvitationResponse(
    val token: String,
    val link: String,
    val expiresAt: Instant,
)

data class UpdateRoleRequest(
    val role: String,
)

data class PendingInvitationResponse(
    val email: String,
    val role: String,
    val token: String,
    val expiresAt: Instant,
)
