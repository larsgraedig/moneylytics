package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Invitation
import com.moneylytics.api.domain.OrgRole

interface CreateInvitationUseCase {
    fun createInvitation(
        organizationId: Long,
        email: String,
        role: OrgRole,
        createdByUserId: Long,
    ): Invitation
}
