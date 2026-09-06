package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesCommand
import com.moneylytics.api.application.port.input.CorrectRecurringSeriesTypeCommand
import com.moneylytics.api.application.port.input.CreateRecurringSeriesCommand
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesCommand
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.output.RecurringFalsePositiveRepository
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringSyncLogRepository
import com.moneylytics.api.application.port.output.RecurringTypeClassifier
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringFalsePositive
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringOccurrenceDeviation
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
    private val syncLogRepository: RecurringSyncLogRepository = mock()
    private val service =
        RecurringSeriesService(
            transactionRepository,
            recurringSeriesRepository,
            falsePositiveRepository,
            detector,
            classifier,
            syncLogRepository,
        )

    private val organizationId = 1L
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
        val command = RefreshRecurringSeriesCommand(organizationId = organizationId)
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(emptyList())
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())

        service.detect(command)

        val fromCaptor = argumentCaptor<LocalDate>()
        val toCaptor = argumentCaptor<LocalDate>()
        verify(transactionRepository).findByAccountingDateBetween(fromCaptor.capture(), toCaptor.capture(), any(), anyOrNull())
        assertThat(toCaptor.firstValue).isAfterOrEqualTo(fromCaptor.firstValue.plusMonths(23))
    }

    @Test
    fun `should classify each detected series using the ML classifier`() {
        val command = RefreshRecurringSeriesCommand(organizationId = organizationId)
        val detectedSeries = listOf(series(today.plusDays(5), type = RecurringType.OTHER))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())

        service.detect(command)

        verify(classifier).classify(eq(organizationId), any())
    }

    @Test
    fun `should not persist series on detect`() {
        val command = RefreshRecurringSeriesCommand(organizationId = organizationId)
        val detectedSeries = listOf(series(today.plusDays(5), type = RecurringType.OTHER))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())

        service.detect(command)

        verify(recurringSeriesRepository, never()).replaceAllForOrganization(any(), any())
    }

    @Test
    fun `should mark series as false positive when fingerprint is known`() {
        val fp = "DE01|E|netflix"
        val command = RefreshRecurringSeriesCommand(organizationId = organizationId)
        val detectedSeries = listOf(series(today.plusDays(5), fingerprint = fp))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(setOf(fp))

        val result = service.detect(command)

        assertThat(result).hasSize(1)
        assertThat(result[0].isFalsePositive).isTrue()
    }

    @Test
    fun `should not mark series as false positive when fingerprint is unknown`() {
        val command = RefreshRecurringSeriesCommand(organizationId = organizationId)
        val detectedSeries = listOf(series(today.plusDays(5), fingerprint = "DE01|E|netflix"))
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(detectedSeries)
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())

        val result = service.detect(command)

        assertThat(result).hasSize(1)
        assertThat(result[0].isFalsePositive).isFalse()
    }

    @Test
    fun `should filter out variable amount series classified as OTHER on detect`() {
        val command = RefreshRecurringSeriesCommand(organizationId = organizationId)
        val variableOtherSeries = series(today.plusDays(5), type = RecurringType.OTHER, amountVariable = true)
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(listOf(variableOtherSeries))
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.OTHER)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())

        val result = service.detect(command)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should persist only confirmed series on confirm`() {
        val confirmedFp = "DE01|E|netflix"
        val dismissedFp = "DE01|E|spotify"
        val command =
            ConfirmRecurringSeriesCommand(
                organizationId = organizationId,
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
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(emptyList())

        service.confirm(command)

        val captor = argumentCaptor<List<RecurringSeries>>()
        verify(recurringSeriesRepository).replaceAllForOrganization(captor.capture(), eq(organizationId))
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0].fingerprint).isEqualTo(confirmedFp)
    }

    @Test
    fun `should save new false positive entries on confirm`() {
        val dismissedFp = "DE01|E|spotify"
        val command =
            ConfirmRecurringSeriesCommand(
                organizationId = organizationId,
                confirmedFingerprints = emptyList(),
                falsePositiveFingerprints = listOf(dismissedFp),
            )
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(emptyList())
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(emptySet())
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(emptyList())

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
                organizationId = organizationId,
                confirmedFingerprints = emptyList(),
                falsePositiveFingerprints = listOf(alreadyFp),
            )
        whenever(transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
        whenever(detector.detect(any())).thenReturn(emptyList())
        whenever(classifier.classify(any(), any())).thenReturn(RecurringType.SUBSCRIPTION)
        whenever(falsePositiveRepository.findFingerprintsByOrganizationId(organizationId)).thenReturn(setOf(alreadyFp))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(emptyList())

        service.confirm(command)

        verify(falsePositiveRepository, never()).saveAll(any())
    }

    @Test
    fun `should return ON_TRACK when next expected date is in the future`() {
        val futureSeries = series(nextExpectedDate = today.plusDays(5))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(futureSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

        assertThat(result).hasSize(1)
        assertThat(result[0].deviation).isEqualTo(RecurrenceDeviation.ON_TRACK)
    }

    @Test
    fun `should return OVERDUE when next expected date has passed beyond grace period`() {
        val overdueSeries = series(nextExpectedDate = today.minusDays(10), intervalDays = 30)
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(overdueSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

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
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(amountChangedSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

        assertThat(result).hasSize(1)
        assertThat(result[0].deviation).isEqualTo(RecurrenceDeviation.AMOUNT_CHANGED)
    }

    @Test
    fun `should leave occurrence deviation null when no expectation was tracked`() {
        val futureSeries = series(nextExpectedDate = today.plusDays(5))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(futureSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

        assertThat(result[0].occurrences).allSatisfy { assertThat(it.deviation).isNull() }
    }

    @Test
    fun `should classify occurrence as ON_TIME when date and amount match the expectation`() {
        val occurrenceDate = today.minusDays(30)
        val trackedOccurrence =
            RecurringOccurrence(
                transactionId = 2L,
                date = occurrenceDate,
                amount = BigDecimal("-50.00"),
                expectedDate = occurrenceDate,
                expectedAmount = BigDecimal("-50.00"),
            )
        val trackedSeries = series(nextExpectedDate = today.plusDays(5)).copy(occurrences = listOf(trackedOccurrence))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(trackedSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

        assertThat(result[0].occurrences).hasSize(1)
        assertThat(result[0].occurrences[0].deviation).isEqualTo(RecurringOccurrenceDeviation.ON_TIME)
    }

    @Test
    fun `should classify occurrence as AMOUNT_CHANGED when amount deviates more than 15 percent from expectation`() {
        val occurrenceDate = today.minusDays(30)
        val trackedOccurrence =
            RecurringOccurrence(
                transactionId = 2L,
                date = occurrenceDate,
                amount = BigDecimal("-70.00"),
                expectedDate = occurrenceDate,
                expectedAmount = BigDecimal("-50.00"),
            )
        val trackedSeries = series(nextExpectedDate = today.plusDays(5)).copy(occurrences = listOf(trackedOccurrence))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(trackedSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

        assertThat(result[0].occurrences[0].deviation).isEqualTo(RecurringOccurrenceDeviation.AMOUNT_CHANGED)
    }

    @Test
    fun `should classify occurrence as DATE_SHIFTED when it lands well outside the expected date`() {
        val expectedDate = today.minusDays(30)
        val trackedOccurrence =
            RecurringOccurrence(
                transactionId = 2L,
                date = expectedDate.plusDays(10),
                amount = BigDecimal("-50.00"),
                expectedDate = expectedDate,
                expectedAmount = BigDecimal("-50.00"),
            )
        val trackedSeries = series(nextExpectedDate = today.plusDays(5)).copy(occurrences = listOf(trackedOccurrence))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(trackedSeries))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))

        assertThat(result[0].occurrences[0].deviation).isEqualTo(RecurringOccurrenceDeviation.DATE_SHIFTED)
    }

    @Test
    fun `should filter by direction when specified`() {
        val expense =
            series(today.plusDays(5)).copy(direction = RecurrenceDirection.EXPENSE)
        val income =
            series(today.plusDays(5)).copy(id = 2L, direction = RecurrenceDirection.INCOME)
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(expense, income))

        val result =
            service.getRecurringSeries(
                GetRecurringSeriesQuery(organizationId = organizationId, direction = RecurrenceDirection.EXPENSE),
            )

        assertThat(result).hasSize(1)
        assertThat(result[0].direction).isEqualTo(RecurrenceDirection.EXPENSE)
    }

    @Test
    fun `should filter by type when specified`() {
        val subscription = series(today.plusDays(5)).copy(type = RecurringType.SUBSCRIPTION)
        val rent = series(today.plusDays(5)).copy(id = 2L, type = RecurringType.RENT)
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(subscription, rent))

        val result = service.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId, type = RecurringType.RENT))

        assertThat(result).hasSize(1)
        assertThat(result[0].type).isEqualTo(RecurringType.RENT)
    }

    @Test
    fun `should create manual series with MANUAL status and interval derived from cadence`() {
        val created = series(today.plusDays(30)).copy(status = RecurrenceStatus.MANUAL)
        whenever(recurringSeriesRepository.save(any(), eq(organizationId))).thenReturn(created)

        val command =
            CreateRecurringSeriesCommand(
                organizationId = organizationId,
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
        verify(recurringSeriesRepository).save(captor.capture(), eq(organizationId))
        assertThat(captor.firstValue.status).isEqualTo(RecurrenceStatus.MANUAL)
        assertThat(captor.firstValue.intervalDays).isEqualTo(30)
        assertThat(captor.firstValue.fingerprint).startsWith("manual:")
    }

    @Test
    fun `should use lastBookingDate for nextExpectedDate when provided`() {
        val customDate = today.minusDays(5)
        val created = series(customDate.plusDays(30)).copy(status = RecurrenceStatus.MANUAL)
        whenever(recurringSeriesRepository.save(any(), eq(organizationId))).thenReturn(created)

        val command =
            CreateRecurringSeriesCommand(
                organizationId = organizationId,
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
        verify(recurringSeriesRepository).save(captor.capture(), eq(organizationId))
        assertThat(captor.firstValue.nextExpectedDate).isEqualTo(customDate.plusDays(30))
        assertThat(captor.firstValue.lastSeen).isEqualTo(customDate)
    }

    @Test
    fun `should delegate delete to repository`() {
        service.delete(DeleteRecurringSeriesCommand(organizationId = organizationId, seriesId = 99L))

        verify(recurringSeriesRepository).deleteByIdAndOrganizationId(99L, organizationId)
    }

    @Test
    fun `should update type in repository and train classifier when type is corrected`() {
        val existingSeries = series(today.plusDays(5)).copy(id = 42L, type = RecurringType.OTHER)
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(existingSeries))

        service.correctType(CorrectRecurringSeriesTypeCommand(seriesId = 42L, organizationId = organizationId, type = RecurringType.RENT))

        verify(recurringSeriesRepository).updateType(42L, RecurringType.RENT)
        verify(classifier).train(eq(organizationId), eq(RecurringType.RENT), eq(existingSeries.toFeatures()))
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
