package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringSyncLogRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringExpectedOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class RecurringMatcherServiceTest {
    private val recurringSeriesRepository: RecurringSeriesRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val syncLogRepository: RecurringSyncLogRepository = mock()
    private val accountRepository: com.moneylytics.api.application.port.output.AccountRepository = mock()
    private val service = RecurringMatcherService(recurringSeriesRepository, transactionRepository, syncLogRepository, accountRepository)

    private val organizationId = 1L
    private val baseDate = LocalDate.of(2024, 1, 1)

    private fun series(
        id: Long = 1L,
        fingerprint: String = "DE01|E|netflix",
        lastSeen: LocalDate = baseDate.plusDays(30),
        firstSeen: LocalDate = baseDate,
        intervalDays: Int = 30,
        occurrenceCount: Int = 3,
    ) = RecurringSeries(
        id = id,
        label = "Netflix",
        type = RecurringType.SUBSCRIPTION,
        direction = RecurrenceDirection.EXPENSE,
        cadence = RecurrenceCadence.MONTHLY,
        intervalDays = intervalDays,
        expectedAmount = BigDecimal("-15.99"),
        amountVariable = false,
        currency = "EUR",
        accountIban = "DE01",
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        occurrenceCount = occurrenceCount,
        nextExpectedDate = lastSeen.plusDays(intervalDays.toLong()),
        status = RecurrenceStatus.DETECTED,
        fingerprint = fingerprint,
    )

    private fun tx(
        id: Long,
        iban: String = "DE01",
        counterpartyName: String? = "Netflix",
        bookingDate: LocalDate = baseDate.plusDays(62),
        amount: String = "-15.99",
    ) = Transaction(
        id = id,
        category = null,
        subcategory = null,
        bookingDate = bookingDate,
        valueDate = bookingDate,
        accountingDate = bookingDate,
        amount = BigDecimal(amount),
        currency = "EUR",
        accountIban = iban,
        counterpartyName = counterpartyName,
    )

    private fun expectedOccurrence(
        id: Long,
        seriesId: Long,
        expectedDate: LocalDate,
        expectedAmount: BigDecimal = BigDecimal("-15.99"),
    ) = RecurringExpectedOccurrence(id = id, seriesId = seriesId, expectedDate = expectedDate, expectedAmount = expectedAmount)

    private fun stubExpectedOccurrenceCreation(nextId: Long = 1L) {
        var counter = nextId
        whenever(recurringSeriesRepository.createExpectedOccurrence(any(), any(), any())).thenAnswer { invocation ->
            expectedOccurrence(
                id = counter++,
                seriesId = invocation.getArgument(0),
                expectedDate = invocation.getArgument(1),
                expectedAmount = invocation.getArgument(2),
            )
        }
    }

    @Test
    fun `should do nothing when no users have series`() {
        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(emptySet())

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository, never()).findByOrganizationId(any())
    }

    @Test
    fun `should skip manual series`() {
        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(
            listOf(series(fingerprint = "manual:some-uuid")),
        )

        service.syncForAllOrganizations()

        verify(transactionRepository, never()).findByAccountingDateBetween(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `should link new matching transaction to series`() {
        val newTx = tx(id = 99L, bookingDate = baseDate.plusDays(62))
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), eq(organizationId), anyOrNull()),
        ).thenReturn(listOf(newTx))
        stubExpectedOccurrenceCreation()

        service.syncForAllOrganizations()

        val idsCaptor = argumentCaptor<List<Long>>()
        verify(recurringSeriesRepository).addMembers(eq(requireNotNull(s.id)), idsCaptor.capture())
        assertThat(idsCaptor.firstValue).containsExactly(99L)
    }

    @Test
    fun `should not re-link already linked transaction`() {
        val existingTx = tx(id = 50L, bookingDate = baseDate.plusDays(62))
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(setOf(50L))
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(existingTx))

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository, never()).addMembers(any(), any())
    }

    @Test
    fun `should not link transaction before lastSeen`() {
        val oldTx = tx(id = 10L, bookingDate = baseDate.plusDays(20))
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(oldTx))

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository, never()).addMembers(any(), any())
    }

    @Test
    fun `should not link transaction with non-matching group key`() {
        val nonMatchingTx = tx(id = 77L, counterpartyName = "Spotify", bookingDate = baseDate.plusDays(62))
        val s = series(fingerprint = "DE01|E|netflix", lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(nonMatchingTx))

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository, never()).addMembers(any(), any())
    }

    @Test
    fun `should update series metadata after linking new transactions`() {
        val newDate = baseDate.plusDays(62)
        val newTx = tx(id = 99L, bookingDate = newDate)
        val s = series(lastSeen = baseDate.plusDays(31), occurrenceCount = 3, intervalDays = 30)

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(newTx))
        stubExpectedOccurrenceCreation()

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository).updateSeriesMetadata(
            seriesId = requireNotNull(s.id),
            lastSeen = newDate,
            nextExpectedDate = newDate.plusDays(30),
            occurrenceCount = 4,
        )
    }

    @Test
    fun `should mark pending expected occurrence as matched and create the next one`() {
        val newDate = baseDate.plusDays(62)
        val newTx = tx(id = 99L, bookingDate = newDate)
        val s = series(lastSeen = baseDate.plusDays(31), intervalDays = 30)
        val pending = expectedOccurrence(id = 5L, seriesId = requireNotNull(s.id), expectedDate = newDate)

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(newTx))
        whenever(recurringSeriesRepository.findPendingExpectedOccurrence(requireNotNull(s.id))).thenReturn(pending)
        stubExpectedOccurrenceCreation(nextId = 6L)

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository).markExpectedOccurrenceMatched(
            expectedOccurrenceId = 5L,
            matchedTransactionId = 99L,
            matchedDate = newDate,
            matchedAmount = newTx.amount,
        )
        verify(recurringSeriesRepository).createExpectedOccurrence(
            seriesId = requireNotNull(s.id),
            expectedDate = newDate.plusDays(30),
            expectedAmount = s.expectedAmount,
        )
    }

    @Test
    fun `should pair multiple new transactions to sequential expected occurrences in chronological order`() {
        val firstDate = baseDate.plusDays(62)
        val secondDate = baseDate.plusDays(93)
        val firstTx = tx(id = 99L, bookingDate = firstDate)
        val secondTx = tx(id = 100L, bookingDate = secondDate)
        val s = series(lastSeen = baseDate.plusDays(31), intervalDays = 30)

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        // Returned out of order to prove the matcher sorts by bookingDate before pairing.
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(secondTx, firstTx))
        whenever(recurringSeriesRepository.findPendingExpectedOccurrence(requireNotNull(s.id))).thenReturn(null)
        stubExpectedOccurrenceCreation()

        service.syncForAllOrganizations()

        val idCaptor = argumentCaptor<Long>()
        val dateCaptor = argumentCaptor<LocalDate>()
        verify(recurringSeriesRepository, org.mockito.kotlin.times(2)).markExpectedOccurrenceMatched(
            expectedOccurrenceId = idCaptor.capture(),
            matchedTransactionId = any(),
            matchedDate = dateCaptor.capture(),
            matchedAmount = any(),
        )
        assertThat(dateCaptor.allValues).containsExactly(firstDate, secondDate)

        val txIdCaptor = argumentCaptor<Long>()
        inOrder(recurringSeriesRepository).let { order ->
            order.verify(recurringSeriesRepository).markExpectedOccurrenceMatched(
                eq(idCaptor.firstValue),
                txIdCaptor.capture(),
                eq(firstDate),
                any(),
            )
            order.verify(recurringSeriesRepository).markExpectedOccurrenceMatched(
                eq(idCaptor.secondValue),
                txIdCaptor.capture(),
                eq(secondDate),
                any(),
            )
        }
        assertThat(txIdCaptor.allValues).containsExactly(99L, 100L)
    }

    @Test
    fun `should create a pending occurrence on the fly when none exists`() {
        val newDate = baseDate.plusDays(62)
        val newTx = tx(id = 99L, bookingDate = newDate)
        val s = series(lastSeen = baseDate.plusDays(31), intervalDays = 30)

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(newTx))
        whenever(recurringSeriesRepository.findPendingExpectedOccurrence(requireNotNull(s.id))).thenReturn(null)
        stubExpectedOccurrenceCreation()

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository).createExpectedOccurrence(
            seriesId = requireNotNull(s.id),
            expectedDate = s.lastSeen.plusDays(30),
            expectedAmount = s.expectedAmount,
        )
    }

    @Test
    fun `should not touch expected occurrences when no new matches are found`() {
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(requireNotNull(s.id))).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(emptyList())

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository, never()).findPendingExpectedOccurrence(any())
        verify(recurringSeriesRepository, never()).createExpectedOccurrence(any(), any(), any())
        verify(recurringSeriesRepository, never()).markExpectedOccurrenceMatched(any(), any(), any(), any())
    }

    @Test
    fun `should process multiple users independently`() {
        val organizationId2 = 2L
        whenever(recurringSeriesRepository.findAllOrganizationIds()).thenReturn(setOf(organizationId, organizationId2))
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId)).thenReturn(emptyList())
        whenever(recurringSeriesRepository.findByOrganizationId(organizationId2)).thenReturn(emptyList())

        service.syncForAllOrganizations()

        verify(recurringSeriesRepository).findByOrganizationId(organizationId)
        verify(recurringSeriesRepository).findByOrganizationId(organizationId2)
    }
}
