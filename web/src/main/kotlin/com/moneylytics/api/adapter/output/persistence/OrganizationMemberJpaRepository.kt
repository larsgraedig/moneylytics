package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrganizationMemberJpaRepository : JpaRepository<OrganizationMemberEntity, OrganizationMemberId> {
    fun findByUserId(userId: Long): List<OrganizationMemberEntity>

    fun findByOrganizationId(organizationId: Long): List<OrganizationMemberEntity>

    fun findByOrganizationIdAndUserId(
        organizationId: Long,
        userId: Long,
    ): OrganizationMemberEntity?

    fun existsByOrganizationIdAndUserId(
        organizationId: Long,
        userId: Long,
    ): Boolean

    @Modifying
    @Query("DELETE FROM OrganizationMemberEntity m WHERE m.organization.id = :orgId AND m.user.id = :userId")
    fun deleteByOrganizationIdAndUserId(
        @Param("orgId") orgId: Long,
        @Param("userId") userId: Long,
    )
}
