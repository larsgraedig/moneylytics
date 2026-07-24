package com.moneylytics.api.application.service

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.sqrt

@Component
class RecurringSeriesDetector {
    private companion object {
        const val TOLERANCE_FACTOR = 0.25
        const val MIN_TOLERANCE_DAYS = 5.0
        const val MIN_REGULARITY_RATIO = 0.6
        const val AMOUNT_VARIABILITY_THRESHOLD = 0.15
        const val MAX_LABEL_LENGTH = 60
        const val MAX_DUPLICATE_PERIOD_RATIO = 0.2
        const val MEDIAN_SCALE = 4
        val WEEKLY_RANGE = 5..10
        val MONTHLY_RANGE = 25..35
        val QUARTERLY_RANGE = 80..100
        val SEMIANNUAL_RANGE = 170..195
        val YEARLY_RANGE = 350..380
        const val MIN_OCCURRENCES_FREQUENT = 3
        const val MIN_OCCURRENCES_SPARSE = 2
        const val MAX_PURPOSE_WORDS = 5
    }

    fun detect(transactions: List<Transaction>): List<RecurringSeries> =
        transactions
            .filter { it.amount != BigDecimal.ZERO }
            .groupBy { groupKey(it) }
            .mapNotNull { (key, group) -> analyzeGroup(group, key) }

    private fun groupKey(tx: Transaction): String {
        val direction = if (tx.amount < BigDecimal.ZERO) "E" else "I"
        val identifier =
            tx.counterpartyIban
                ?: tx.counterpartyName?.let { normalizeName(it) }
                ?: tx.purpose?.let { normalizePurpose(it) }
                ?: "unknown"
        return "${tx.accountIban}|$direction|$identifier"
    }

    private fun normalizeName(name: String): String =
        name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun normalizePurpose(purpose: String): String {
        var s = purpose.trim().lowercase()
        s = s.replace(Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b"), "")
        s = s.replace(Regex("\\b\\d+[.,]\\d+\\b"), "")
        s = s.replace(Regex("\\b[a-z]{0,2}\\d{4,}\\b"), "")
        s = s.replace(Regex("[^a-z0-9 ]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
            .split(" ")
            .filter { it.length > 2 }
            .take(MAX_PURPOSE_WORDS)
            .joinToString(" ")
    }

    private fun analyzeGroup(
        txns: List<Transaction>,
        fingerprint: String,
    ): RecurringSeries? {
        if (txns.size < 2) return null

        val sorted = txns.sortedBy { it.bookingDate }
        val intervals = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.bookingDate, b.bookingDate).toInt() }
        val medianInterval = medianInt(intervals)

        val cadence = classifyCadence(medianInterval) ?: return null

        val minOccurrences =
            when (cadence) {
                RecurrenceCadence.WEEKLY, RecurrenceCadence.MONTHLY, RecurrenceCadence.QUARTERLY -> MIN_OCCURRENCES_FREQUENT
                RecurrenceCadence.SEMIANNUAL, RecurrenceCadence.YEARLY -> MIN_OCCURRENCES_SPARSE
            }
        if (sorted.size < minOccurrences) return null

        val tolerance = maxOf(medianInterval * TOLERANCE_FACTOR, MIN_TOLERANCE_DAYS)
        val regularCount = intervals.count { abs(it - medianInterval).toDouble() <= tolerance }
        if (regularCount < intervals.size * MIN_REGULARITY_RATIO) return null

        if (cadence == RecurrenceCadence.WEEKLY || cadence == RecurrenceCadence.MONTHLY) {
            if (!hasAtMostOnePerPeriod(sorted, cadence)) return null
        }

        val amounts = sorted.map { it.amount.abs() }
        val medianAmount = medianBigDecimal(amounts)
        val amountVariable = amounts.size > 1 && standardDeviation(amounts) / medianAmount.toDouble() > AMOUNT_VARIABILITY_THRESHOLD
        val direction = if (sorted.first().amount < BigDecimal.ZERO) RecurrenceDirection.EXPENSE else RecurrenceDirection.INCOME
        val label = buildLabel(sorted.first())
        val nextExpectedDate = sorted.last().bookingDate.plusDays(medianInterval.toLong())

        return RecurringSeries(
            label = label,
            type = RecurringType.OTHER,
            direction = direction,
            cadence = cadence,
            intervalDays = medianInterval,
            expectedAmount = medianAmount,
            amountVariable = amountVariable,
            currency = sorted.first().currency,
            accountIban = sorted.first().accountIban,
            firstSeen = sorted.first().bookingDate,
            lastSeen = sorted.last().bookingDate,
            occurrenceCount = sorted.size,
            nextExpectedDate = nextExpectedDate,
            status = RecurrenceStatus.DETECTED,
            fingerprint = fingerprint,
            occurrences =
                sorted.mapNotNull { tx ->
                    tx.id?.let { id ->
                        RecurringOccurrence(
                            transactionId = id,
                            date = tx.bookingDate,
                            amount = tx.amount,
                            purpose = tx.purpose,
                            counterpartyName = tx.counterpartyName,
                            counterpartyIban = tx.counterpartyIban,
                        )
                    }
                },
        )
    }

    private fun classifyCadence(days: Int): RecurrenceCadence? =
        when (days) {
            in WEEKLY_RANGE -> RecurrenceCadence.WEEKLY
            in MONTHLY_RANGE -> RecurrenceCadence.MONTHLY
            in QUARTERLY_RANGE -> RecurrenceCadence.QUARTERLY
            in SEMIANNUAL_RANGE -> RecurrenceCadence.SEMIANNUAL
            in YEARLY_RANGE -> RecurrenceCadence.YEARLY
            else -> null
        }

    private fun buildLabel(first: Transaction): String =
        first.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
            ?: first.purpose
                ?.trim()
                ?.take(MAX_LABEL_LENGTH)
                ?.takeIf { it.isNotEmpty() }
            ?: "Unbekannt"

    private fun hasAtMostOnePerPeriod(
        sorted: List<Transaction>,
        cadence: RecurrenceCadence,
    ): Boolean {
        val key: (Transaction) -> String =
            when (cadence) {
                RecurrenceCadence.WEEKLY -> { tx ->
                    val woy = tx.bookingDate.get(WeekFields.ISO.weekOfWeekBasedYear())
                    "${tx.bookingDate.year}-$woy"
                }
                RecurrenceCadence.MONTHLY -> { tx -> "${tx.bookingDate.year}-${tx.bookingDate.monthValue}" }
                else -> return true
            }
        val periods = sorted.groupBy(key)
        val duplicatePeriods = periods.values.count { it.size > 1 }
        return duplicatePeriods.toDouble() / periods.size <= MAX_DUPLICATE_PERIOD_RATIO
    }

    private fun medianInt(values: List<Int>): Int {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        } else {
            sorted[sorted.size / 2]
        }
    }

    private fun medianBigDecimal(values: List<BigDecimal>): BigDecimal {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2])
                .divide(BigDecimal("2"), MEDIAN_SCALE, RoundingMode.HALF_UP)
        } else {
            sorted[sorted.size / 2]
        }
    }

    private fun standardDeviation(values: List<BigDecimal>): Double {
        if (values.size < 2) return 0.0
        val mean = values.sumOf { it.toDouble() } / values.size
        val variance = values.sumOf { v -> (v.toDouble() - mean).let { d -> d * d } } / (values.size - 1)
        return sqrt(variance)
    }
}
