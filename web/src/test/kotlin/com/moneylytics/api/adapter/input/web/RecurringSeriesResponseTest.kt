package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDeviation
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringExpectedSlot
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurringSeriesResponseTest {
    private val baseDate = LocalDate.of(2024, 1, 1)

    private fun series(
        firstSeen: LocalDate = baseDate,
        intervalDays: Int = 30,
        expectedSlots: List<RecurringExpectedSlot> = emptyList(),
    ) = RecurringSeries(
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
        occurrenceCount = expectedSlots.size,
        nextExpectedDate = firstSeen.plusDays(intervalDays.toLong()),
        status = RecurrenceStatus.DETECTED,
        deviation = RecurrenceDeviation.ON_TRACK,
        expectedSlots = expectedSlots,
    )

    private fun slot(
        expectedDate: LocalDate,
        amount: BigDecimal,
        txId: Long = 1L,
    ) = RecurringExpectedSlot(
        expectedDate = expectedDate,
        transactionId = txId,
        date = expectedDate,
        amount = amount,
    )

    @Test
    fun `missed slot has null prediction when no matched slots exist`() {
        val s = series(expectedSlots = emptyList())
        val today = baseDate.plusDays(40)

        val item = s.toItem()
        val missedSlot = item.expectedSlots.find { !it.matched }

        assertThat(missedSlot).isNotNull()
        assertThat(missedSlot!!.predictedAmount).isNull()
        assertThat(missedSlot.predictedAmountMin).isNull()
        assertThat(missedSlot.predictedAmountMax).isNull()
    }

    @Test
    fun `matched slot always has null prediction fields`() {
        val s = series(expectedSlots = listOf(slot(baseDate, BigDecimal("-15.00"))))
        val item = s.toItem()
        val matchedSlot = item.expectedSlots.find { it.matched }

        assertThat(matchedSlot).isNotNull()
        assertThat(matchedSlot!!.predictedAmount).isNull()
        assertThat(matchedSlot.predictedAmountMin).isNull()
        assertThat(matchedSlot.predictedAmountMax).isNull()
    }

    @Test
    fun `single matched slot gives prediction equal to its amount`() {
        val amount = BigDecimal("-950.00")
        val s = series(expectedSlots = listOf(slot(baseDate, amount)))
        val today = baseDate.plusDays(40)

        val item = s.toItem()
        val missedSlot = item.expectedSlots.find { !it.matched }

        assertThat(missedSlot).isNotNull()
        assertThat(missedSlot!!.predictedAmount).isEqualByComparingTo(amount)
        assertThat(missedSlot.predictedAmountMin).isEqualByComparingTo(amount)
        assertThat(missedSlot.predictedAmountMax).isEqualByComparingTo(amount)
    }

    @Test
    fun `odd number of matched slots uses exact median`() {
        // amounts: -900, -950, -1000 → sorted: [-1000, -950, -900] → median = -950
        val slots =
            listOf(
                slot(baseDate, BigDecimal("-900.00"), 1),
                slot(baseDate.plusDays(30), BigDecimal("-950.00"), 2),
                slot(baseDate.plusDays(60), BigDecimal("-1000.00"), 3),
            )
        val s = series(expectedSlots = slots)
        val today = baseDate.plusDays(100)

        val item = s.toItem()
        val missedSlots = item.expectedSlots.filter { !it.matched }

        assertThat(missedSlots).isNotEmpty()
        val prediction = missedSlots.first()
        assertThat(prediction.predictedAmount).isEqualByComparingTo(BigDecimal("-950.00"))
        assertThat(prediction.predictedAmountMin).isEqualByComparingTo(BigDecimal("-1000.00"))
        assertThat(prediction.predictedAmountMax).isEqualByComparingTo(BigDecimal("-900.00"))
    }

    @Test
    fun `even number of matched slots averages the two middle values`() {
        // amounts: -900, -1000 → sorted: [-1000, -900] → median = (-1000 + -900) / 2 = -950
        val slots =
            listOf(
                slot(baseDate, BigDecimal("-900.00"), 1),
                slot(baseDate.plusDays(30), BigDecimal("-1000.00"), 2),
            )
        val s = series(expectedSlots = slots)
        val today = baseDate.plusDays(70)

        val item = s.toItem()
        val missedSlots = item.expectedSlots.filter { !it.matched }

        assertThat(missedSlots).isNotEmpty()
        assertThat(missedSlots.first().predictedAmount).isEqualByComparingTo(BigDecimal("-950.00"))
    }

    @Test
    fun `income amounts produce positive prediction`() {
        val slots =
            listOf(
                slot(baseDate, BigDecimal("2800.00"), 1),
                slot(baseDate.plusDays(30), BigDecimal("3000.00"), 2),
                slot(baseDate.plusDays(60), BigDecimal("2900.00"), 3),
            )
        val s = series(expectedSlots = slots)
        val today = baseDate.plusDays(100)

        val item = s.toItem()
        val missedSlots = item.expectedSlots.filter { !it.matched }

        assertThat(missedSlots).isNotEmpty()
        assertThat(missedSlots.first().predictedAmount).isEqualByComparingTo(BigDecimal("2900.00"))
        assertThat(missedSlots.first().predictedAmountMin).isEqualByComparingTo(BigDecimal("2800.00"))
        assertThat(missedSlots.first().predictedAmountMax).isEqualByComparingTo(BigDecimal("3000.00"))
    }
}
