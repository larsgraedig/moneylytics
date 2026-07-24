package com.moneylytics.api.application.service

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSeriesDetectorTest {
    private val detector = RecurringSeriesDetector()

    private val baseDate = LocalDate.of(2024, 1, 1)

    private fun expense(
        id: Long,
        amount: String,
        daysOffset: Int,
        counterpartyName: String? = null,
        purpose: String? = null,
        iban: String = "DE01",
    ) = Transaction(
        id = id,
        category = null,
        subcategory = null,
        bookingDate = baseDate.plusDays(daysOffset.toLong()),
        valueDate = baseDate.plusDays(daysOffset.toLong()),
        accountingDate = baseDate.plusDays(daysOffset.toLong()),
        amount = BigDecimal(amount),
        currency = "EUR",
        accountIban = iban,
        counterpartyName = counterpartyName,
        purpose = purpose,
    )

    @Test
    fun `should detect monthly recurring series`() {
        val transactions =
            listOf(
                expense(1L, "-50.00", 0, counterpartyName = "Netflix"),
                expense(2L, "-50.00", 31, counterpartyName = "Netflix"),
                expense(3L, "-50.00", 62, counterpartyName = "Netflix"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].cadence).isEqualTo(RecurrenceCadence.MONTHLY)
        assertThat(result[0].direction).isEqualTo(RecurrenceDirection.EXPENSE)
        assertThat(result[0].occurrenceCount).isEqualTo(3)
        assertThat(result[0].label).isEqualTo("Netflix")
    }

    @Test
    fun `should discard series with too few occurrences for monthly cadence`() {
        val transactions =
            listOf(
                expense(1L, "-100.00", 0, counterpartyName = "Rent"),
                expense(2L, "-100.00", 30, counterpartyName = "Rent"),
            )

        val result = detector.detect(transactions)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should accept two occurrences for yearly cadence`() {
        val transactions =
            listOf(
                expense(1L, "-200.00", 0, counterpartyName = "Insurance"),
                expense(2L, "-200.00", 365, counterpartyName = "Insurance"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].cadence).isEqualTo(RecurrenceCadence.YEARLY)
    }

    @Test
    fun `should discard irregular series where more than 40 percent of intervals deviate`() {
        val transactions =
            listOf(
                expense(1L, "-50.00", 0, counterpartyName = "Irregular"),
                expense(2L, "-50.00", 30, counterpartyName = "Irregular"),
                expense(3L, "-50.00", 90, counterpartyName = "Irregular"),
                expense(4L, "-50.00", 185, counterpartyName = "Irregular"),
            )

        val result = detector.detect(transactions)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should detect variable amount when standard deviation exceeds 15 percent`() {
        val transactions =
            listOf(
                expense(1L, "-100.00", 0, counterpartyName = "Stadtwerke"),
                expense(2L, "-140.00", 31, counterpartyName = "Stadtwerke"),
                expense(3L, "-160.00", 62, counterpartyName = "Stadtwerke"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].amountVariable).isTrue()
    }

    @Test
    fun `should group transactions by counterparty IBAN when available`() {
        val transactions =
            listOf(
                expense(1L, "-50.00", 0, counterpartyName = "Netflix Old Name").copy(counterpartyIban = "DE99"),
                expense(2L, "-50.00", 31, counterpartyName = "Netflix New Name").copy(counterpartyIban = "DE99"),
                expense(3L, "-50.00", 62, counterpartyName = "Netflix Different").copy(counterpartyIban = "DE99"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].occurrenceCount).isEqualTo(3)
    }

    @Test
    fun `should fall back to normalised purpose when no counterparty available`() {
        val transactions =
            listOf(
                expense(1L, "-12.99", 0, purpose = "Spotify Premium Abo"),
                expense(2L, "-12.99", 31, purpose = "Spotify Premium Abo"),
                expense(3L, "-12.99", 62, purpose = "Spotify Premium Abo"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].occurrenceCount).isEqualTo(3)
    }

    @Test
    fun `should not mix transactions from different accounts into the same series`() {
        val transactions =
            listOf(
                expense(1L, "-50.00", 0, counterpartyName = "Shop", iban = "DE01"),
                expense(2L, "-50.00", 31, counterpartyName = "Shop", iban = "DE01"),
                expense(3L, "-50.00", 62, counterpartyName = "Shop", iban = "DE01"),
                expense(4L, "-50.00", 0, counterpartyName = "Shop", iban = "DE02"),
                expense(5L, "-50.00", 31, counterpartyName = "Shop", iban = "DE02"),
                expense(6L, "-50.00", 62, counterpartyName = "Shop", iban = "DE02"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.accountIban }.toSet()).containsExactlyInAnyOrder("DE01", "DE02")
    }

    @Test
    fun `should reject series where the same vendor has multiple transactions in a single cadence period`() {
        // Jan 3, Jan 28 (same month!), Feb 25, Mar 27 — median interval 28d → MONTHLY
        // but January has two occurrences (33% of periods) → rejected
        val transactions =
            listOf(
                expense(1L, "-50.00", 2, counterpartyName = "REWE"),
                expense(2L, "-50.00", 27, counterpartyName = "REWE"),
                expense(3L, "-50.00", 55, counterpartyName = "REWE"),
                expense(4L, "-50.00", 86, counterpartyName = "REWE"),
            )

        val result = detector.detect(transactions)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should mark series with high amount variance as amountVariable`() {
        // The detector marks variable amounts; the service layer decides whether to keep or reject
        val transactions =
            listOf(
                expense(1L, "-35.00", 0, counterpartyName = "Tankstelle"),
                expense(2L, "-52.00", 31, counterpartyName = "Tankstelle"),
                expense(3L, "-48.00", 62, counterpartyName = "Tankstelle"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].amountVariable).isTrue()
    }
}
