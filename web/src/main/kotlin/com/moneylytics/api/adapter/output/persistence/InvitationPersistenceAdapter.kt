package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.InvitationRepository
import com.moneylytics.api.domain.Invitation
import com.moneylytics.api.domain.OrgRole
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class InvitationPersistenceAdapter(
    private val invitationJpaRepository: InvitationJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : InvitationRepository {
    @Transactional
    override fun create(
        organizationId: Long,
        email: String,
        role: OrgRole,
        createdByUserId: Long,
    ): Invitation {
        val org = organizationJpaRepository.getReferenceById(organizationId)
        val creator = userJpaRepository.getReferenceById(createdByUserId)
        val token = UUID.randomUUID().toString()
        val entity =
            invitationJpaRepository.save(
                InvitationEntity(
                    organization = org,
                    email = email,
                    token = token,
                    role = role,
                    createdBy = creator,
                    expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                ),
            )
        return entity.toDomain()
    }

    @Transactional(readOnly = true)
    override fun findByToken(token: String): Invitation? = invitationJpaRepository.findByToken(token)?.toDomain()

    @Transactional(readOnly = true)
    override fun findPendingByOrganizationId(organizationId: Long): List<Invitation> =
        invitationJpaRepository
            .findByOrganizationIdAndAcceptedAtIsNullAndExpiresAtAfter(organizationId, Instant.now())
            .map { it.toDomain() }

    @Transactional
    override fun markAccepted(
        invitationId: Long,
        acceptedByUserId: Long,
    ) {
        val entity = invitationJpaRepository.getReferenceById(invitationId)
        val acceptor = userJpaRepository.getReferenceById(acceptedByUserId)
        entity.acceptedAt = Instant.now()
        entity.acceptedBy = acceptor
    }

    private fun InvitationEntity.toDomain() =
        Invitation(
            id = id!!,
            organizationId = organization.id!!,
            organizationName = organization.name,
            email = email,
            role = role,
            token = token,
            expiresAt = expiresAt,
            acceptedAt = acceptedAt,
        )
}
