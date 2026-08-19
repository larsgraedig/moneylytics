package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.HandleStripeWebhookUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks")
class StripeWebhookController(
    private val handleStripeWebhookUseCase: HandleStripeWebhookUseCase,
) {
    @PostMapping("/stripe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun handleStripeWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
    ) = withContext(Dispatchers.IO) {
        handleStripeWebhookUseCase.handle(payload, signature)
    }
}
