package com.moneylytics.api.application.port.input

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.server.ServerWebExchange

fun interface ResolveOrganizationUseCase {
    suspend fun resolveOrganization(
        principal: UserDetails,
        exchange: ServerWebExchange,
    ): Long
}
