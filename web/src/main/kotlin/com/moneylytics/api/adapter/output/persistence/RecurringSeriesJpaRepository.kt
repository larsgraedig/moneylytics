package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.RecurrenceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecurringSeriesJpaRepository : JpaRepository<RecurringSeriesEntity, Long> {
    fun findByOrganizationId(organizationId: Long): List<RecurringSeriesEntity>

    @Query("SELECT DISTINCT e.organization.id FROM RecurringSeriesEntity e")
    fun findDistinctOrganizationIds(): List<Long>

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): RecurringSeriesEntity?

    @Modifying
    @Query("DELETE FROM RecurringSeriesEntity s WHERE s.organization.id = :organizationId AND s.status = :status")
    fun deleteByOrganizationIdAndStatus(
        @Param("organizationId") organizationId: Long,
        @Param("status") status: RecurrenceStatus,
    )
}
