package com.moneylytics.api.application.port.input

fun interface ResolveUserUseCase {
    fun resolveUser(externalId: String): Long
}
