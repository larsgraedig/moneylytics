package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class RecurringSeriesPersistenceAdapter(
    private val recurringSeriesJpaRepository: RecurringSeriesJpaRepository,
    private val recurringSeriesMemberJpaRepository: RecurringSeriesMemberJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
) : RecurringSeriesRepository {
    @Transactional
    override fun replaceAllForUser(
        series: List<RecurringSeries>,
        userId: Long,
    ) {
        val detected = recurringSeriesJpaRepository.findByUserId(userId).filter { it.status == RecurrenceStatus.DETECTED }
        if (detected.isNotEmpty()) {
            val detectedIds = detected.mapNotNull { it.id }
            recurringSeriesMemberJpaRepository.deleteBySeriesIdIn(detectedIds)
            recurringSeriesJpaRepository.deleteByUserIdAndStatus(userId, RecurrenceStatus.DETECTED)
        }

        val user = userJpaRepository.getReferenceById(userId)
        series.forEach { s -> persistSeries(s, user) }
    }

    @Transactional
    override fun save(
        series: RecurringSeries,
        userId: Long,
    ): RecurringSeries {
        val user = userJpaRepository.getReferenceById(userId)
        val entity = persistSeries(series, user)
        return entity.toDomain(emptyList(), emptyMap())
    }

    @Transactional
    override fun deleteByIdAndUserId(
        seriesId: Long,
        userId: Long,
    ) {
        val entity =
            recurringSeriesJpaRepository.findByIdAndUserId(seriesId, userId)
                ?: throw NoSuchElementException("Series $seriesId not found for user $userId")
        val id = requireNotNull(entity.id)
        recurringSeriesMemberJpaRepository.deleteBySeriesIdIn(listOf(id))
        recurringSeriesJpaRepository.delete(entity)
    }

    @Transactional
    override fun updateType(
        seriesId: Long,
        type: RecurringType,
    ) {
        val entity = recurringSeriesJpaRepository.findById(seriesId).orElseThrow()
        entity.type = type
        recurringSeriesJpaRepository.save(entity)
    }

    override fun findByUserId(userId: Long): List<RecurringSeries> {
        val entities = recurringSeriesJpaRepository.findByUserId(userId)
        if (entities.isEmpty()) return emptyList()

        val entityIds = entities.mapNotNull { it.id }
        val members = recurringSeriesMemberJpaRepository.findBySeriesIdIn(entityIds)
        val membersBySeriesId = members.groupBy { it.seriesId }

        val allTransactionIds = members.map { it.transactionId }
        val transactionsById =
            if (allTransactionIds.isEmpty()) {
                emptyMap()
            } else {
                transactionJpaRepository.findAllById(allTransactionIds).associateBy { requireNotNull(it.id) }
            }

        return entities.map { entity -> entity.toDomain(membersBySeriesId[entity.id] ?: emptyList(), transactionsById) }
    }

    override fun findAllUserIds(): Set<Long> = recurringSeriesJpaRepository.findDistinctUserIds().toHashSet()

    override fun findMemberTransactionIds(seriesId: Long): Set<Long> =
        recurringSeriesMemberJpaRepository
            .findBySeriesIdIn(listOf(seriesId))
            .map { it.transactionId }
            .toHashSet()

    @Transactional
    override fun addMembers(
        seriesId: Long,
        transactionIds: List<Long>,
    ) {
        recurringSeriesMemberJpaRepository.saveAll(
            transactionIds.map { txId -> RecurringSeriesMemberEntity(seriesId = seriesId, transactionId = txId) },
        )
    }

    @Transactional
    override fun updateSeriesMetadata(
        seriesId: Long,
        lastSeen: LocalDate,
        nextExpectedDate: LocalDate,
        occurrenceCount: Int,
    ) {
        val entity = recurringSeriesJpaRepository.findById(seriesId).orElseThrow()
        entity.lastSeen = lastSeen
        entity.nextExpectedDate = nextExpectedDate
        entity.occurrenceCount = occurrenceCount
        recurringSeriesJpaRepository.save(entity)
    }

    private fun persistSeries(
        s: RecurringSeries,
        user: UserEntity,
    ): RecurringSeriesEntity {
        val entity =
            recurringSeriesJpaRepository.save(
                RecurringSeriesEntity(
                    user = user,
                    label = s.label,
                    type = s.type,
                    direction = s.direction,
                    cadence = s.cadence,
                    intervalDays = s.intervalDays,
                    expectedAmount = s.expectedAmount,
                    amountVariable = s.amountVariable,
                    currency = s.currency,
                    accountIban = s.accountIban,
                    firstSeen = s.firstSeen,
                    lastSeen = s.lastSeen,
                    occurrenceCount = s.occurrenceCount,
                    nextExpectedDate = s.nextExpectedDate,
                    status = s.status,
                    fingerprint = s.fingerprint,
                ),
            )
        val seriesId = requireNotNull(entity.id)
        if (s.occurrences.isNotEmpty()) {
            recurringSeriesMemberJpaRepository.saveAll(
                s.occurrences.map { o ->
                    RecurringSeriesMemberEntity(
                        seriesId = seriesId,
                        transactionId = o.transactionId,
                    )
                },
            )
        }
        return entity
    }

    private fun RecurringSeriesEntity.toDomain(
        members: List<RecurringSeriesMemberEntity>,
        transactionsById: Map<Long, TransactionEntity>,
    ): RecurringSeries =
        RecurringSeries(
            id = id,
            label = label,
            type = type,
            direction = direction,
            cadence = cadence,
            intervalDays = intervalDays,
            expectedAmount = expectedAmount,
            amountVariable = amountVariable,
            currency = currency,
            accountIban = accountIban,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            occurrenceCount = occurrenceCount,
            nextExpectedDate = nextExpectedDate,
            status = status,
            fingerprint = fingerprint,
            occurrences =
                members
                    .mapNotNull { m ->
                        val tx = transactionsById[m.transactionId] ?: return@mapNotNull null
                        RecurringOccurrence(
                            transactionId = m.transactionId,
                            date = tx.bookingDate,
                            amount = tx.amount,
                            purpose = tx.purpose,
                            counterpartyName = tx.counterpartyName,
                            counterpartyIban = tx.counterpartyIban,
                        )
                    }.sortedByDescending { it.date },
        )
}
