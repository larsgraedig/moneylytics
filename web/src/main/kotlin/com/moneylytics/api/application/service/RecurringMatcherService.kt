package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetRecurringSyncLogUseCase
import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.RecurringSyncLogRepository
import com.moneylytics.api.application.port.output.TransactionRepository
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
) : SyncRecurringSeriesUseCase,
    GetRecurringSyncLogUseCase {
    private data class SeriesMatchResult(
        val seriesId: Long,
        val seriesLabel: String,
        val newTransactions: List<Transaction>,
    )

    override fun syncForAllUsers() {
        val userIds = recurringSeriesRepository.findAllUserIds()
        userIds.forEach { userId -> syncAndLogForUser(userId) }
    }

    override fun getRecentSyncLogs(userId: Long): List<RecurringSyncLog> = syncLogRepository.findRecentByUserId(userId)

    private fun syncAndLogForUser(userId: Long) {
        val results = syncForUser(userId)
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
                triggeredBy = RecurringSyncTrigger.SCHEDULED,
                seriesUpdatedCount = results.size,
                transactionsLinkedCount = entries.size,
                entries = entries,
            ),
            userId,
        )
    }

    private fun syncForUser(userId: Long): List<SeriesMatchResult> {
        val today = LocalDate.now()
        val allSeries = recurringSeriesRepository.findByUserId(userId)
        val results = mutableListOf<SeriesMatchResult>()

        allSeries
            .filter { series -> !series.fingerprint.startsWith("manual:") }
            .forEach { series ->
                val seriesId = series.id ?: return@forEach
                val parts = series.fingerprint.split("|")
                if (parts.size != 3) return@forEach
                val accountIban = parts[0]

                val existingTransactionIds = recurringSeriesRepository.findMemberTransactionIds(seriesId)

                val candidates =
                    transactionRepository.findByAccountingDateBetween(
                        from = series.firstSeen,
                        to = today,
                        userId = userId,
                        accountIban = accountIban,
                    )

                val newMatches =
                    candidates.filter { tx ->
                        tx.id != null &&
                            tx.id !in existingTransactionIds &&
                            tx.bookingDate > series.lastSeen &&
                            groupKey(tx) == series.fingerprint
                    }

                if (newMatches.isNotEmpty()) {
                    val newIds = newMatches.mapNotNull { it.id }
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
            }

        return results
    }
}
