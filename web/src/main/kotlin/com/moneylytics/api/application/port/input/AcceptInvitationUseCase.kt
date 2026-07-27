package com.moneylytics.api.application.port.input

fun interface AcceptInvitationUseCase {
    fun acceptInvitation(
        token: String,
        userId: Long,
    )
}
