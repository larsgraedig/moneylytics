package com.moneylytics.api.adapter.output.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "stripe_customer")
class StripeCustomerEntity(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
    @Column(name = "stripe_customer_id", nullable = false, unique = true, length = 255)
    val stripeCustomerId: String,
    @Column(name = "stripe_subscription_id", nullable = true, length = 255)
    var stripeSubscriptionId: String? = null,
    @Column(name = "subscription_status", nullable = true, length = 50)
    var subscriptionStatus: String? = null,
    @Column(name = "current_period_start", nullable = true)
    var currentPeriodStart: Long? = null,
    @Column(name = "current_period_end", nullable = true)
    var currentPeriodEnd: Long? = null,
    @Column(name = "price_id", nullable = true, length = 255)
    var priceId: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
