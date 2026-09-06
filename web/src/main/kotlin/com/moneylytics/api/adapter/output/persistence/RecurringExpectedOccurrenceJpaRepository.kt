package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RecurringExpectedOccurrenceJpaRepository : JpaRepository<RecurringExpectedOccurrenceEntity, Long> {
    fun findBySeriesIdAndMatchedTransactionIdIsNull(seriesId: Long): RecurringExpectedOccurrenceEntity?

    fun findBySeriesIdIn(seriesIds: Collection<Long>): List<RecurringExpectedOccurrenceEntity>

    @Modifying
    @Query("DELETE FROM RecurringExpectedOccurrenceEntity e WHERE e.seriesId IN :ids")
    fun deleteBySeriesIdIn(ids: Collection<Long>)
}
