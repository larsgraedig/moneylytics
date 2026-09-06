package com.moneylytics.api.application.service

import com.moneylytics.api.adapter.output.persistence.AbstractServiceIT
import com.moneylytics.api.adapter.output.persistence.RecurringExpectedOccurrenceJpaRepository
import com.moneylytics.api.adapter.output.persistence.RecurringSeriesJpaRepository
import com.moneylytics.api.adapter.output.persistence.TransactionEntity
import com.moneylytics.api.adapter.output.persistence.TransactionJpaRepository
import com.moneylytics.api.application.port.input.ConfirmRecurringSeriesCommand
import com.moneylytics.api.application.port.input.DeleteRecurringSeriesCommand
import com.moneylytics.api.application.port.input.GetRecurringSeriesQuery
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.domain.RecurringOccurrenceDeviation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class RecurringMatcherServiceIT : AbstractServiceIT() {
    @Autowired private lateinit var recurringSeriesService: RecurringSeriesService

    @Autowired private lateinit var recurringMatcherService: RecurringMatcherService

    @Autowired private lateinit var transactionJpaRepository: TransactionJpaRepository

    @Autowired private lateinit var recurringSeriesJpaRepository: RecurringSeriesJpaRepository

    @Autowired private lateinit var recurringExpectedOccurrenceJpaRepository: RecurringExpectedOccurrenceJpaRepository

    private fun savedTransaction(
        bookingDate: LocalDate,
        amount: BigDecimal = BigDecimal("-15.99"),
    ) = transactionJpaRepository.save(
        TransactionEntity(
            bookingDate = bookingDate,
            valueDate = bookingDate,
            accountingDate = bookingDate,
            amount = amount,
            currency = "EUR",
            account = account,
            fingerprint = null,
            organization = organization,
            counterpartyName = "Netflix",
        ),
    )

    private fun confirmDetectedSeries(): Long {
        // Spread across three different calendar months (Jan/Feb/Mar) - the detector rejects
        // groups where more than 20% of periods contain multiple bookings in the same month.
        savedTransaction(LocalDate.of(2025, 1, 1))
        savedTransaction(LocalDate.of(2025, 2, 1))
        savedTransaction(LocalDate.of(2025, 3, 3))
        flushAndClear()

        val detected = recurringSeriesService.detect(RefreshRecurringSeriesCommand(organizationId = organizationId))
        val fp = detected.single { it.fingerprint.endsWith("|netflix") }.fingerprint

        recurringSeriesService.confirm(
            ConfirmRecurringSeriesCommand(
                organizationId = organizationId,
                confirmedFingerprints = listOf(fp),
                falsePositiveFingerprints = emptyList(),
            ),
        )
        flushAndClear()

        return checkNotNull(recurringSeriesJpaRepository.findByOrganizationId(organizationId).single { it.fingerprint == fp }.id)
    }

    @Test
    fun `should seed a pending occurrence on confirm then match a new transaction and create the next one`() {
        val seriesId = confirmDetectedSeries()

        val pendingBeforeSync = recurringExpectedOccurrenceJpaRepository.findBySeriesIdAndMatchedTransactionIdIsNull(seriesId)
        assertThat(pendingBeforeSync).isNotNull
        assertThat(pendingBeforeSync?.expectedDate).isEqualTo(LocalDate.of(2025, 4, 2))

        val newTx = savedTransaction(LocalDate.of(2025, 4, 2))
        flushAndClear()

        recurringMatcherService.syncForOrganization(organizationId)
        flushAndClear()

        val allOccurrences = recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(seriesId))
        val matched = allOccurrences.single { it.id == pendingBeforeSync?.id }
        assertThat(matched.matchedTransactionId).isEqualTo(newTx.id)
        assertThat(matched.matchedDate).isEqualTo(LocalDate.of(2025, 4, 2))

        val newPending = recurringExpectedOccurrenceJpaRepository.findBySeriesIdAndMatchedTransactionIdIsNull(seriesId)
        assertThat(newPending).isNotNull
        assertThat(newPending?.id).isNotEqualTo(pendingBeforeSync?.id)
    }

    @Test
    fun `should pair multiple new transactions to sequential occurrences in chronological order`() {
        val seriesId = confirmDetectedSeries()

        savedTransaction(LocalDate.of(2025, 4, 2))
        savedTransaction(LocalDate.of(2025, 5, 2))
        flushAndClear()

        recurringMatcherService.syncForOrganization(organizationId)
        flushAndClear()

        val matched =
            recurringExpectedOccurrenceJpaRepository
                .findBySeriesIdIn(listOf(seriesId))
                .filter { it.matchedTransactionId != null }
                .sortedBy { it.matchedDate }

        assertThat(matched).hasSize(2)
        assertThat(matched.map { it.matchedDate }).containsExactly(LocalDate.of(2025, 4, 2), LocalDate.of(2025, 5, 2))
        assertThat(recurringExpectedOccurrenceJpaRepository.findBySeriesIdAndMatchedTransactionIdIsNull(seriesId)).isNotNull
    }

    @Test
    fun `should surface a date-shifted deviation for a matched transaction booked well outside its expected date`() {
        val seriesId = confirmDetectedSeries()

        savedTransaction(LocalDate.of(2025, 4, 13))
        flushAndClear()

        recurringMatcherService.syncForOrganization(organizationId)
        flushAndClear()

        val result = recurringSeriesService.getRecurringSeries(GetRecurringSeriesQuery(organizationId = organizationId))
        val series = result.single { it.id == seriesId }
        val shiftedOccurrence = series.occurrences.single { it.date == LocalDate.of(2025, 4, 13) }

        assertThat(shiftedOccurrence.expectedDate).isEqualTo(LocalDate.of(2025, 4, 2))
        assertThat(shiftedOccurrence.deviation).isEqualTo(RecurringOccurrenceDeviation.DATE_SHIFTED)
    }

    @Test
    fun `should remove expected occurrence rows when deleting a series`() {
        val seriesId = confirmDetectedSeries()
        assertThat(recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(seriesId))).isNotEmpty

        recurringSeriesService.delete(DeleteRecurringSeriesCommand(organizationId = organizationId, seriesId = seriesId))
        flushAndClear()

        assertThat(recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(seriesId))).isEmpty()
    }

    @Test
    fun `should preserve matched history and refresh the pending row when re-confirming`() {
        val originalSeriesId = confirmDetectedSeries()
        val newTx = savedTransaction(LocalDate.of(2025, 4, 2))
        flushAndClear()
        recurringMatcherService.syncForOrganization(organizationId)
        flushAndClear()

        val detected = recurringSeriesService.detect(RefreshRecurringSeriesCommand(organizationId = organizationId))
        val fp = detected.single { it.fingerprint.endsWith("|netflix") }.fingerprint
        recurringSeriesService.confirm(
            ConfirmRecurringSeriesCommand(
                organizationId = organizationId,
                confirmedFingerprints = listOf(fp),
                falsePositiveFingerprints = emptyList(),
            ),
        )
        flushAndClear()

        val newSeriesId =
            checkNotNull(recurringSeriesJpaRepository.findByOrganizationId(organizationId).single { it.fingerprint == fp }.id)
        assertThat(newSeriesId).isNotEqualTo(originalSeriesId)

        val occurrencesAfterReconfirm = recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(newSeriesId))
        val matchedHistory = occurrencesAfterReconfirm.filter { it.matchedTransactionId != null }
        val pending = occurrencesAfterReconfirm.filter { it.matchedTransactionId == null }

        assertThat(matchedHistory).hasSize(1)
        assertThat(matchedHistory[0].matchedTransactionId).isEqualTo(newTx.id)
        assertThat(pending).hasSize(1)
        assertThat(recurringExpectedOccurrenceJpaRepository.findBySeriesIdIn(listOf(originalSeriesId))).isEmpty()
    }
}
