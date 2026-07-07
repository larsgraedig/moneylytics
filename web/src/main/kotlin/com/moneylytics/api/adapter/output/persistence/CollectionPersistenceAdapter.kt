package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.CollectionRepository
import com.moneylytics.api.domain.Collection
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CollectionPersistenceAdapter(
    private val collectionJpaRepository: CollectionJpaRepository,
    private val collectionTransactionJpaRepository: CollectionTransactionJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : CollectionRepository {
    override fun findAllByUserId(userId: Long): List<Collection> = collectionJpaRepository.findByUserId(userId).map { it.toDomain() }

    override fun findTransactionIdsByCollectionId(
        collectionId: Long,
        userId: Long,
    ): List<Long> = collectionTransactionJpaRepository.findTransactionIdsByCollectionId(collectionId)

    @Transactional
    override fun create(
        collection: Collection,
        userId: Long,
    ): Collection =
        collectionJpaRepository
            .save(
                CollectionEntity(
                    user = userJpaRepository.getReferenceById(userId),
                    name = collection.name,
                    note = collection.note,
                ),
            ).toDomain()

    @Transactional
    override fun update(
        collection: Collection,
        userId: Long,
    ): Collection {
        val entity = collectionJpaRepository.findByUserId(userId).first { it.id == collection.id }
        entity.name = collection.name
        entity.note = collection.note
        return collectionJpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    ) = collectionJpaRepository.deleteByIdAndUserId(id, userId)

    @Transactional
    override fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
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
        userId: Long,
    ) = collectionTransactionJpaRepository.deleteByCollectionIdAndTransactionId(collectionId, transactionId)

    private fun CollectionEntity.toDomain() =
        Collection(
            id = id,
            name = name,
            note = note,
        )
}
