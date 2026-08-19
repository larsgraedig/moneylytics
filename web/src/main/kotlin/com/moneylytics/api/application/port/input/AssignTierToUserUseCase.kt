package com.moneylytics.api.application.port.input

interface AssignTierToUserUseCase {
    fun assignTierToUser(
        userId: Long,
        tierId: Long,
    )
}
