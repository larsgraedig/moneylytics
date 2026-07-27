package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.Collection

interface CollectionRepository {
    fun findAllByOrganizationId(organizationId: Long): List<Collection>

    fun findByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    ): Collection?

    fun findTransactionIdsByCollectionId(
        collectionId: Long,
        organizationId: Long,
    ): List<Long>

    fun create(
        collection: Collection,
        organizationId: Long,
    ): Collection

    fun update(
        collection: Collection,
        organizationId: Long,
    ): Collection

    fun deleteByIdAndOrganizationId(
        id: Long,
        organizationId: Long,
    )

    fun addTransaction(
        collectionId: Long,
        transactionId: Long,
        organizationId: Long,
    )

    fun removeTransaction(
        collectionId: Long,
        transactionId: Long,
        organizationId: Long,
    )
}
