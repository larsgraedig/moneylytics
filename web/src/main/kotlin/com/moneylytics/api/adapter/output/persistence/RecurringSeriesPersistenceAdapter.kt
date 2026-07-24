package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.domain.RecurringOccurrence
import com.moneylytics.api.domain.RecurringSeries
import com.moneylytics.api.domain.RecurringType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RecurringSeriesPersistenceAdapter(
    private val recurringSeriesJpaRepository: RecurringSeriesJpaRepository,
    private val recurringSeriesMemberJpaRepository: RecurringSeriesMemberJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : RecurringSeriesRepository {
    @Transactional
    override fun replaceAllForUser(
        series: List<RecurringSeries>,
        userId: Long,
    ) {
        val existing = recurringSeriesJpaRepository.findByUserId(userId)
        if (existing.isNotEmpty()) {
            val existingIds = existing.mapNotNull { it.id }
            recurringSeriesMemberJpaRepository.deleteBySeriesIdIn(existingIds)
            recurringSeriesJpaRepository.deleteByUserId(userId)
        }

        val user = userJpaRepository.getReferenceById(userId)
        series.forEach { s ->
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
            recurringSeriesMemberJpaRepository.saveAll(
                s.occurrences.map { o ->
                    RecurringSeriesMemberEntity(
                        seriesId = seriesId,
                        transactionId = o.transactionId,
                        occurredOn = o.date,
                        amount = o.amount,
                        purpose = o.purpose,
                        counterpartyName = o.counterpartyName,
                        counterpartyIban = o.counterpartyIban,
                    )
                },
            )
        }
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
        val membersBySeriesId = recurringSeriesMemberJpaRepository.findBySeriesIdIn(entityIds).groupBy { it.seriesId }

        return entities.map { entity -> entity.toDomain(membersBySeriesId[entity.id] ?: emptyList()) }
    }

    private fun RecurringSeriesEntity.toDomain(members: List<RecurringSeriesMemberEntity>): RecurringSeries =
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
                    .map { m ->
                        RecurringOccurrence(
                            transactionId = m.transactionId,
                            date = m.occurredOn,
                            amount = m.amount,
                            purpose = m.purpose,
                            counterpartyName = m.counterpartyName,
                            counterpartyIban = m.counterpartyIban,
                        )
                    }.sortedByDescending { it.date },
        )
}
