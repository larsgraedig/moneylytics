package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryClassifierClassCountJpaRepository : JpaRepository<CategoryClassifierClassCountEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<CategoryClassifierClassCountEntity>

    fun findByOrganizationIdAndCategoryId(
        organizationId: Long,
        categoryId: Long,
    ): CategoryClassifierClassCountEntity?

    fun existsByOrganizationId(organizationId: Long): Boolean
}

interface CategoryClassifierTokenCountJpaRepository : JpaRepository<CategoryClassifierTokenCountEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<CategoryClassifierTokenCountEntity>

    fun findByOrganizationIdAndCategoryIdAndToken(
        organizationId: Long,
        categoryId: Long,
        token: String,
    ): CategoryClassifierTokenCountEntity?

    fun findByOrganizationIdAndCategoryId(
        organizationId: Long,
        categoryId: Long,
    ): List<CategoryClassifierTokenCountEntity>
}
