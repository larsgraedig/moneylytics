package com.moneylytics.api.application.port.input

import java.math.BigDecimal
import java.time.LocalDate

fun interface GetCalendarSumsUseCase {
    fun getCalendarSums(query: CalendarSumsQuery): CalendarSumsResponse
}

data class CalendarSumsQuery(
    val from: LocalDate,
    val to: LocalDate,
    val organizationId: Long,
    val accountIban: String? = null,
)

data class CalendarSumsResponse(
    val data: List<CalendarDaySum>,
)

data class CalendarDaySum(
    val day: String,
    val value: BigDecimal,
)
