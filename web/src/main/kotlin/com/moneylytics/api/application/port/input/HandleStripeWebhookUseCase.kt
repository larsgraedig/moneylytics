package com.moneylytics.api.application.port.input

interface HandleStripeWebhookUseCase {
    fun handle(
        payload: String,
        signature: String,
    )
}
