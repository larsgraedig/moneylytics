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
import kotlin.math.abs
import kotlin.math.sqrt

@Component
class RecurringSeriesDetector {
    fun detect(transactions: List<Transaction>): List<RecurringSeries> =
        transactions
            .filter { it.amount != BigDecimal.ZERO }
            .groupBy { groupKey(it) }
            .mapNotNull { (_, group) -> analyzeGroup(group) }

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
            .take(5)
            .joinToString(" ")
    }

    private fun analyzeGroup(txns: List<Transaction>): RecurringSeries? {
        if (txns.size < 2) return null

        val sorted = txns.sortedBy { it.bookingDate }
        val intervals = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.bookingDate, b.bookingDate).toInt() }
        val medianInterval = medianInt(intervals)

        val cadence = classifyCadence(medianInterval) ?: return null

        val minOccurrences =
            when (cadence) {
                RecurrenceCadence.WEEKLY, RecurrenceCadence.MONTHLY, RecurrenceCadence.QUARTERLY -> 3
                RecurrenceCadence.SEMIANNUAL, RecurrenceCadence.YEARLY -> 2
            }
        if (sorted.size < minOccurrences) return null

        val tolerance = maxOf(medianInterval * 0.25, 5.0)
        val regularCount = intervals.count { abs(it - medianInterval).toDouble() <= tolerance }
        if (regularCount < intervals.size * 0.6) return null

        val amounts = sorted.map { it.amount.abs() }
        val medianAmount = medianBigDecimal(amounts)
        val amountVariable = amounts.size > 1 && standardDeviation(amounts) / medianAmount.toDouble() > 0.15

        val direction = if (sorted.first().amount < BigDecimal.ZERO) RecurrenceDirection.EXPENSE else RecurrenceDirection.INCOME
        val type = classify(direction, cadence, medianAmount, sorted)
        val label = buildLabel(sorted.first())
        val nextExpectedDate = sorted.last().bookingDate.plusDays(medianInterval.toLong())

        return RecurringSeries(
            label = label,
            type = type,
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
            occurrences = sorted.mapNotNull { tx -> tx.id?.let { id -> RecurringOccurrence(id, tx.bookingDate, tx.amount) } },
        )
    }

    private fun classifyCadence(days: Int): RecurrenceCadence? =
        when (days) {
            in 5..10 -> RecurrenceCadence.WEEKLY
            in 25..35 -> RecurrenceCadence.MONTHLY
            in 80..100 -> RecurrenceCadence.QUARTERLY
            in 170..195 -> RecurrenceCadence.SEMIANNUAL
            in 350..380 -> RecurrenceCadence.YEARLY
            else -> null
        }

    private fun classify(
        direction: RecurrenceDirection,
        cadence: RecurrenceCadence,
        amount: BigDecimal,
        txns: List<Transaction>,
    ): RecurringType {
        val text = txns.mapNotNull { it.counterpartyName ?: it.purpose }.joinToString(" ").lowercase()

        if (direction == RecurrenceDirection.INCOME) {
            if (text.containsAny("gehalt", "lohn", "bezüge", "salary", "payroll", "entgelt")) return RecurringType.SALARY
            return RecurringType.OTHER
        }

        if (text.containsAny("miete", "rent", "vermieter", "wohnungsgeld", "hausgeld", "pacht")) return RecurringType.RENT
        if (text.containsAny(
                "versicherung",
                "insurance",
                "allianz",
                "axa",
                "generali",
                "huk",
                "ergo",
                "signal iduna",
                "zurich",
            )
        ) {
            return RecurringType.INSURANCE
        }
        if (text.containsAny("kredit", "darlehen", "tilgung", "finanzierung", "hypothek")) return RecurringType.LOAN
        if (text.containsAny(
                "strom",
                "gas",
                "wasser",
                "fernwärme",
                "stadtwerke",
                "enbw",
                "e.on",
                "rwe",
                "telekom",
                "vodafone",
                "internet",
                "o2",
                "1&1",
            )
        ) {
            return RecurringType.UTILITY
        }
        if (text.containsAny("netflix", "spotify", "amazon prime", "disney", "apple", "abo", "subscription", "mitglied") ||
            (cadence == RecurrenceCadence.MONTHLY && amount < BigDecimal("50"))
        ) {
            return RecurringType.SUBSCRIPTION
        }
        if (text.containsAny("verein", "mitgliedschaft", "club", "beitrag")) return RecurringType.MEMBERSHIP

        return RecurringType.OTHER
    }

    private fun buildLabel(first: Transaction): String =
        first.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
            ?: first.purpose
                ?.trim()
                ?.take(60)
                ?.takeIf { it.isNotEmpty() }
            ?: "Unbekannt"

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { this.contains(it) }

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
                .divide(BigDecimal("2"), 4, RoundingMode.HALF_UP)
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
