package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesCommand
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeCommand
import com.moneylytics.api.application.port.input.CreateRecurringSeriesCommand
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesCommand
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.output.RecurringFalsePositiveRepository
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringTypeClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringFalsePositive
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.toFeatures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSeriesServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val recurringSeriesRepository: RecurringSeriesRepository = mock()
    private val falsePositiveRepository: RecurringFalsePositiveRepository = mock()
    private val detector: RecurringSeriesDetector = mock()
    private val classifier: RecurringTypeClassifier = mock()
    private val service =
        RecurringSeriesService(transactionRepository, recurringSeriesRepository, falsePositiveRepository, detector, classifier)

    private val userId = 1L
    private val today = LocalDate.now()

    private fun series(
        nextExpectedDate: LocalDate,
        lastOccurrenceDate: LocalDate = nextExpectedDate.minusDays(30),
        lastOccurrenceAmount: BigDecimal = BigDecimal("-50.00"),
        expectedAmount: BigDecimal = BigDecimal("-50.00"),
        intervalDays: Int = 30,
        occurrenceCount: Int = 3,
        type: RecurringType = RecurringType.SUBSCRIPTION,
        amountVariable: Boolean = false,
        fingerprint: String = "DE01|E|netflix",
    ) = RecurringSeries(
        id = 1L,
        label = "Test",
        type = type,
        direction = RecurrenceDirection.EXPENSE,
        cadence = RecurrenceCadence.MONTHLY,
        intervalDays = intervalDays,
        expectedAmount = expectedAmount,
        amountVariable = amountVariable,
        currency = "EUR",
        accountIban = "DE01",
        firstSeen = lastOccurrenceDate.minusMonths(2),
        lastSeen = lastOccurrenceDate,
        occurrenceCount = occurrenceCount,
        nextExpectedDate = nextExpectedDate,
        status = RecurrenceStatus.DETECTED,
        fingerprint = fingerprint,
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
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())

        service.detect(command)

        val fromCaptor = argumentCaptor<LocalDate>()
        val toCaptor = argumentCaptor<LocalDate>()
        verify(transactionRepository).findByAccountingDateBetween(fromCaptor.capture(), toCaptor.capture(), any(), anyOrNull())
        assertThat(toCaptor.firstValue).isAfterOrEqualTo(fromCaptor.firstValue.plusMonths(23))
    }

    @Test
    fun `should classify each detected series using the ML classifier`() {
        val command = RefreshRecurringSeriesCommand(userId = userId)
        val detectedSeries = listOf(series(today.plusDays(5), type = RecurringType.OTHER))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())

        service.detect(command)

        verify(classifier).classify(eq(userId), any())
    }

    @Test
    fun `should not persist series on detect`() {
        val command = RefreshRecurringSeriesCommand(userId = userId)
        val detectedSeries = listOf(series(today.plusDays(5), type = RecurringType.OTHER))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())

        service.detect(command)

        verify(recurringSeriesRepository, never()).replaceAllForUser(any(), any())
    }

    @Test
    fun `should mark series as false positive when fingerprint is known`() {
        val fp = "DE01|E|netflix"
        val command = RefreshRecurringSeriesCommand(userId = userId)
        val detectedSeries = listOf(series(today.plusDays(5), fingerprint = fp))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(setOf(fp))

        val result = service.detect(command)

        assertThat(result).hasSize(1)
        assertThat(result[0].isFalsePositive).isTrue()
    }

    @Test
    fun `should not mark series as false positive when fingerprint is unknown`() {
        val command = RefreshRecurringSeriesCommand(userId = userId)
        val detectedSeries = listOf(series(today.plusDays(5), fingerprint = "DE01|E|netflix"))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())

        val result = service.detect(command)

        assertThat(result).hasSize(1)
        assertThat(result[0].isFalsePositive).isFalse()
    }

    @Test
    fun `should filter out variable amount series classified as OTHER on detect`() {
        val command = RefreshRecurringSeriesCommand(userId = userId)
        val variableOtherSeries = series(today.plusDays(5), type = RecurringType.OTHER, amountVariable = true)
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(listOf(variableOtherSeries))
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.OTHER)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())

        val result = service.detect(command)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should persist only confirmed series on confirm`() {
        val confirmedFp = "DE01|E|netflix"
        val dismissedFp = "DE01|E|spotify"
        val command =
            ConfirmRecurringSeriesCommand(
                userId = userId,
                confirmedFingerprints = listOf(confirmedFp),
                falsePositiveFingerprints = listOf(dismissedFp),
            )
        val detectedSeries =
            listOf(
                series(today.plusDays(5), fingerprint = confirmedFp),
                series(today.plusDays(5), fingerprint = dismissedFp).copy(id = 2L),
            )
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(emptyList())

        service.confirm(command)

        val captor = argumentCaptor<List<RecurringSeries>>()
        verify(recurringSeriesRepository).replaceAllForUser(captor.capture(), eq(userId))
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].fingerprint).isEqualTo(confirmedFp)
    }

    @Test
    fun `should save new false positive entries on confirm`() {
        val dismissedFp = "DE01|E|spotify"
        val command =
            ConfirmRecurringSeriesCommand(
                userId = userId,
                confirmedFingerprints = emptyList(),
                falsePositiveFingerprints = listOf(dismissedFp),
            )
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(emptyList())
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(emptySet())
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(emptyList())

        service.confirm(command)

        val captor = argumentCaptor<List<RecurringFalsePositive>>()
        verify(falsePositiveRepository).saveAll(captor.capture())
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].fingerprint).isEqualTo(dismissedFp)
    }

    @Test
    fun `should not duplicate existing false positive entries on confirm`() {
        val alreadyFp = "DE01|E|spotify"
        val command =
            ConfirmRecurringSeriesCommand(
                userId = userId,
                confirmedFingerprints = emptyList(),
                falsePositiveFingerprints = listOf(alreadyFp),
            )
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(emptyList())
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByUserId(userId)).thenReturn(setOf(alreadyFp))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(emptyList())

        service.confirm(command)

        verify(falsePositiveRepository, never()).saveAll(any())
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

    @Test
    fun `should create manual series with MANUAL status and interval derived from cadence`() {
        val created = series(today.plusDays(30)).copy(status = RecurrenceStatus.MANUAL)
        whenever(recurringSeriesRepository.save(any(), eq(userId))).thenReturn(created)

        val command =
            CreateRecurringSeriesCommand(
                userId = userId,
                label = "Miete",
                type = RecurringType.RENT,
                direction = RecurrenceDirection.EXPENSE,
                cadence = RecurrenceCadence.MONTHLY,
                expectedAmount = BigDecimal("850.00"),
                currency = "EUR",
                accountIban = "",
                lastBookingDate = null,
            )
        service.create(command)

        val captor = argumentCaptor<RecurringSeries>()
        verify(recurringSeriesRepository).save(captor.capture(), eq(userId))
        assertThat(captor.firstValue.status).isEqualTo(RecurrenceStatus.MANUAL)
        assertThat(captor.firstValue.intervalDays).isEqualTo(30)
        assertThat(captor.firstValue.fingerprint).startsWith("manual:")
    }

    @Test
    fun `should use lastBookingDate for nextExpectedDate when provided`() {
        val customDate = today.minusDays(5)
        val created = series(customDate.plusDays(30)).copy(status = RecurrenceStatus.MANUAL)
        whenever(recurringSeriesRepository.save(any(), eq(userId))).thenReturn(created)

        val command =
            CreateRecurringSeriesCommand(
                userId = userId,
                label = "Gym",
                type = RecurringType.MEMBERSHIP,
                direction = RecurrenceDirection.EXPENSE,
                cadence = RecurrenceCadence.MONTHLY,
                expectedAmount = BigDecimal("29.99"),
                currency = "EUR",
                accountIban = "",
                lastBookingDate = customDate,
            )
        service.create(command)

        val captor = argumentCaptor<RecurringSeries>()
        verify(recurringSeriesRepository).save(captor.capture(), eq(userId))
        assertThat(captor.firstValue.nextExpectedDate).isEqualTo(customDate.plusDays(30))
        assertThat(captor.firstValue.lastSeen).isEqualTo(customDate)
    }

    @Test
    fun `should delegate delete to repository`() {
        service.delete(DeleteRecurringSeriesCommand(userId = userId, seriesId = 99L))

        verify(recurringSeriesRepository).deleteByIdAndUserId(99L, userId)
    }

    @Test
    fun `should update type in repository and train classifier when type is corrected`() {
        val existingSeries = series(today.plusDays(5)).copy(id = 42L, type = RecurringType.OTHER)
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(existingSeries))

        service.correctType(CorrectRecurringSeriesTypeCommand(seriesId = 42L, userId = userId, type = RecurringType.RENT))

        verify(recurringSeriesRepository).updateType(42L, RecurringType.RENT)
        verify(classifier).train(eq(userId), eq(RecurringType.RENT), eq(existingSeries.toFeatures()))
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
