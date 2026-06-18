package com.moneylytics.api.application.port.input

interface CreateUserUseCase {
    fun createUser(
        externalId: String,
        rawPassword: String,
    ): Long
}
