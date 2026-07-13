package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RecurringSeriesMemberJpaRepository : JpaRepository<RecurringSeriesMemberEntity, Long> {
    fun findBySeriesIdIn(seriesIds: Collection<Long>): List<RecurringSeriesMemberEntity>

    @Modifying
    @Query("DELETE FROM RecurringSeriesMemberEntity m WHERE m.seriesId IN :ids")
    fun deleteBySeriesIdIn(ids: Collection<Long>)
}
