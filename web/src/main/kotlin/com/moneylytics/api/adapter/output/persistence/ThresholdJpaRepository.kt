package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ThresholdPeriod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ThresholdJpaRepository : JpaRepository<ThresholdEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<ThresholdEntity>

    fun findByOrganizationIdAndCategoryAndSubcategoryIsNullAndPeriod(
        organizationId: Long,
        category: String,
        period: ThresholdPeriod,
    ): ThresholdEntity?

    fun findByOrganizationIdAndCategoryAndSubcategoryAndPeriod(
        organizationId: Long,
        category: String,
        subcategory: String,
        period: ThresholdPeriod,
    ): ThresholdEntity?

    @Modifying
    @Query("DELETE FROM ThresholdEntity t WHERE t.id = :id AND t.organization.id = :organizationId")
    fun deleteByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    )
}
