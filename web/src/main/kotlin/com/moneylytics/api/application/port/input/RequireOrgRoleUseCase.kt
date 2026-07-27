package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.OrgRole

interface RequireOrgRoleUseCase {
    fun requireOrgRole(
        organizationId: Long,
        userId: Long,
        minimumRole: OrgRole,
    )
}
