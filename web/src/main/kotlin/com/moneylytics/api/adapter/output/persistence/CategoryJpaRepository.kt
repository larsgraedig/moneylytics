package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CategoryJpaRepository : JpaRepository<CategoryEntity, Long> {
    fun findAllByOrganizationId(organizationId: Long): List<CategoryEntity>

    fun findByNameAndParentIdAndOrganizationId(
        name: String,
        parentId: Long?,
        organizationId: Long,
    ): CategoryEntity?

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): CategoryEntity?

    fun findAllByParentIdAndOrganizationId(
        parentId: Long,
        organizationId: Long,
    ): List<CategoryEntity>

    @Modifying
    @Query(
        "UPDATE CategoryEntity c SET c.parent.id = :toParentId WHERE c.parent.id = :fromParentId AND c.organization.id = :organizationId",
    )
    fun reparentChildren(
        @Param("fromParentId") fromParentId: Long,
        @Param("toParentId") toParentId: Long,
        @Param("organizationId") organizationId: Long,
    )
}
