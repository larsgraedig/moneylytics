package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import java.math.BigDecimal
import java.time.LocalDate

fun interface CreateRecurringSeriesUseCase {
    fun create(command: CreateRecurringSeriesCommand): RecurringSeries
}

data class CreateRecurringSeriesCommand(
    val userId: Long,
    val label: String,
    val type: RecurringType,
    val direction: RecurrenceDirection,
    val cadence: RecurrenceCadence,
    val expectedAmount: BigDecimal,
    val currency: String,
    val accountIban: String,
    val lastBookingDate: LocalDate?,
)
