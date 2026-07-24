package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.RecurrenceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RecurringSeriesJpaRepository : JpaRepository<RecurringSeriesEntity, Long> {
    fun findByUserId(userId: Long): List<RecurringSeriesEntity>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): RecurringSeriesEntity?

    @Modifying
    @Query("DELETE FROM RecurringSeriesEntity s WHERE s.user.id = :userId AND s.status = :status")
    fun deleteByUserIdAndStatus(
        userId: Long,
        status: RecurrenceStatus,
    )
}
