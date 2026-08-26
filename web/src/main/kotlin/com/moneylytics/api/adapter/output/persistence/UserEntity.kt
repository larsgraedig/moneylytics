package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.domain.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "`user`")
class UserEntity(
    @Column(nullable = false, unique = true)
    val externalId: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    var tier: CustomerTierEntity,
    @Column(nullable = true)
    var passwordHash: String? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "default_account_id", nullable = true)
    var defaultAccount: AccountEntity? = null,
    @Column(nullable = true, length = 10)
    var language: String? = null,
    @Column(nullable = true, length = 500)
    var transactionsColumnOrder: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.USER,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
