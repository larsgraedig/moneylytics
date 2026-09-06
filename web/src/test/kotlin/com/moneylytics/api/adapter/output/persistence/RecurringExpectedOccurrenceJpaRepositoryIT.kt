package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.RecurrenceCadence
import com.moneylytics.api.domain.RecurrenceDirection
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class RecurringExpectedOccurrenceJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var recurringSeriesRepo: RecurringSeriesJpaRepository

    @Autowired private lateinit var expectedOccurrenceRepo: RecurringExpectedOccurrenceJpaRepository

    private fun savedSeries(fingerprint: String = "DE01|E|netflix") =
        recurringSeriesRepo.save(
            RecurringSeriesEntity(
                organization = organization,
                label = "Netflix",
                type = RecurringType.SUBSCRIPTION,
                direction = RecurrenceDirection.EXPENSE,
                cadence = RecurrenceCadence.MONTHLY,
                intervalDays = 30,
                expectedAmount = BigDecimal("-15.99"),
                amountVariable = false,
                currency = "EUR",
                accountIban = account.iban,
                firstSeen = LocalDate.of(2025, 11, 1),
                lastSeen = LocalDate.of(2026, 1, 1),
                occurrenceCount = 3,
                nextExpectedDate = LocalDate.of(2026, 2, 1),
                status = RecurrenceStatus.DETECTED,
                fingerprint = fingerprint,
            ),
        )

    private fun savedOccurrence(
        seriesId: Long,
        expectedDate: LocalDate = LocalDate.of(2026, 2, 1),
        matchedTransactionId: Long? = null,
    ) = expectedOccurrenceRepo.save(
        RecurringExpectedOccurrenceEntity(
            seriesId = seriesId,
            expectedDate = expectedDate,
            expectedAmount = BigDecimal("-15.99"),
            matchedTransactionId = matchedTransactionId,
            matchedDate = if (matchedTransactionId != null) expectedDate else null,
            matchedAmount = if (matchedTransactionId != null) BigDecimal("-15.99") else null,
        ),
    )

    @Test
    fun `should find the single pending occurrence for a series`() {
        val series = checkNotNull(savedSeries().id)
        savedOccurrence(series, expectedDate = LocalDate.of(2026, 1, 1), matchedTransactionId = 1L)
        val pending = savedOccurrence(series, expectedDate = LocalDate.of(2026, 2, 1))
        flushAndClear()

        val result = expectedOccurrenceRepo.findBySeriesIdAndMatchedTransactionIdIsNull(series)

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(pending.id)
    }

    @Test
    fun `should return null when no pending occurrence exists`() {
        val series = checkNotNull(savedSeries().id)
        savedOccurrence(series, matchedTransactionId = 1L)
        flushAndClear()

        val result = expectedOccurrenceRepo.findBySeriesIdAndMatchedTransactionIdIsNull(series)

        assertThat(result).isNull()
    }

    @Test
    fun `should find all occurrences for the given series ids in one batch`() {
        val seriesA = checkNotNull(savedSeries(fingerprint = "DE01|E|netflix").id)
        val seriesB = checkNotNull(savedSeries(fingerprint = "DE01|E|spotify").id)
        val seriesC = checkNotNull(savedSeries(fingerprint = "DE01|E|other").id)
        savedOccurrence(seriesA)
        savedOccurrence(seriesB)
        savedOccurrence(seriesC)
        flushAndClear()

        val result = expectedOccurrenceRepo.findBySeriesIdIn(listOf(seriesA, seriesB))

        assertThat(result).hasSize(2)
        assertThat(result.map { it.seriesId }).containsExactlyInAnyOrder(seriesA, seriesB)
    }

    @Test
    fun `should delete occurrences only for the given series ids`() {
        val seriesA = checkNotNull(savedSeries(fingerprint = "DE01|E|netflix").id)
        val seriesB = checkNotNull(savedSeries(fingerprint = "DE01|E|spotify").id)
        savedOccurrence(seriesA)
        savedOccurrence(seriesB)
        flushAndClear()

        expectedOccurrenceRepo.deleteBySeriesIdIn(listOf(seriesA))
        flushAndClear()

        assertThat(expectedOccurrenceRepo.findBySeriesIdIn(listOf(seriesA))).isEmpty()
        assertThat(expectedOccurrenceRepo.findBySeriesIdIn(listOf(seriesB))).hasSize(1)
    }

    @Test
    fun `should persist matched fields when updating a pending occurrence`() {
        val series = checkNotNull(savedSeries().id)
        val pending = savedOccurrence(series)
        flushAndClear()

        val entity = expectedOccurrenceRepo.findById(checkNotNull(pending.id)).get()
        entity.matchedTransactionId = 42L
        entity.matchedDate = LocalDate.of(2026, 2, 3)
        entity.matchedAmount = BigDecimal("-15.99")
        expectedOccurrenceRepo.save(entity)
        flushAndClear()

        val updated = expectedOccurrenceRepo.findById(checkNotNull(pending.id)).get()
        assertThat(updated.matchedTransactionId).isEqualTo(42L)
        assertThat(updated.matchedDate).isEqualTo(LocalDate.of(2026, 2, 3))
        assertThat(updated.matchedAmount).isEqualByComparingTo(BigDecimal("-15.99"))
    }
}
