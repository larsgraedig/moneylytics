package com.moneylytics.api.domain

data class StripeCustomer(
    val id: Long,
    val userId: Long,
    val stripeCustomerId: String,
    val stripeSubscriptionId: String?,
    val subscriptionStatus: SubscriptionStatus?,
    val currentPeriodStart: Long?,
    val currentPeriodEnd: Long?,
    val priceId: String?,
)
