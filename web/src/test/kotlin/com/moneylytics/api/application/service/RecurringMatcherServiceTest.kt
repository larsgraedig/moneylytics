package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import com.moneylytics.api.domain.Transaction
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

class RecurringMatcherServiceTest {
    private val recurringSeriesRepository: RecurringSeriesRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val service = RecurringMatcherService(recurringSeriesRepository, transactionRepository)

    private val userId = 1L
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

    @Test
    fun `should do nothing when no users have series`() {
        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(emptySet())

        service.syncForAllUsers()

        verify(recurringSeriesRepository, never()).findByUserId(any())
    }

    @Test
    fun `should skip manual series`() {
        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(
            listOf(series(fingerprint = "manual:some-uuid")),
        )

        service.syncForAllUsers()

        verify(transactionRepository, never()).findByAccountingDateBetween(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `should link new matching transaction to series`() {
        val newTx = tx(id = 99L, bookingDate = baseDate.plusDays(62))
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(s.id!!)).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), eq(userId), anyOrNull()),
        ).thenReturn(listOf(newTx))

        service.syncForAllUsers()

        val idsCaptor = argumentCaptor<List<Long>>()
        verify(recurringSeriesRepository).addMembers(eq(s.id!!), idsCaptor.capture())
        assertThat(idsCaptor.firstValue).containsExactly(99L)
    }

    @Test
    fun `should not re-link already linked transaction`() {
        val existingTx = tx(id = 50L, bookingDate = baseDate.plusDays(62))
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(s.id!!)).thenReturn(setOf(50L))
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(existingTx))

        service.syncForAllUsers()

        verify(recurringSeriesRepository, never()).addMembers(any(), any())
    }

    @Test
    fun `should not link transaction before lastSeen`() {
        val oldTx = tx(id = 10L, bookingDate = baseDate.plusDays(20))
        val s = series(lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(s.id!!)).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(oldTx))

        service.syncForAllUsers()

        verify(recurringSeriesRepository, never()).addMembers(any(), any())
    }

    @Test
    fun `should not link transaction with non-matching group key`() {
        val nonMatchingTx = tx(id = 77L, counterpartyName = "Spotify", bookingDate = baseDate.plusDays(62))
        val s = series(fingerprint = "DE01|E|netflix", lastSeen = baseDate.plusDays(31))

        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(s.id!!)).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(nonMatchingTx))

        service.syncForAllUsers()

        verify(recurringSeriesRepository, never()).addMembers(any(), any())
    }

    @Test
    fun `should update series metadata after linking new transactions`() {
        val newDate = baseDate.plusDays(62)
        val newTx = tx(id = 99L, bookingDate = newDate)
        val s = series(lastSeen = baseDate.plusDays(31), occurrenceCount = 3, intervalDays = 30)

        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(listOf(s))
        whenever(recurringSeriesRepository.findMemberTransactionIds(s.id!!)).thenReturn(emptySet())
        whenever(
            transactionRepository.findByAccountingDateBetween(any(), any(), any(), anyOrNull()),
        ).thenReturn(listOf(newTx))

        service.syncForAllUsers()

        verify(recurringSeriesRepository).updateSeriesMetadata(
            seriesId = s.id!!,
            lastSeen = newDate,
            nextExpectedDate = newDate.plusDays(30),
            occurrenceCount = 4,
        )
    }

    @Test
    fun `should process multiple users independently`() {
        val userId2 = 2L
        whenever(recurringSeriesRepository.findAllUserIds()).thenReturn(setOf(userId, userId2))
        whenever(recurringSeriesRepository.findByUserId(userId)).thenReturn(emptyList())
        whenever(recurringSeriesRepository.findByUserId(userId2)).thenReturn(emptyList())

        service.syncForAllUsers()

        verify(recurringSeriesRepository).findByUserId(userId)
        verify(recurringSeriesRepository).findByUserId(userId2)
    }
}
