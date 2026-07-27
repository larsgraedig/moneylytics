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
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "accounts",
    uniqueConstraints = [UniqueConstraint(columnNames = ["iban", "organization_id"])],
)
class AccountEntity(
    @Column(nullable = false)
    val iban: String,
    @Column(nullable = false)
    val name: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    val organization: OrganizationEntity,
    @Column(precision = 19, scale = 4)
    var balance: BigDecimal? = null,
    @Column(name = "balance_date")
    var balanceDate: LocalDate? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
