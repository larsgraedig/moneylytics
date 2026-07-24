package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType

interface RecurringSeriesRepository {
    fun replaceAllForUser(
        series: List<RecurringSeries>,
        userId: Long,
    )

    fun findByUserId(userId: Long): List<RecurringSeries>

    fun updateType(
        seriesId: Long,
        type: RecurringType,
    )
}
