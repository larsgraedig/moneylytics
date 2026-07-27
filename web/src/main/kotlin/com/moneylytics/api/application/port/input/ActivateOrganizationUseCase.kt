package com.moneylytics.api.application.port.input

import org.springframework.web.server.WebSession

fun interface ActivateOrganizationUseCase {
    suspend fun activateOrganization(
        organizationId: Long,
        userId: Long,
        session: WebSession,
    )
}
