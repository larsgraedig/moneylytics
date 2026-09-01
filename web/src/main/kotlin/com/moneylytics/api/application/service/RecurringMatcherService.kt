package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetRecurringSyncLogUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringSyncLogRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSyncLog
import com.moneylytics.api.domain.RecurringSyncLogEntry
import com.moneylytics.api.domain.RecurringSyncTrigger
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
class RecurringMatcherService(
    private val recurringSeriesRepository: RecurringSeriesRepository,
    private val transactionRepository: TransactionRepository,
    private val syncLogRepository: RecurringSyncLogRepository,
    private val accountRepository: AccountRepository,
    private val slotAssigner: RecurringSlotAssigner,
) : SyncRecurringSeriesUseCase,
    GetRecurringSyncLogUseCase {
    companion object {
        private const val FINGERPRINT_PART_COUNT = 3
    }

    private data class SeriesMatchResult(
        val seriesId: Long,
        val seriesLabel: String,
        val newTransactions: List<Transaction>,
    )

    override fun syncForAllOrganizations() {
        val organizationIds = recurringSeriesRepository.findAllOrganizationIds()
        organizationIds.forEach { organizationId -> syncAndLogForOrganization(organizationId) }
    }

    override fun syncForOrganization(organizationId: Long) = syncAndLogForOrganization(organizationId, RecurringSyncTrigger.MANUAL)

    override fun getRecentSyncLogs(organizationId: Long): List<RecurringSyncLog> =
        syncLogRepository.findRecentByOrganizationId(organizationId)

    private fun syncAndLogForOrganization(
        organizationId: Long,
        trigger: RecurringSyncTrigger = RecurringSyncTrigger.SCHEDULED,
    ) {
        val results = matchSeriesForOrganization(organizationId)
        val entries =
            results.flatMap { r ->
                r.newTransactions.mapNotNull { tx ->
                    tx.id?.let { txId ->
                        RecurringSyncLogEntry(
                            seriesId = r.seriesId,
                            seriesLabel = r.seriesLabel,
                            transactionId = txId,
                            bookingDate = tx.bookingDate,
                            amount = tx.amount,
                            counterpartyName = tx.counterpartyName,
                        )
                    }
                }
            }
        syncLogRepository.save(
            RecurringSyncLog(
                ranAt = Instant.now(),
                triggeredBy = trigger,
                seriesUpdatedCount = results.size,
                transactionsLinkedCount = entries.size,
                entries = entries,
            ),
            organizationId,
        )
    }

    private fun matchSeriesForOrganization(organizationId: Long): List<SeriesMatchResult> {
        val today = LocalDate.now()
        val allSeries = recurringSeriesRepository.findByOrganizationId(organizationId)
        val results = mutableListOf<SeriesMatchResult>()

        allSeries
            .filter { series -> !series.fingerprint.startsWith("manual:") }
            .forEach { series ->
                val seriesId = series.id ?: return@forEach
                val parts = series.fingerprint.split("|")
                if (parts.size != FINGERPRINT_PART_COUNT) return@forEach
                val accountIban = parts[0]
                val accountId = accountRepository.findByIban(accountIban, organizationId)?.id

                val existingTransactionIds = recurringSeriesRepository.findMemberTransactionIds(seriesId)

                val candidates =
                    transactionRepository.findByAccountingDateBetween(
                        from = series.firstSeen,
                        to = today,
                        organizationId = organizationId,
                        accountId = accountId,
                    )

                val newMatches =
                    candidates.filter { tx ->
                        tx.id != null &&
                            tx.id !in existingTransactionIds &&
                            tx.bookingDate > series.lastSeen &&
                            groupKey(tx) == series.fingerprint
                    }

                val newOccurrences =
                    newMatches.mapNotNull { tx ->
                        tx.id?.let { id ->
                            RecurringOccurrence(
                                transactionId = id,
                                date = tx.bookingDate,
                                amount = tx.amount,
                                counterpartyName = tx.counterpartyName,
                                counterpartyIban = tx.counterpartyIban,
                                purpose = tx.purpose,
                            )
                        }
                    }

                if (newMatches.isNotEmpty()) {
                    val newIds = newOccurrences.map { it.transactionId }
                    recurringSeriesRepository.addMembers(seriesId, newIds)
                    val newLastSeen = newMatches.maxOf { it.bookingDate }
                    recurringSeriesRepository.updateSeriesMetadata(
                        seriesId = seriesId,
                        lastSeen = newLastSeen,
                        nextExpectedDate = newLastSeen.plusDays(series.intervalDays.toLong()),
                        occurrenceCount = series.occurrenceCount + newIds.size,
                    )
                    results.add(SeriesMatchResult(seriesId, series.label, newMatches))
                }

                val seriesForSlots = series.copy(occurrences = series.occurrences + newOccurrences)
                slotAssigner.computeAndPersist(seriesForSlots, today)
            }

        return results
    }
}
