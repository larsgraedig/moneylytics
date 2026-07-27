package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Invitation
import com.moneylytics.api.domain.OrgRole

interface InvitationRepository {
    fun create(
        organizationId: Long,
        email: String,
        role: OrgRole,
        createdByUserId: Long,
    ): Invitation

    fun findByToken(token: String): Invitation?

    fun findPendingByOrganizationId(organizationId: Long): List<Invitation>

    fun markAccepted(
        invitationId: Long,
        acceptedByUserId: Long,
    )
}
