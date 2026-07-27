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
    override fun getCollections(organizationId: Long): List<CollectionWithTransactions> =
        collectionRepository.findAllByOrganizationId(organizationId).map { collection ->
            buildWithTransactions(collection, organizationId)
        }

    override fun getCollection(
        id: Long,
        organizationId: Long,
    ): CollectionWithTransactions? {
        val collection = collectionRepository.findByIdAndOrganizationId(id, organizationId) ?: return null
        return buildWithTransactions(collection, organizationId)
    }

    private fun buildWithTransactions(
        collection: com.moneylytics.api.domain.Collection,
        organizationId: Long,
    ): CollectionWithTransactions {
        val txIds = collectionRepository.findTransactionIdsByCollectionId(requireNotNull(collection.id), organizationId)
        val transactions =
            if (txIds.isEmpty()) {
                emptyList()
            } else {
                transactionRepository.findByIdsAndOrganizationId(txIds.toSet(), organizationId)
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
        organizationId: Long,
    ): Collection = collectionRepository.create(collection, organizationId)

    override fun updateCollection(
        collection: Collection,
        organizationId: Long,
    ): Collection = collectionRepository.update(collection, organizationId)

    override fun deleteCollection(
        id: Long,
        organizationId: Long,
    ) = collectionRepository.deleteByIdAndOrganizationId(id, organizationId)

    override fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        organizationId: Long,
    ) = collectionRepository.addTransaction(collectionId, transactionId, organizationId)

    override fun removeTransaction(
        collectionId: Long,
        transactionId: Long,
        organizationId: Long,
    ) = collectionRepository.removeTransaction(collectionId, transactionId, organizationId)
}
