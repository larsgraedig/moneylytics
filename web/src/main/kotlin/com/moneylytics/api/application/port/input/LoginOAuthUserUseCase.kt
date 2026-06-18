package com.moneylytics.api.application.port.input

interface LoginOAuthUserUseCase {
    fun loginOAuthUser(externalId: String): Long
}
