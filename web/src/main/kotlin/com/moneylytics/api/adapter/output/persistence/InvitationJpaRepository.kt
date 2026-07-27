package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface InvitationJpaRepository : JpaRepository<InvitationEntity, Long> {
    fun findByToken(token: String): InvitationEntity?

    fun findByOrganizationIdAndAcceptedAtIsNullAndExpiresAtAfter(
        organizationId: Long,
        now: Instant,
    ): List<InvitationEntity>
}
