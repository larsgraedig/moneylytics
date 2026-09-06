package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSeriesPersistenceAdapterTest {
    private val recurringSeriesJpaRepository: RecurringSeriesJpaRepository = mock()
    private val recurringSeriesMemberJpaRepository: RecurringSeriesMemberJpaRepository = mock()
    private val recurringExpectedOccurrenceJpaRepository: RecurringExpectedOccurrenceJpaRepository = mock()
    private val organizationJpaRepository: OrganizationJpaRepository = mock()
    private val transactionJpaRepository: TransactionJpaRepository = mock()
    private val adapter =
        RecurringSeriesPersistenceAdapter(
            recurringSeriesJpaRepository,
            recurringSeriesMemberJpaRepository,
            recurringExpectedOccurrenceJpaRepository,
            organizationJpaRepository,
            transactionJpaRepository,
        )

    private val organizationId = 1L
    private val organizationEntity = OrganizationEntity(name = "Test Org", id = organizationId)
    private val accountEntity = AccountEntity(iban = "DE01", name = "Giro", organization = organizationEntity, id = 1L)

    private fun seriesEntity(
        id: Long,
        fingerprint: String = "DE01|E|netflix",
        nextExpectedDate: LocalDate = LocalDate.of(2026, 2, 1),
        expectedAmount: BigDecimal = BigDecimal("-15.99"),
    ) = RecurringSeriesEntity(
        organization = organizationEntity,
        label = "Netflix",
        type = RecurringType.SUBSCRIPTION,
        direction = RecurrenceDirection.EXPENSE,
        cadence = RecurrenceCadence.MONTHLY,
        intervalDays = 30,
        expectedAmount = expectedAmount,
        amountVariable = false,
        currency = "EUR",
        accountIban = "DE01",
        firstSeen = LocalDate.of(2025, 11, 1),
        lastSeen = LocalDate.of(2026, 1, 1),
        occurrenceCount = 3,
        nextExpectedDate = nextExpectedDate,
        status = RecurrenceStatus.DETECTED,
        fingerprint = fingerprint,
        id = id,
    )

    private fun series(
        fingerprint: String = "DE01|E|netflix",
        nextExpectedDate: LocalDate = LocalDate.of(2026, 2, 1),
        expectedAmount: BigDecimal = BigDecimal("-15.99"),
    ) = RecurringSeries(
        id = null,
        label = "Netflix",
        type = RecurringType.SUBSCRIPTION,
        direction = RecurrenceDirection.EXPENSE,
        cadence = RecurrenceCadence.MONTHLY,
        intervalDays = 30,
        expectedAmount = expectedAmount,
        amountVariable = false,
        currency = "EUR",
        accountIban = "DE01",
        firstSeen = LocalDate.of(2025, 11, 1),
        lastSeen = LocalDate.of(2026, 1, 1),
        occurrenceCount = 3,
        nextExpectedDate = nextExpectedDate,
        status = RecurrenceStatus.DETECTED,
        fingerprint = fingerprint,
    )

    private fun txEntity(
        id: Long,
        bookingDate: LocalDate,
        amount: BigDecimal,
    ) = TransactionEntity(
        id = id,
        bookingDate = bookingDate,
        valueDate = bookingDate,
        accountingDate = bookingDate,
        amount = amount,
        currency = "EUR",
        account = accountEntity,
        fingerprint = "fp$id",
        organization = organizationEntity,
    )

    private fun stubSavingNewSeriesWithId(newId: Long) {
        whenever(recurringSeriesJpaRepository.save(any())).thenAnswer { invocation ->
            val entity = invocation.getArgument<RecurringSeriesEntity>(0)
            entity.id = newId
            entity
        }
    }

    @Test
    fun `should join expected occurrence data into matched transaction occurrences`() {
        val entity = seriesEntity(id = 1L)
        val member = RecurringSeriesMemberEntity(seriesId = 1L, transactionId = 100L)
        val transaction = txEntity(id = 100L, bookingDate = LocalDate.of(2026, 1, 5), amount = BigDecimal("-15.99"))
        val matchedOccurrence =
            RecurringExpectedOccurrenceEntity(
                seriesId = 1L,
                expectedDate = LocalDate.of(2026, 1, 1),
                expectedAmount = BigDecimal("-15.99"),
                matchedTransactionId = 100L,
                matchedDate = LocalDate.of(2026, 1, 5),
                matchedAmount = BigDecimal("-15.99"),
            )
        val pendingOccurrence =
            RecurringExpectedOccurrenceEntity(seriesId = 1L, expectedDate = LocalDate.of(2026, 2, 1), expectedAmount = BigDecimal("-15.99"))

        whenever(recurringSeriesJpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(entity))
        whenever(recurringSeriesMemberJpaRepository.findBySeriesIdIn(listOf(1L))).thenReturn(listOf(member))
        whenever(transactionJpaRepository.findAllById(listOf(100L))).thenReturn(listOf(transaction))
        whenever(recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(1L)))
            .thenReturn(listOf(matchedOccurrence, pendingOccurrence))

        val result = adapter.findByOrganizationId(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].occurrences).hasSize(1)
        val occurrence = result[0].occurrences[0]
        assertThat(occurrence.expectedDate).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(occurrence.expectedAmount).isEqualByComparingTo(BigDecimal("-15.99"))
    }

    @Test
    fun `should delete expected occurrences when deleting a series`() {
        val entity = seriesEntity(id = 1L)
        whenever(recurringSeriesJpaRepository.findByIdAndOrganizationId(1L, organizationId)).thenReturn(entity)

        adapter.deleteByIdAndOrganizationId(1L, organizationId)

        verify(recurringExpectedOccurrenceJpaRepository).deleteBySeriesIdIn(listOf(1L))
        verify(recurringSeriesMemberJpaRepository).deleteBySeriesIdIn(listOf(1L))
    }

    @Test
    fun `should preserve matched expected occurrence history and refresh the pending row on re-confirm`() {
        val fingerprint = "DE01|E|netflix"
        val oldEntity = seriesEntity(id = 1L, fingerprint = fingerprint)
        val matchedHistory =
            RecurringExpectedOccurrenceEntity(
                seriesId = 1L,
                expectedDate = LocalDate.of(2025, 12, 1),
                expectedAmount = BigDecimal("-15.99"),
                matchedTransactionId = 50L,
                matchedDate = LocalDate.of(2025, 12, 2),
                matchedAmount = BigDecimal("-15.99"),
            )
        val oldPending =
            RecurringExpectedOccurrenceEntity(seriesId = 1L, expectedDate = LocalDate.of(2026, 1, 1), expectedAmount = BigDecimal("-15.99"))

        whenever(recurringSeriesJpaRepository.findByOrganizationId(organizationId)).thenReturn(listOf(oldEntity))
        whenever(recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(1L))).thenReturn(listOf(matchedHistory, oldPending))
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        stubSavingNewSeriesWithId(newId = 2L)

        val freshlyDetected =
            series(fingerprint = fingerprint, nextExpectedDate = LocalDate.of(2026, 2, 1), expectedAmount = BigDecimal("-16.99"))

        adapter.replaceAllForOrganization(listOf(freshlyDetected), organizationId)

        verify(recurringExpectedOccurrenceJpaRepository).deleteBySeriesIdIn(listOf(1L))

        val savedHistoryCaptor = argumentCaptor<List<RecurringExpectedOccurrenceEntity>>()
        verify(recurringExpectedOccurrenceJpaRepository).saveAll(savedHistoryCaptor.capture())
        assertThat(savedHistoryCaptor.firstValue).hasSize(1)
        assertThat(savedHistoryCaptor.firstValue[0].seriesId).isEqualTo(2L)
        assertThat(savedHistoryCaptor.firstValue[0].matchedTransactionId).isEqualTo(50L)

        val singleSaveCaptor = argumentCaptor<RecurringExpectedOccurrenceEntity>()
        verify(recurringExpectedOccurrenceJpaRepository).save(singleSaveCaptor.capture())
        assertThat(singleSaveCaptor.firstValue.seriesId).isEqualTo(2L)
        assertThat(singleSaveCaptor.firstValue.matchedTransactionId).isNull()
        assertThat(singleSaveCaptor.firstValue.expectedDate).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(singleSaveCaptor.firstValue.expectedAmount).isEqualByComparingTo(BigDecimal("-16.99"))
    }

    @Test
    fun `should seed only a fresh pending occurrence for a brand new series`() {
        whenever(recurringSeriesJpaRepository.findByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(organizationJpaRepository.getReferenceById(organizationId)).thenReturn(organizationEntity)
        stubSavingNewSeriesWithId(newId = 3L)

        val newSeries = series(fingerprint = "DE01|E|spotify", nextExpectedDate = LocalDate.of(2026, 2, 1))

        adapter.replaceAllForOrganization(listOf(newSeries), organizationId)

        verify(recurringExpectedOccurrenceJpaRepository, never()).saveAll(any<List<RecurringExpectedOccurrenceEntity>>())
        val singleSaveCaptor = argumentCaptor<RecurringExpectedOccurrenceEntity>()
        verify(recurringExpectedOccurrenceJpaRepository).save(singleSaveCaptor.capture())
        assertThat(singleSaveCaptor.firstValue.seriesId).isEqualTo(3L)
        assertThat(singleSaveCaptor.firstValue.expectedDate).isEqualTo(LocalDate.of(2026, 2, 1))
    }
}
