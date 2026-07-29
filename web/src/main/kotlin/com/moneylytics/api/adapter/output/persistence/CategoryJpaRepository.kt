package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryJpaRepository : JpaRepository<CategoryEntity, Long> {
    fun findAllByOrganizationId(organizationId: Long): List<CategoryEntity>

    fun findByNameAndSubcategoryAndGroupNameAndOrganizationId(
        name: String,
        subcategory: String?,
        groupName: String,
        organizationId: Long,
    ): CategoryEntity?
}
