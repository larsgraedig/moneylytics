package com.moneylytics.api.domain

import java.math.BigDecimal
import java.time.LocalDate

data class RecurringOccurrence(
    val transactionId: Long,
    val date: LocalDate,
    val amount: BigDecimal,
)

data class RecurringSeries(
    val id: Long? = null,
    val label: String,
    val type: RecurringType,
    val direction: RecurrenceDirection,
    val cadence: RecurrenceCadence,
    val intervalDays: Int,
    val expectedAmount: BigDecimal,
    val amountVariable: Boolean,
    val currency: String,
    val accountIban: String,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
    val occurrenceCount: Int,
    val nextExpectedDate: LocalDate,
    val status: RecurrenceStatus,
    val fingerprint: String = "",
    val isFalsePositive: Boolean = false,
    val occurrences: List<RecurringOccurrence> = emptyList(),
    val deviation: RecurrenceDeviation = RecurrenceDeviation.ON_TRACK,
)
