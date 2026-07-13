package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RecurringSeriesJpaRepository : JpaRepository<RecurringSeriesEntity, Long> {
    fun findByUserId(userId: Long): List<RecurringSeriesEntity>

    @Modifying
    @Query("DELETE FROM RecurringSeriesEntity s WHERE s.user.id = :userId")
    fun deleteByUserId(userId: Long)
}
