package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Organization

fun interface OnboardOrganizationUseCase {
    fun onboardOrganization(
        name: String,
        userId: Long,
    ): Organization
}
