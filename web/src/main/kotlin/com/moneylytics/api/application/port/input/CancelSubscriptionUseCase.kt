package com.moneylytics.api.application.port.input

interface CancelSubscriptionUseCase {
    fun cancelSubscription(userId: Long)
}
