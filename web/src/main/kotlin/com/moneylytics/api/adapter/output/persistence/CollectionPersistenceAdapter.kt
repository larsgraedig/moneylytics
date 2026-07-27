package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CollectionRepository
import com.moneylytics.api.domain.Collection
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CollectionPersistenceAdapter(
    private val collectionJpaRepository: CollectionJpaRepository,
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository,
    private val organizationJpaRepository: OrganizationJpaRepository,
) : CollectionRepository {
    override fun findAllByOrganizationId(organizationId: Long): List<Collection> =
        collectionJpaRepository.findByOrganizationId(organizationId).map { it.toDomain() }

    override fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): Collection? = collectionJpaRepository.findByIdAndOrganizationId(id, organizationId)?.toDomain()

    override fun findTransactionIdsByCollectionId(
        collectionId: Long,
        organizationId: Long,
    ): List<Long> = collectionTransactionJpaRepository.findTransactionIdsByCollectionId(collectionId)

    @Transactional
    override fun create(
        collection: Collection,
        organizationId: Long,
    ): Collection =
        collectionJpaRepository
            .save(
                CollectionEntity(
                    organization = organizationJpaRepository.getReferenceById(organizationId),
                    name = collection.name,
                    note = collection.note,
                ),
            ).toDomain()

    @Transactional
    override fun update(
        collection: Collection,
        organizationId: Long,
    ): Collection {
        val entity = collectionJpaRepository.findByOrganizationId(organizationId).first { it.id == collection.id }
        entity.name = collection.name
        entity.note = collection.note
        return collectionJpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ) = collectionJpaRepository.deleteByIdAndOrganizationId(id, organizationId)

    @Transactional
    override fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        organizationId: Long,
    ) {
        if (!collectionTransactionJpaRepository.existsByCollectionIdAndTransactionId(collectionId, transactionId)) {
            collectionTransactionJpaRepository.save(
                CollectionTransactionEntity(collectionId = collectionId, transactionId = transactionId),
            )
        }
    }

    @Transactional
    override fun removeTransaction(
        collectionId: Long,
        transactionId: Long,
        organizationId: Long,
    ) = collectionTransactionJpaRepository.deleteByCollectionIdAndTransactionId(collectionId, transactionId)

    private fun CollectionEntity.toDomain() =
        Collection(
            id = id,
            name = name,
            note = note,
        )
}
