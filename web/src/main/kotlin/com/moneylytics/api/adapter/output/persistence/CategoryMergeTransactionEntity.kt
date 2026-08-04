package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

data class CategoryMergeTransactionId(
    val mergeId: Long = 0,
    val transactionId: Long = 0,
) : Serializable

@Entity
@Table(name = "category_merge_transaction")
@IdClass(CategoryMergeTransactionId::class)
class CategoryMergeTransactionEntity(
    @Id
    @Column(name = "merge_id")
    val mergeId: Long,
    @Id
    @Column(name = "transaction_id")
    val transactionId: Long,
)
