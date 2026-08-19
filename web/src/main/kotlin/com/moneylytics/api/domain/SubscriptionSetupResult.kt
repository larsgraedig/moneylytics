package com.moneylytics.api.domain

data class SubscriptionSetupResult(
    val subscriptionId: String,
    val clientSecret: String,
    val currentPeriodEnd: Long?,
    val priceId: String?,
)
