package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.Organization
import com.moneylytics.api.domain.OrganizationMembership

interface OrganizationRepository {
    fun save(name: String): Organization

    fun findById(id: Long): Organization?

    fun findByMemberUserId(userId: Long): List<OrganizationMembership>

    fun findMembersByOrganizationId(organizationId: Long): List<OrganizationMembership>

    fun addMember(
        organizationId: Long,
        userId: Long,
        role: OrgRole,
    )

    fun removeMember(
        organizationId: Long,
        userId: Long,
    )

    fun updateMemberRole(
        organizationId: Long,
        userId: Long,
        role: OrgRole,
    )

    fun isMember(
        organizationId: Long,
        userId: Long,
    ): Boolean

    fun getMemberRole(
        organizationId: Long,
        userId: Long,
    ): OrgRole?

    fun findAllOrganizationIds(): Set<Long>
}
