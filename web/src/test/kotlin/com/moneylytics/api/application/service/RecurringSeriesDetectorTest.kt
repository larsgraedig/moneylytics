package com.moneylytics.api.application.service

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurringType
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
                expense(2L, "-50.00", 30, counterpartyName = "Netflix"),
                expense(3L, "-50.00", 60, counterpartyName = "Netflix"),
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
                expense(1L, "-100.00", 0, counterpartyName = "Utility"),
                expense(2L, "-140.00", 30, counterpartyName = "Utility"),
                expense(3L, "-160.00", 60, counterpartyName = "Utility"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].amountVariable).isTrue()
    }

    @Test
    fun `should classify monthly income with salary keywords as SALARY`() {
        val transactions =
            (0..3).map { i ->
                Transaction(
                    id = i.toLong() + 1,
                    category = null,
                    subcategory = null,
                    bookingDate = baseDate.plusMonths(i.toLong()),
                    valueDate = baseDate.plusMonths(i.toLong()),
                    accountingDate = baseDate.plusMonths(i.toLong()),
                    amount = BigDecimal("3500.00"),
                    currency = "EUR",
                    accountIban = "DE01",
                    purpose = "Gehalt Dezember Arbeitgeber GmbH",
                )
            }

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].type).isEqualTo(RecurringType.SALARY)
        assertThat(result[0].direction).isEqualTo(RecurrenceDirection.INCOME)
    }

    @Test
    fun `should classify rent based on counterparty name keyword`() {
        val transactions =
            listOf(
                expense(1L, "-900.00", 0, counterpartyName = "Vermieter Müller"),
                expense(2L, "-900.00", 30, counterpartyName = "Vermieter Müller"),
                expense(3L, "-900.00", 60, counterpartyName = "Vermieter Müller"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(1)
        assertThat(result[0].type).isEqualTo(RecurringType.RENT)
    }

    @Test
    fun `should group transactions by counterparty IBAN when available`() {
        val transactions =
            listOf(
                expense(1L, "-50.00", 0, counterpartyName = "Netflix Old Name").copy(counterpartyIban = "DE99"),
                expense(2L, "-50.00", 30, counterpartyName = "Netflix New Name").copy(counterpartyIban = "DE99"),
                expense(3L, "-50.00", 60, counterpartyName = "Netflix Different").copy(counterpartyIban = "DE99"),
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
                expense(2L, "-12.99", 30, purpose = "Spotify Premium Abo"),
                expense(3L, "-12.99", 60, purpose = "Spotify Premium Abo"),
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
                expense(2L, "-50.00", 30, counterpartyName = "Shop", iban = "DE01"),
                expense(3L, "-50.00", 60, counterpartyName = "Shop", iban = "DE01"),
                expense(4L, "-50.00", 0, counterpartyName = "Shop", iban = "DE02"),
                expense(5L, "-50.00", 30, counterpartyName = "Shop", iban = "DE02"),
                expense(6L, "-50.00", 60, counterpartyName = "Shop", iban = "DE02"),
            )

        val result = detector.detect(transactions)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.accountIban }.toSet()).containsExactlyInAnyOrder("DE01", "DE02")
    }
}
