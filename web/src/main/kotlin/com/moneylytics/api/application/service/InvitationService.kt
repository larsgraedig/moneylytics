package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.AcceptInvitationUseCase
import com.moneylytics.api.application.port.input.CreateInvitationUseCase
import com.moneylytics.api.application.port.input.GetInvitationUseCase
import com.moneylytics.api.application.port.output.InvitationRepository
import com.moneylytics.api.application.port.output.OrganizationRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.Invitation
import com.moneylytics.api.domain.OrgRole
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class InvitationService(
    private val invitationRepository: InvitationRepository,
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
) : CreateInvitationUseCase,
    GetInvitationUseCase,
    AcceptInvitationUseCase {
    override fun createInvitation(
        organizationId: Long,
        email: String,
        role: OrgRole,
        createdByUserId: Long,
    ): Invitation = invitationRepository.create(organizationId, email, role, createdByUserId)

    override fun getInvitation(token: String): Invitation? = invitationRepository.findByToken(token)

    @Transactional
    override fun acceptInvitation(
        token: String,
        userId: Long,
    ) {
        val invitation =
            invitationRepository.findByToken(token)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found")
        if (invitation.acceptedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Invitation already accepted")
        }
        if (invitation.expiresAt.isBefore(Instant.now())) {
            throw ResponseStatusException(HttpStatus.GONE, "Invitation expired")
        }
        val user =
            userRepository.findById(userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        if (!user.externalId.equals(invitation.email, ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Email does not match invitation")
        }
        if (!organizationRepository.isMember(invitation.organizationId, userId)) {
            organizationRepository.addMember(invitation.organizationId, userId, invitation.role)
        }
        invitationRepository.markAccepted(invitation.id, userId)
    }
}
