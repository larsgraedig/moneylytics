package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Invitation

fun interface GetInvitationUseCase {
    fun getInvitation(token: String): Invitation?
}
