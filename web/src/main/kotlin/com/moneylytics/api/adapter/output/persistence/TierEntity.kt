package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "tier")
class TierEntity(
    @Column(nullable = false, unique = true, length = 100)
    val name: String,
    @Column(nullable = true, length = 500)
    val description: String? = null,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(nullable = false, name = "is_default")
    var isDefault: Boolean = false,
    @Column(nullable = true, unique = true, name = "stripe_price_id", length = 255)
    var stripePriceId: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
