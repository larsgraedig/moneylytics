package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Organization

fun interface CreateOrganizationUseCase {
    fun createOrganization(
        name: String,
        ownerUserId: Long,
    ): Organization
}
