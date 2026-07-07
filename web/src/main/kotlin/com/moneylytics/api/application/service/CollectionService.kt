package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CollectionWithTransactions
import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.DeleteCollectionUseCase
import com.moneylytics.api.application.port.input.GetCollectionsUseCase
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.UpdateCollectionUseCase
import com.moneylytics.api.application.port.output.CollectionRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Collection
import org.springframework.stereotype.Service

@Service
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val transactionRepository: TransactionRepository,
) : GetCollectionsUseCase,
    CreateCollectionUseCase,
    UpdateCollectionUseCase,
    DeleteCollectionUseCase,
    ManageCollectionMembersUseCase {
    override fun getCollections(userId: Long): List<CollectionWithTransactions> =
        collectionRepository.findAllByUserId(userId).map { collection ->
            buildWithTransactions(collection, userId)
        }

    override fun getCollection(
        id: Long,
        userId: Long,
    ): CollectionWithTransactions? {
        val collection = collectionRepository.findByIdAndUserId(id, userId) ?: return null
        return buildWithTransactions(collection, userId)
    }

    private fun buildWithTransactions(
        collection: com.moneylytics.api.domain.Collection,
        userId: Long,
    ): CollectionWithTransactions {
        val txIds = collectionRepository.findTransactionIdsByCollectionId(requireNotNull(collection.id), userId)
        val transactions =
            if (txIds.isEmpty()) {
                emptyList()
            } else {
                transactionRepository.findByIdsAndUserId(txIds.toSet(), userId)
            }
        return CollectionWithTransactions(
            id = requireNotNull(collection.id),
            name = collection.name,
            note = collection.note,
            transactions = transactions,
        )
    }

    override fun createCollection(
        collection: Collection,
        userId: Long,
    ): Collection = collectionRepository.create(collection, userId)

    override fun updateCollection(
        collection: Collection,
        userId: Long,
    ): Collection = collectionRepository.update(collection, userId)

    override fun deleteCollection(
        id: Long,
        userId: Long,
    ) = collectionRepository.deleteByIdAndUserId(id, userId)

    override fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
    ) = collectionRepository.addTransaction(collectionId, transactionId, userId)

    override fun removeTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
    ) = collectionRepository.removeTransaction(collectionId, transactionId, userId)
}
