package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Invitation

fun interface ListPendingInvitationsUseCase {
    fun listPendingInvitations(organizationId: Long): List<Invitation>
}
