package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "transaction_group_member")
@IdClass(TransactionGroupMemberEntity.PK::class)
class TransactionGroupMemberEntity(
    @Id
    @Column(name = "group_id")
    val groupId: Long,
    @Id
    @Column(name = "transaction_id")
    val transactionId: Long,
) {
    data class PK(
        val groupId: Long = 0,
        val transactionId: Long = 0,
    ) : Serializable
}
