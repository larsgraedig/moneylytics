package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.RecurringExpectedSlotRepository
import com.moneylytics.api.application.port.output.RecurringSeriesRepository
import com.moneylytics.api.domain.RecurrenceStatus
import com.moneylytics.api.domain.RecurringExpectedSlot
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
    private val recurringExpectedSlotJpaRepository: RecurringExpectedSlotJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val transactionJpaRepository: TransactionJpaRepository,
) : RecurringSeriesRepository,
    RecurringExpectedSlotRepository {
    @Transactional
    override fun replaceAllForOrganization(
        series: List<RecurringSeries>,
        organizationId: Long,
    ) {
        val detected = recurringSeriesJpaRepository.findByOrganizationId(organizationId).filter { it.status == RecurrenceStatus.DETECTED }
        if (detected.isNotEmpty()) {
            val detectedIds = detected.mapNotNull { it.id }
            recurringExpectedSlotJpaRepository.deleteBySeriesIdIn(detectedIds)
            recurringSeriesMemberJpaRepository.deleteBySeriesIdIn(detectedIds)
            recurringSeriesJpaRepository.deleteByOrganizationIdAndStatus(organizationId, RecurrenceStatus.DETECTED)
        }

        val organization = organizationJpaRepository.getReferenceById(organizationId)
        series.forEach { s -> persistSeries(s, organization) }
    }

    @Transactional
    override fun save(
        series: RecurringSeries,
        organizationId: Long,
    ): RecurringSeries {
        val organization = organizationJpaRepository.getReferenceById(organizationId)
        val entity = persistSeries(series, organization)
        return entity.toDomain(emptyList(), emptyList(), emptyMap())
    }

    @Transactional
    override fun deleteByIdAndOrganizationId(
        seriesId: Long,
        organizationId: Long,
    ) {
        val entity =
            recurringSeriesJpaRepository.findByIdAndOrganizationId(seriesId, organizationId)
                ?: throw NoSuchElementException("Series $seriesId not found for organization $organizationId")
        val id = requireNotNull(entity.id)
        recurringExpectedSlotJpaRepository.deleteBySeriesIdIn(listOf(id))
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

    override fun findByOrganizationId(organizationId: Long): List<RecurringSeries> {
        val entities = recurringSeriesJpaRepository.findByOrganizationId(organizationId)
        if (entities.isEmpty()) return emptyList()

        val entityIds = entities.mapNotNull { it.id }
        val members = recurringSeriesMemberJpaRepository.findBySeriesIdIn(entityIds)
        val membersBySeriesId = members.groupBy { it.seriesId }

        val slotEntities = recurringExpectedSlotJpaRepository.findBySeriesIdIn(entityIds)
        val slotsBySeriesId = slotEntities.groupBy { it.seriesId }

        val allTransactionIds = (members.map { it.transactionId } + slotEntities.map { it.transactionId }).distinct()
        val transactionsById =
            if (allTransactionIds.isEmpty()) {
                emptyMap()
            } else {
                transactionJpaRepository.findAllById(allTransactionIds).associateBy { requireNotNull(it.id) }
            }

        return entities.map { entity ->
            entity.toDomain(
                membersBySeriesId[entity.id] ?: emptyList(),
                slotsBySeriesId[entity.id] ?: emptyList(),
                transactionsById,
            )
        }
    }

    override fun findAllOrganizationIds(): Set<Long> = recurringSeriesJpaRepository.findDistinctOrganizationIds().toHashSet()

    @Transactional
    override fun replaceSlots(
        seriesId: Long,
        slots: List<RecurringExpectedSlot>,
    ) {
        recurringExpectedSlotJpaRepository.deleteBySeriesId(seriesId)
        if (slots.isNotEmpty()) {
            recurringExpectedSlotJpaRepository.saveAll(
                slots.map { slot ->
                    RecurringExpectedSlotEntity(
                        seriesId = seriesId,
                        expectedDate = slot.expectedDate,
                        transactionId = slot.transactionId,
                    )
                },
            )
        }
    }

    override fun findBySeriesIds(seriesIds: List<Long>): Map<Long, List<RecurringExpectedSlot>> {
        if (seriesIds.isEmpty()) return emptyMap()
        val slotEntities = recurringExpectedSlotJpaRepository.findBySeriesIdIn(seriesIds)
        if (slotEntities.isEmpty()) return emptyMap()
        val txIds = slotEntities.map { it.transactionId }.distinct()
        val txById = transactionJpaRepository.findAllById(txIds).associateBy { requireNotNull(it.id) }
        return slotEntities
            .groupBy { it.seriesId }
            .mapValues { (_, entities) ->
                entities.mapNotNull { e ->
                    val tx = txById[e.transactionId] ?: return@mapNotNull null
                    RecurringExpectedSlot(
                        expectedDate = e.expectedDate,
                        transactionId = e.transactionId,
                        date = tx.bookingDate,
                        amount = tx.amount,
                        counterpartyName = tx.counterpartyName,
                        purpose = tx.purpose,
                    )
                }
            }
    }

    @Transactional
    override fun deleteBySeriesIds(seriesIds: List<Long>) {
        if (seriesIds.isNotEmpty()) recurringExpectedSlotJpaRepository.deleteBySeriesIdIn(seriesIds)
    }

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
        organization: OrganizationEntity,
    ): RecurringSeriesEntity {
        val entity =
            recurringSeriesJpaRepository.save(
                RecurringSeriesEntity(
                    organization = organization,
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
        slotEntities: List<RecurringExpectedSlotEntity>,
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
            expectedSlots =
                slotEntities.mapNotNull { e ->
                    val tx = transactionsById[e.transactionId] ?: return@mapNotNull null
                    RecurringExpectedSlot(
                        expectedDate = e.expectedDate,
                        transactionId = e.transactionId,
                        date = tx.bookingDate,
                        amount = tx.amount,
                        counterpartyName = tx.counterpartyName,
                        purpose = tx.purpose,
                    )
                },
        )
}
