package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.RecurringType

fun interface CorrectRecurringSeriesTypeUseCase {
    fun correctType(command: CorrectRecurringSeriesTypeCommand)
}

data class CorrectRecurringSeriesTypeCommand(
    val seriesId: Long,
    val userId: Long,
    val type: RecurringType,
)
