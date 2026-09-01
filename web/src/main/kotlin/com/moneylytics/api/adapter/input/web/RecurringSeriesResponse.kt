package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class RecurringOccurrenceItem(
    val transactionId: Long,
    val date: String,
    val amount: BigDecimal,
    val purpose: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
)

data class ExpectedSlotItem(
    val expectedDate: String,
    val matched: Boolean,
    val transactionId: Long?,
    val date: String?,
    val amount: BigDecimal?,
    val counterpartyName: String?,
    val purpose: String?,
    val predictedAmount: BigDecimal?,
    val predictedAmountMin: BigDecimal?,
    val predictedAmountMax: BigDecimal?,
)

private data class AmountPrediction(
    val median: BigDecimal,
    val min: BigDecimal,
    val max: BigDecimal,
)

private fun computeAmountPrediction(amounts: List<BigDecimal>): AmountPrediction? {
    if (amounts.isEmpty()) return null
    val sorted = amounts.sorted()
    val n = sorted.size
    val median =
        if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]).divide(BigDecimal.TWO, sorted[0].scale(), RoundingMode.HALF_UP)
        }
    return AmountPrediction(median = median, min = sorted.first(), max = sorted.last())
}

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
    val expectedSlots: List<ExpectedSlotItem>,
)

fun RecurringSeries.toItem(): RecurringSeriesItem {
    val today = LocalDate.now()
    return RecurringSeriesItem(
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
        expectedSlots = buildExpectedSlots(today),
    )
}

// Rolling anchor mirrors the same logic used by RecurringSlotAssigner so expected dates stay in sync.
private fun RecurringSeries.buildExpectedSlots(today: LocalDate): List<ExpectedSlotItem> {
    val slotsByExpectedDate = expectedSlots.associateBy { it.expectedDate }
    val prediction = computeAmountPrediction(expectedSlots.map { it.amount })
    val result = mutableListOf<ExpectedSlotItem>()
    var anchor = firstSeen

    while (!anchor.isAfter(today)) {
        val matched = slotsByExpectedDate[anchor]
        result.add(
            if (matched != null) {
                ExpectedSlotItem(
                    expectedDate = anchor.toString(),
                    matched = true,
                    transactionId = matched.transactionId,
                    date = matched.date.toString(),
                    amount = matched.amount,
                    counterpartyName = matched.counterpartyName,
                    purpose = matched.purpose,
                    predictedAmount = null,
                    predictedAmountMin = null,
                    predictedAmountMax = null,
                )
            } else {
                ExpectedSlotItem(
                    expectedDate = anchor.toString(),
                    matched = false,
                    transactionId = null,
                    date = null,
                    amount = null,
                    counterpartyName = null,
                    purpose = null,
                    predictedAmount = prediction?.median,
                    predictedAmountMin = prediction?.min,
                    predictedAmountMax = prediction?.max,
                )
            },
        )
        anchor =
            if (matched != null) {
                matched.date.plusDays(intervalDays.toLong())
            } else {
                anchor.plusDays(intervalDays.toLong())
            }
    }

    return result.sortedByDescending { it.expectedDate }
}
