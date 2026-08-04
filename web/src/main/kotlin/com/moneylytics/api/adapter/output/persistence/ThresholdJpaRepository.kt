package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.ThresholdPeriod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ThresholdJpaRepository : JpaRepository<ThresholdEntity, Long> {
    fun findByOrganizationIdAndCategoryEntityIsNotNull(organizationId: Long): List<ThresholdEntity>

    fun findByOrganizationIdAndCategoryEntityIdAndPeriod(
        organizationId: Long,
        categoryEntityId: Long,
        period: ThresholdPeriod,
    ): ThresholdEntity?

    fun existsByOrganizationIdAndCategoryEntityId(
        organizationId: Long,
        categoryEntityId: Long,
    ): Boolean

    @Modifying
    @Query("DELETE FROM ThresholdEntity t WHERE t.id = :id AND t.organization.id = :organizationId")
    fun deleteByIdAndOrganizationId(
        @Param("id") id: Long,
        @Param("organizationId") organizationId: Long,
    )
}
