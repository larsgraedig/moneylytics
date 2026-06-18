package com.moneylytics.api.application.port.input

interface RegisterUserUseCase {
    fun registerUser(
        externalId: String,
        rawPassword: String,
    ): Long
}
