package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType

fun interface GetRecurringSeriesUseCase {
    fun getRecurringSeries(query: GetRecurringSeriesQuery): List<RecurringSeries>
}

data class GetRecurringSeriesQuery(
    val userId: Long,
    val direction: RecurrenceDirection? = null,
    val type: RecurringType? = null,
)
