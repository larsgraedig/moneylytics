package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RecurringExpectedSlotJpaRepository : JpaRepository<RecurringExpectedSlotEntity, Long> {
    fun findBySeriesIdIn(seriesIds: List<Long>): List<RecurringExpectedSlotEntity>

    fun deleteBySeriesIdIn(seriesIds: List<Long>)

    fun deleteBySeriesId(seriesId: Long)
}
