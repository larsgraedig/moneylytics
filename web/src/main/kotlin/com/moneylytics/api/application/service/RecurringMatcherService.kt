package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.SyncRecurringSeriesUseCase
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RecurringMatcherService(
    private val recurringSeriesRepository: RecurringSeriesRepository,
    private val transactionRepository: TransactionRepository,
) : SyncRecurringSeriesUseCase {
    override fun syncForAllUsers() {
        val userIds = recurringSeriesRepository.findAllUserIds()
        userIds.forEach { userId -> syncForUser(userId) }
    }

    private fun syncForUser(userId: Long) {
        val today = LocalDate.now()
        val allSeries = recurringSeriesRepository.findByUserId(userId)

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
                }
            }
    }
}
