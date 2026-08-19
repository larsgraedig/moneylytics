package com.moneylytics.api.application.port.output

import com.moneylytics.api.domain.StripeCustomer
import com.moneylytics.api.domain.SubscriptionStatus

interface StripeCustomerRepository {
    fun findByUserId(userId: Long): StripeCustomer?

    fun findByStripeCustomerId(stripeCustomerId: String): StripeCustomer?

    fun save(stripeCustomer: StripeCustomer): StripeCustomer

    fun updateSubscription(
        stripeCustomerId: String,
        subscriptionId: String,
        status: SubscriptionStatus,
        currentPeriodEnd: Long?,
        priceId: String?,
    )

    fun updateStatus(
        stripeCustomerId: String,
        status: SubscriptionStatus,
    )
}
