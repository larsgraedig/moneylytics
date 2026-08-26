package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CollectionTransactionJpaRepository : JpaRepository<CollectionTransactionEntity, CollectionTransactionEntity.PK> {
    @Query("SELECT ct FROM CollectionTransactionEntity ct WHERE ct.transactionId IN :ids")
    fun findByTransactionIds(
        @Param("ids") ids: Collection<Long>,
    ): List<CollectionTransactionEntity>

    @Query("SELECT ct.transactionId FROM CollectionTransactionEntity ct WHERE ct.collectionId = :collectionId")
    fun findTransactionIdsByCollectionId(
        @Param("collectionId") collectionId: Long,
    ): List<Long>

    fun deleteByCollectionIdAndTransactionId(
        collectionId: Long,
        transactionId: Long,
    )

    fun existsByCollectionIdAndTransactionId(
        collectionId: Long,
        transactionId: Long,
    ): Boolean

    fun existsByTransactionId(transactionId: Long): Boolean
}
