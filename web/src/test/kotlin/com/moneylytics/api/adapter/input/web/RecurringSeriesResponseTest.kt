package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringOccurrenceDeviation
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSeriesResponseTest {
    @Test
    fun `should surface expected date, expected amount and deviation on the mapped occurrence`() {
        val occurrenceDate = LocalDate.of(2026, 1, 5)
        val expectedDate = LocalDate.of(2026, 1, 1)
        val series =
            RecurringSeries(
                id = 1L,
                label = "Netflix",
                type = RecurringType.SUBSCRIPTION,
                direction = RecurrenceDirection.EXPENSE,
                cadence = RecurrenceCadence.MONTHLY,
                intervalDays = 30,
                expectedAmount = BigDecimal("-15.99"),
                amountVariable = false,
                currency = "EUR",
                accountIban = "DE01",
                firstSeen = expectedDate.minusMonths(2),
                lastSeen = occurrenceDate,
                occurrenceCount = 3,
                nextExpectedDate = occurrenceDate.plusDays(30),
                status = RecurrenceStatus.DETECTED,
                fingerprint = "DE01|E|netflix",
                occurrences =
                    listOf(
                        RecurringOccurrence(
                            transactionId = 99L,
                            date = occurrenceDate,
                            amount = BigDecimal("-15.99"),
                            expectedDate = expectedDate,
                            expectedAmount = BigDecimal("-15.99"),
                            deviation = RecurringOccurrenceDeviation.DATE_SHIFTED,
                        ),
                    ),
            )

        val item = series.toItem()

        assertThat(item.occurrences).hasSize(1)
        val occurrence = item.occurrences[0]
        assertThat(occurrence.expectedDate).isEqualTo(expectedDate.toString())
        assertThat(occurrence.expectedAmount).isEqualByComparingTo(BigDecimal("-15.99"))
        assertThat(occurrence.deviation).isEqualTo(RecurringOccurrenceDeviation.DATE_SHIFTED)
    }

    @Test
    fun `should map null expected date, amount and deviation when not tracked`() {
        val series =
            RecurringSeries(
                id = 1L,
                label = "Netflix",
                type = RecurringType.SUBSCRIPTION,
                direction = RecurrenceDirection.EXPENSE,
                cadence = RecurrenceCadence.MONTHLY,
                intervalDays = 30,
                expectedAmount = BigDecimal("-15.99"),
                amountVariable = false,
                currency = "EUR",
                accountIban = "DE01",
                firstSeen = LocalDate.of(2025, 11, 1),
                lastSeen = LocalDate.of(2026, 1, 5),
                occurrenceCount = 3,
                nextExpectedDate = LocalDate.of(2026, 2, 4),
                status = RecurrenceStatus.DETECTED,
                fingerprint = "DE01|E|netflix",
                occurrences =
                    listOf(
                        RecurringOccurrence(transactionId = 99L, date = LocalDate.of(2026, 1, 5), amount = BigDecimal("-15.99")),
                    ),
            )

        val item = series.toItem()

        val occurrence = item.occurrences[0]
        assertThat(occurrence.expectedDate).isNull()
        assertThat(occurrence.expectedAmount).isNull()
        assertThat(occurrence.deviation).isNull()
    }
}
