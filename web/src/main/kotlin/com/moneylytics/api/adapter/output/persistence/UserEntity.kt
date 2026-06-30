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

@Entity
@Table(name = "users")
class UserEntity(
    @Column(nullable = false, unique = true)
    val externalId: String,
    @Column(nullable = true)
    var passwordHash: String? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "default_account_id", nullable = true)
    var defaultAccount: AccountEntity? = null,
    @Column(nullable = true, length = 10)
    var language: String? = null,
    @Column(nullable = true, length = 500)
    var transactionsColumnOrder: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
