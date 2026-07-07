package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Collection

interface CollectionRepository {
    fun findAllByUserId(userId: Long): List<Collection>

    fun findTransactionIdsByCollectionId(
        collectionId: Long,
        userId: Long,
    ): List<Long>

    fun create(
        collection: Collection,
        userId: Long,
    ): Collection

    fun update(
        collection: Collection,
        userId: Long,
    ): Collection

    fun deleteByIdAndUserId(
        id: Long,
        userId: Long,
    )

    fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
    )

    fun removeTransaction(
        collectionId: Long,
        transactionId: Long,
        userId: Long,
    )
}
