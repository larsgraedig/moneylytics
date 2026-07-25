package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringSyncLogRepository
import com.moneylytics.api.domain.RecurringSyncLog
import com.moneylytics.api.domain.RecurringSyncLogEntry
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RecurringSyncLogPersistenceAdapter(
    private val syncLogJpaRepository: RecurringSyncLogJpaRepository,
    private val syncLogEntryJpaRepository: RecurringSyncLogEntryJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : RecurringSyncLogRepository {
    @Transactional
    override fun save(
        log: RecurringSyncLog,
        userId: Long,
    ) {
        val user = userJpaRepository.getReferenceById(userId)
        val entity =
            syncLogJpaRepository.save(
                RecurringSyncLogEntity(
                    user = user,
                    ranAt = log.ranAt,
                    triggeredBy = log.triggeredBy,
                    seriesUpdatedCount = log.seriesUpdatedCount,
                    transactionsLinkedCount = log.transactionsLinkedCount,
                ),
            )
        val logId = requireNotNull(entity.id)
        if (log.entries.isNotEmpty()) {
            syncLogEntryJpaRepository.saveAll(
                log.entries.map { e ->
                    RecurringSyncLogEntryEntity(
                        logId = logId,
                        seriesId = e.seriesId,
                        seriesLabel = e.seriesLabel,
                        transactionId = e.transactionId,
                        bookingDate = e.bookingDate,
                        amount = e.amount,
                        counterpartyName = e.counterpartyName,
                    )
                },
            )
        }
    }

    override fun findRecentByUserId(
        userId: Long,
        limit: Int,
    ): List<RecurringSyncLog> {
        val entities =
            syncLogJpaRepository.findByUserIdOrderByRanAtDesc(userId, PageRequest.of(0, limit))
        if (entities.isEmpty()) return emptyList()

        val ids = entities.mapNotNull { it.id }
        val entriesByLogId = syncLogEntryJpaRepository.findByLogIdIn(ids).groupBy { it.logId }

        return entities.map { e ->
            RecurringSyncLog(
                id = e.id,
                ranAt = e.ranAt,
                triggeredBy = e.triggeredBy,
                seriesUpdatedCount = e.seriesUpdatedCount,
                transactionsLinkedCount = e.transactionsLinkedCount,
                entries =
                    (entriesByLogId[e.id] ?: emptyList()).map { entry ->
                        RecurringSyncLogEntry(
                            seriesId = entry.seriesId,
                            seriesLabel = entry.seriesLabel,
                            transactionId = entry.transactionId,
                            bookingDate = entry.bookingDate,
                            amount = entry.amount,
                            counterpartyName = entry.counterpartyName,
                        )
                    },
            )
        }
    }
}
