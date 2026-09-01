package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.RecurringExpectedSlotRepository
import com.moneylytics.api.domain.RecurringExpectedSlot
import com.moneylytics.api.domain.RecurringSeries
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Service
class RecurringSlotAssigner(
    private val expectedSlotRepository: RecurringExpectedSlotRepository,
) {
    companion object {
        private const val MIN_GRACE_DAYS = 3
        private const val GRACE_PERIOD_FACTOR = 0.15
    }

    fun computeAndPersist(
        series: RecurringSeries,
        today: LocalDate = LocalDate.now(),
    ) {
        val seriesId = series.id ?: return
        val slots = computeSlots(series, today)
        expectedSlotRepository.replaceSlots(seriesId, slots)
    }

    fun computeSlots(
        series: RecurringSeries,
        today: LocalDate = LocalDate.now(),
    ): List<RecurringExpectedSlot> {
        if (series.occurrences.isEmpty()) return emptyList()

        val grace = maxOf(MIN_GRACE_DAYS, (series.intervalDays * GRACE_PERIOD_FACTOR).toInt()).toLong()
        val sortedOccurrences = series.occurrences.sortedBy { it.date }
        val usedIds = mutableSetOf<Long>()
        val slots = mutableListOf<RecurringExpectedSlot>()

        // Rolling anchor: re-anchor from each matched occurrence to avoid drift accumulation
        var anchor = series.firstSeen
        while (!anchor.isAfter(today)) {
            val match =
                sortedOccurrences
                    .filter { it.transactionId !in usedIds }
                    .filter { abs(ChronoUnit.DAYS.between(anchor, it.date)) <= grace }
                    .minByOrNull { abs(ChronoUnit.DAYS.between(anchor, it.date)) }

            if (match != null) {
                usedIds.add(match.transactionId)
                slots.add(
                    RecurringExpectedSlot(
                        expectedDate = anchor,
                        transactionId = match.transactionId,
                        date = match.date,
                        amount = match.amount,
                        counterpartyName = match.counterpartyName,
                        purpose = match.purpose,
                    ),
                )
                anchor = match.date.plusDays(series.intervalDays.toLong())
            } else {
                anchor = anchor.plusDays(series.intervalDays.toLong())
            }
        }

        return slots
    }
}
