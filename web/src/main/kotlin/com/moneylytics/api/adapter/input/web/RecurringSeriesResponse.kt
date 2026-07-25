package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import java.math.BigDecimal

data class RecurringOccurrenceItem(
    val transactionId: Long,
    val date: String,
    val amount: BigDecimal,
    val purpose: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
)

data class RecurringSeriesItem(
    val id: Long?,
    val label: String,
    val type: RecurringType,
    val direction: RecurrenceDirection,
    val cadence: RecurrenceCadence,
    val intervalDays: Int,
    val expectedAmount: BigDecimal,
    val amountVariable: Boolean,
    val currency: String,
    val accountIban: String,
    val firstSeen: String,
    val lastSeen: String,
    val occurrenceCount: Int,
    val nextExpectedDate: String,
    val status: RecurrenceStatus,
    val fingerprint: String,
    val isFalsePositive: Boolean,
    val deviation: RecurrenceDeviation,
    val occurrences: List<RecurringOccurrenceItem>,
)

fun RecurringSeries.toItem() =
    RecurringSeriesItem(
        id = id,
        label = label,
        type = type,
        direction = direction,
        cadence = cadence,
        intervalDays = intervalDays,
        expectedAmount = expectedAmount,
        amountVariable = amountVariable,
        currency = currency,
        accountIban = accountIban,
        firstSeen = firstSeen.toString(),
        lastSeen = lastSeen.toString(),
        occurrenceCount = occurrenceCount,
        nextExpectedDate = nextExpectedDate.toString(),
        status = status,
        fingerprint = fingerprint,
        isFalsePositive = isFalsePositive,
        deviation = deviation,
        occurrences =
            occurrences.map {
                RecurringOccurrenceItem(
                    transactionId = it.transactionId,
                    date = it.date.toString(),
                    amount = it.amount,
                    purpose = it.purpose,
                    counterpartyName = it.counterpartyName,
                    counterpartyIban = it.counterpartyIban,
                )
            },
    )
