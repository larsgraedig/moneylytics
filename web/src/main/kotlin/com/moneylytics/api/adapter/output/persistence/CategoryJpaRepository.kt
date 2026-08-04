package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

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
}
