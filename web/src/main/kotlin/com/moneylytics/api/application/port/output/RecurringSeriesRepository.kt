package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.RecurringSeries

interface RecurringSeriesRepository {
    fun replaceAllForUser(
        series: List<RecurringSeries>,
        userId: Long,
    )

    fun findByUserId(userId: Long): List<RecurringSeries>
}
