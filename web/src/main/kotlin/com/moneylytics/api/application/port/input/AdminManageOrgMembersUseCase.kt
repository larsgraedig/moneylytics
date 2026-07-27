package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.OrgRole

interface AdminManageOrgMembersUseCase {
    fun adminAddMember(
        orgId: Long,
        externalId: String,
        role: OrgRole,
    )

    fun adminRemoveMember(
        orgId: Long,
        externalId: String,
    )
}
