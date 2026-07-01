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
    @Column(nullable = true)
    var category: String?,
    @Column(nullable = true)
    var subcategory: String?,
    @Column(nullable = true, name = "category_group")
    var categoryGroup: String? = null,
    @Column(nullable = false)
    val bookingDate: LocalDate,
    @Column(nullable = false)
    val valueDate: LocalDate,
    @Column(nullable = false, name = "accounting_date")
    var accountingDate: LocalDate,
    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,
    @Column(nullable = false, length = 3)
    val currency: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: AccountEntity,
    @Column(nullable = false, unique = true, length = 64)
    val fingerprint: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = true, length = 1000)
    var comment: String? = null,
    @Column(nullable = true, length = 2000)
    var purpose: String? = null,
    @Column(nullable = true, length = 255)
    var counterpartyName: String? = null,
    @Column(nullable = true, length = 34)
    var counterpartyIban: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
