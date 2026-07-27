package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.InvitationRepository
import com.moneylytics.api.application.port.output.OrganizationRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.Invitation
import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class InvitationServiceTest {
    private val invitationRepository: InvitationRepository = mock()
    private val organizationRepository: OrganizationRepository = mock()
    private val userRepository: UserRepository = mock()
    private val service = InvitationService(invitationRepository, organizationRepository, userRepository)

    private val token = "abc123"
    private val userId = 10L
    private val orgId = 5L
    private val user = User(id = userId, externalId = "invited@test.de", passwordHash = null)

    private fun invitation(
        acceptedAt: Instant? = null,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) = Invitation(
        id = 1L,
        organizationId = orgId,
        organizationName = "Test Org",
        email = "invited@test.de",
        role = OrgRole.MEMBER,
        token = token,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
    )

    @Test
    fun `should add user to org when invitation is valid`() {
        whenever(invitationRepository.findByToken(token)).thenReturn(invitation())
        whenever(userRepository.findById(userId)).thenReturn(user)
        whenever(organizationRepository.isMember(orgId, userId)).thenReturn(false)

        service.acceptInvitation(token, userId)

        verify(organizationRepository).addMember(orgId, userId, OrgRole.MEMBER)
        verify(invitationRepository).markAccepted(1L, userId)
    }

    @Test
    fun `should throw 404 when token does not exist`() {
        whenever(invitationRepository.findByToken(token)).thenReturn(null)

        assertThatThrownBy { service.acceptInvitation(token, userId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("not found")
    }

    @Test
    fun `should throw 409 when invitation is already accepted`() {
        whenever(invitationRepository.findByToken(token)).thenReturn(invitation(acceptedAt = Instant.now()))

        assertThatThrownBy { service.acceptInvitation(token, userId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("already accepted")
    }

    @Test
    fun `should throw 410 when invitation is expired`() {
        whenever(invitationRepository.findByToken(token)).thenReturn(
            invitation(expiresAt = Instant.now().minusSeconds(1)),
        )

        assertThatThrownBy { service.acceptInvitation(token, userId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun `should throw 403 when user email does not match invitation`() {
        val wrongUser = User(id = userId, externalId = "other@test.de", passwordHash = null)
        whenever(invitationRepository.findByToken(token)).thenReturn(invitation())
        whenever(userRepository.findById(userId)).thenReturn(wrongUser)

        assertThatThrownBy { service.acceptInvitation(token, userId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Email does not match")
    }

    @Test
    fun `should not add member again when user is already in org`() {
        whenever(invitationRepository.findByToken(token)).thenReturn(invitation())
        whenever(userRepository.findById(userId)).thenReturn(user)
        whenever(organizationRepository.isMember(orgId, userId)).thenReturn(true)

        service.acceptInvitation(token, userId)

        verify(organizationRepository, never()).addMember(any(), any(), any())
        verify(invitationRepository).markAccepted(1L, userId)
    }
}
