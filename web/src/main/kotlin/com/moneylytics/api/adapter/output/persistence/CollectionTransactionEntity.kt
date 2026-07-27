package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "collection_transaction")
@IdClass(CollectionTransactionEntity.PK::class)
class CollectionTransactionEntity(
    @Id
    @Column(name = "collection_id")
    val collectionId: Long,
    @Id
    @Column(name = "transaction_id")
    val transactionId: Long,
) {
    data class PK(
        val collectionId: Long = 0,
        val transactionId: Long = 0,
    ) : Serializable
}
