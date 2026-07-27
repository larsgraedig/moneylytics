package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.OrganizationMembership

interface ManageOrganizationMembersUseCase {
    fun getMembers(organizationId: Long): List<OrganizationMembership>

    fun addMember(
        organizationId: Long,
        email: String,
        password: String,
        role: OrgRole,
        requestingUserId: Long,
    )

    fun removeMember(
        organizationId: Long,
        targetUserId: Long,
        requestingUserId: Long,
    )

    fun updateMemberRole(
        organizationId: Long,
        targetUserId: Long,
        role: OrgRole,
        requestingUserId: Long,
    )
}
