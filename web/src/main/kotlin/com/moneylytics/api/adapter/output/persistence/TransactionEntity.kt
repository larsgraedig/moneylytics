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
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "transactions")
class TransactionEntity(
    @Column(nullable = false)
    val category: String,
    @Column(nullable = false)
    val subcategory: String,
    @Column(nullable = false)
    val bookingDate: LocalDate,
    @Column(nullable = false)
    val valueDate: LocalDate,
    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,
    @Column(nullable = false, length = 3)
    val currency: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: AccountEntity,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
