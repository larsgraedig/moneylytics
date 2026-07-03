package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.math.BigDecimal

@Entity
@Table(
    name = "transaction_offsets",
    uniqueConstraints = [UniqueConstraint(columnNames = ["transaction_a_id", "transaction_b_id"])],
)
class TransactionOffsetEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_a_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val transactionA: TransactionEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_b_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val transactionB: TransactionEntity,
    @Column(precision = 19, scale = 4)
    val amount: BigDecimal? = null,
    @Column(name = "group_id")
    val groupId: Long? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
