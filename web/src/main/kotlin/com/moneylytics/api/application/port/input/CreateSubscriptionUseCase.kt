package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.BillingInterval
import com.moneylytics.api.domain.SubscriptionSetupResult

interface CreateSubscriptionUseCase {
    fun createSubscription(
        userId: Long,
        interval: BillingInterval,
    ): SubscriptionSetupResult
}
