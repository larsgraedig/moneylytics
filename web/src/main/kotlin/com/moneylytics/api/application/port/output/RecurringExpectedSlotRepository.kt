package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringExpectedSlot

interface RecurringExpectedSlotRepository {
    fun replaceSlots(
        seriesId: Long,
        slots: List<RecurringExpectedSlot>,
    )

    fun findBySeriesIds(seriesIds: List<Long>): Map<Long, List<RecurringExpectedSlot>>

    fun deleteBySeriesIds(seriesIds: List<Long>)
}
