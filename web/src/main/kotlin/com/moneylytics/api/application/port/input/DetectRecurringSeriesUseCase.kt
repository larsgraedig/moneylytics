package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.RecurringSeries

fun interface DetectRecurringSeriesUseCase {
    fun detect(command: RefreshRecurringSeriesCommand): List<RecurringSeries>
}

data class RefreshRecurringSeriesCommand(
    val userId: Long,
    val lookbackMonths: Long = 24,
)
