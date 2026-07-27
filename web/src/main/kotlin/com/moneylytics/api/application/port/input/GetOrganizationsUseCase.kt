package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.OrganizationMembership

fun interface GetOrganizationsUseCase {
    fun getOrganizations(userId: Long): List<OrganizationMembership>
}
