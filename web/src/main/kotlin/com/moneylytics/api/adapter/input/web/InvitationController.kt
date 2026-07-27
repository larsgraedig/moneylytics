package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.AcceptInvitationUseCase
import com.moneylytics.api.application.port.input.GetInvitationUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/invitations")
class InvitationController(
    private val getInvitationUseCase: GetInvitationUseCase,
    private val acceptInvitationUseCase: AcceptInvitationUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping("/{token}")
    suspend fun getInvitation(
        @PathVariable token: String,
    ): InvitationPreviewResponse {
        val invitation =
            withContext(Dispatchers.IO) { getInvitationUseCase.getInvitation(token) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        return InvitationPreviewResponse(
            organizationName = invitation.organizationName,
            email = invitation.email,
            role = invitation.role.name,
            expiresAt = invitation.expiresAt,
            valid = invitation.acceptedAt == null && invitation.expiresAt.isAfter(Instant.now()),
        )
    }

    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun acceptInvitation(
        @PathVariable token: String,
        @AuthenticationPrincipal principal: UserDetails,
    ) {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        withContext(Dispatchers.IO) { acceptInvitationUseCase.acceptInvitation(token, userId) }
    }
}

data class InvitationPreviewResponse(
    val organizationName: String,
    val email: String,
    val role: String,
    val expiresAt: Instant,
    val valid: Boolean,
)
