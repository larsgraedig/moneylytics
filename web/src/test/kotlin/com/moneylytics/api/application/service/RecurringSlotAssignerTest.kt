package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.RecurringExpectedSlotRepository
import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSlotAssignerTest {
    private val repo: RecurringExpectedSlotRepository = mock()
    private val assigner = RecurringSlotAssigner(repo)

    private val baseDate = LocalDate.of(2024, 1, 1)

    private fun series(
        id: Long = 1L,
        firstSeen: LocalDate = baseDate,
        intervalDays: Int = 30,
        occurrences: List<RecurringOccurrence> = emptyList(),
    ) = RecurringSeries(
        id = id,
        label = "Test",
        type = RecurringType.SUBSCRIPTION,
        direction = RecurrenceDirection.EXPENSE,
        cadence = RecurrenceCadence.MONTHLY,
        intervalDays = intervalDays,
        expectedAmount = BigDecimal("-15.00"),
        amountVariable = false,
        currency = "EUR",
        accountIban = "DE01",
        firstSeen = firstSeen,
        lastSeen = firstSeen,
        occurrenceCount = occurrences.size,
        nextExpectedDate = firstSeen.plusDays(intervalDays.toLong()),
        status = RecurrenceStatus.DETECTED,
        occurrences = occurrences,
        deviation = RecurrenceDeviation.ON_TRACK,
    )

    private fun occ(
        id: Long,
        date: LocalDate,
        amount: BigDecimal = BigDecimal("-15.00"),
        counterpartyName: String? = "Spotify",
    ) = RecurringOccurrence(
        transactionId = id,
        date = date,
        amount = amount,
        counterpartyName = counterpartyName,
    )

    @Test
    fun `should return empty list when no occurrences exist`() {
        val result = assigner.computeSlots(series(), today = baseDate.plusDays(60))
        assertThat(result).isEmpty()
    }

    @Test
    fun `should match all slots when occurrences are on time`() {
        val occ1 = occ(1, baseDate)
        val occ2 = occ(2, baseDate.plusDays(30))
        val occ3 = occ(3, baseDate.plusDays(60))
        val s = series(occurrences = listOf(occ1, occ2, occ3))
        val today = baseDate.plusDays(70)

        val result = assigner.computeSlots(s, today)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.transactionId }).containsExactlyInAnyOrder(1L, 2L, 3L)
        assertThat(result.all { it.expectedDate != null }).isTrue()
    }

    @Test
    fun `should leave gap for missed slot`() {
        val occ1 = occ(1, baseDate)
        val occ3 = occ(3, baseDate.plusDays(60))
        val s = series(occurrences = listOf(occ1, occ3))
        val today = baseDate.plusDays(70)

        val result = assigner.computeSlots(s, today)

        assertThat(result).hasSize(2)
        val matchedDates = result.map { it.expectedDate }
        assertThat(matchedDates).containsExactlyInAnyOrder(baseDate, baseDate.plusDays(60))
        // The expected slot for day 30 is not matched (occ2 is missing)
        assertThat(matchedDates).doesNotContain(baseDate.plusDays(30))
    }

    @Test
    fun `should match occurrence within grace period`() {
        val occ1 = occ(1, baseDate)
        val occ2 = occ(2, baseDate.plusDays(33)) // 3 days late, within grace (30 * 0.15 = 4.5 → 4 days)
        val s = series(intervalDays = 30, occurrences = listOf(occ1, occ2))
        val today = baseDate.plusDays(40)

        val result = assigner.computeSlots(s, today)

        assertThat(result).hasSize(2)
        val slot2 = result.find { it.expectedDate == baseDate.plusDays(30) }
        assertThat(slot2).isNotNull()
        assertThat(slot2!!.transactionId).isEqualTo(2L)
    }

    @Test
    fun `should not match occurrence outside grace period`() {
        val occ1 = occ(1, baseDate)
        val occ2 = occ(2, baseDate.plusDays(40)) // 10 days late, beyond grace (max(3, 30*0.15)=4)
        val s = series(intervalDays = 30, occurrences = listOf(occ1, occ2))
        val today = baseDate.plusDays(45)

        val result = assigner.computeSlots(s, today)

        // slot at day 30 has no match (occ2 is too far), occ2 falls in slot at day 60 range (not reached) or no slot
        assertThat(result).hasSize(1)
        assertThat(result.first().expectedDate).isEqualTo(baseDate)
    }

    @Test
    fun `should not generate future slots`() {
        val occ1 = occ(1, baseDate)
        val s = series(intervalDays = 30, occurrences = listOf(occ1))
        val today = baseDate.plusDays(15) // before the second expected date

        val result = assigner.computeSlots(s, today)

        assertThat(result).hasSize(1)
        assertThat(result.first().expectedDate).isEqualTo(baseDate)
    }

    @Test
    fun `should include overdue slot as missed when past today`() {
        val occ1 = occ(1, baseDate)
        val s = series(intervalDays = 30, occurrences = listOf(occ1))
        val today = baseDate.plusDays(50) // day 30 slot is in the past with no matching occ

        val result = assigner.computeSlots(s, today)

        // day 0 is matched, day 30 slot has no occurrence (missed — not included in result)
        assertThat(result).hasSize(1)
        assertThat(result.first().transactionId).isEqualTo(1L)
    }

    @Test
    fun `should skip series without id when persisting`() {
        val s = series(id = 0L).copy(id = null)
        assigner.computeAndPersist(s)
        verify(repo, never()).replaceSlots(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `should call replaceSlots with computed slots`() {
        val occ1 = occ(1, baseDate)
        val s = series(id = 42L, occurrences = listOf(occ1))
        val today = baseDate.plusDays(10)

        assigner.computeAndPersist(s, today)

        val captor = argumentCaptor<List<com.moneylytics.api.domain.RecurringExpectedSlot>>()
        verify(repo).replaceSlots(org.mockito.kotlin.eq(42L), captor.capture())
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue.first().transactionId).isEqualTo(1L)
    }
}
