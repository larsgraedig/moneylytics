package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSeriesServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val recurringSeriesRepository: RecurringSeriesRepository = mock()
    private val detector: RecurringSeriesDetector = mock()
    private val service = RecurringSeriesService(transactionRepository, recurringSeriesRepository, detector)

    private val userId = 1L
    private val today = LocalDate.now()

    private fun series(
        nextExpectedDate: LocalDate,
        lastOccurrenceDate: LocalDate = nextExpectedDate.minusDays(30),
        lastOccurrenceAmount: BigDecimal = BigDecimal("-50.00"),
        expectedAmount: BigDecimal = BigDecimal("-50.00"),
        intervalDays: Int = 30,
        occurrenceCount: Int = 3,
    ) = RecurringSeries(
        id = 1L,
        label = "Test",
        type = RecurringType.SUBSCRIPTION,
        direction = RecurrenceDirection.EXPENSE,
        cadence = RecurrenceCadence.MONTHLY,
        intervalDays = intervalDays,
        expectedAmount = expectedAmount,
        amountVariable = false,
        currency = "EUR",
        accountIban = "DE01",
        firstSeen = lastOccurrenceDate.minusMonths(2),
        lastSeen = lastOccurrenceDate,
        occurrenceCount = occurrenceCount,
        nextExpectedDate = nextExpectedDate,
        status = RecurrenceStatus.DETECTED,
        occurrences =
            listOf(
                RecurringOccurrence(1L, lastOccurrenceDate.minusDays(30), BigDecimal("-50.00")),
                RecurringOccurrence(2L, lastOccurrenceDate, lastOccurrenceAmount),
            ),
    )

    @Test
    fun `should load 24 months of history when detecting`() {
        val command = RefreshRecurringSeriesCommand(userId = userId)
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(emptyList())

        service.detect(command)

        val fromCaptor = argumentCaptor<LocalDate>()
        val toCaptor = argumentCaptor<LocalDate>()
        verify(transactionRepository).findByAccountingDateBetween(fromCaptor.capture(), toCaptor.capture(), any(), anyOrNull())
        assertThat(toCaptor.firstValue).isAfterOrEqualTo(fromCaptor.firstValue.plusMonths(23))
    }

    @Test
    fun `should persist detected series for user`() {
        val command = RefreshRecurringSeriesCommand(userId = userId)
        val detectedSeries = listOf(series(today.plusDays(5)))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)

        service.detect(command)

        verify(recurringSeriesRepository).replaceAllForUser(detectedSeries, userId)
    }

    @Test
    fun `should return ON_TRACK when next expected date is in the future`() {
        val futureSeries = series(nextExpectedDate = today.plusDays(5))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(futureSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(userId = userId))

        assertThat(result).hasSize(1)
        assertThat(result[0].deviation).isEqualTo(RecurrenceDeviation.ON_TRACK)
    }

    @Test
    fun `should return OVERDUE when next expected date has passed beyond grace period`() {
        val overdueSeries = series(nextExpectedDate = today.minusDays(10), intervalDays = 30)
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(overdueSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(userId = userId))

        assertThat(result).hasSize(1)
        assertThat(result[0].deviation).isEqualTo(RecurrenceDeviation.OVERDUE)
    }

    @Test
    fun `should return AMOUNT_CHANGED when last occurrence deviates more than 15 percent`() {
        val changedAmount = BigDecimal("-70.00")
        val amountChangedSeries =
            series(
                nextExpectedDate = today.plusDays(5),
                lastOccurrenceAmount = changedAmount,
                expectedAmount = BigDecimal("-50.00"),
                occurrenceCount = 4,
            )
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(amountChangedSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(userId = userId))

        assertThat(result).hasSize(1)
        assertThat(result[0].deviation).isEqualTo(RecurrenceDeviation.AMOUNT_CHANGED)
    }

    @Test
    fun `should filter by direction when specified`() {
        val expense =
            series(today.plusDays(5)).copy(direction = RecurrenceDirection.EXPENSE)
        val income =
            series(today.plusDays(5)).copy(id = 2L, direction = RecurrenceDirection.INCOME)
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(expense, income))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(userId = userId, direction = RecurrenceDirection.EXPENSE))

        assertThat(result).hasSize(1)
        assertThat(result[0].direction).isEqualTo(RecurrenceDirection.EXPENSE)
    }

    @Test
    fun `should filter by type when specified`() {
        val subscription = series(today.plusDays(5)).copy(type = RecurringType.SUBSCRIPTION)
        val rent = series(today.plusDays(5)).copy(id = 2L, type = RecurringType.RENT)
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(subscription, rent))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(userId = userId, type = RecurringType.RENT))

        assertThat(result).hasSize(1)
        assertThat(result[0].type).isEqualTo(RecurringType.RENT)
    }

    private fun tx(iban: String = "DE01") =
        Transaction(
            category = null,
            subcategory = null,
            bookingDate = today,
            valueDate = today,
            accountingDate = today,
            amount = BigDecimal("-50.00"),
            currency = "EUR",
            accountIban = iban,
        )
}
