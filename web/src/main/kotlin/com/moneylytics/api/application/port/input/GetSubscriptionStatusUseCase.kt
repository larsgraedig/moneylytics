package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.StripeCustomer

interface GetSubscriptionStatusUseCase {
    fun getSubscriptionStatus(userId: Long): StripeCustomer?
}
