package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CancelSubscriptionUseCase
import com.moneylytics.api.application.port.input.CreateSubscriptionUseCase
import com.moneylytics.api.application.port.input.GetSubscriptionStatusUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.config.StripeProperties
import com.moneylytics.api.domain.BillingInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/subscriptions")
class SubscriptionController(
    private val resolveUserUseCase: ResolveUserUseCase,
    private val createSubscriptionUseCase: CreateSubscriptionUseCase,
    private val cancelSubscriptionUseCase: CancelSubscriptionUseCase,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val stripeProperties: StripeProperties,
) {
    @GetMapping("/config")
    fun getConfig(): StripeConfigResponse = StripeConfigResponse(publishableKey = stripeProperties.publishableKey)

    @PostMapping
    suspend fun createSubscription(
        @RequestBody request: CreateSubscriptionRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): SubscriptionSetupResponse {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val interval = BillingInterval.valueOf(request.interval.uppercase())
        val result = withContext(Dispatchers.IO) { createSubscriptionUseCase.createSubscription(userId, interval) }
        return SubscriptionSetupResponse(clientSecret = result.clientSecret, subscriptionId = result.subscriptionId)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun cancelSubscription(
        @AuthenticationPrincipal principal: UserDetails,
    ) {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        withContext(Dispatchers.IO) { cancelSubscriptionUseCase.cancelSubscription(userId) }
    }

    @GetMapping("/status")
    suspend fun getStatus(
        @AuthenticationPrincipal principal: UserDetails,
    ): SubscriptionStatusResponse {
        val userId = withContext(Dispatchers.IO) { resolveUserUseCase.resolveUser(principal.username) }
        val stripeCustomer = withContext(Dispatchers.IO) { getSubscriptionStatusUseCase.getSubscriptionStatus(userId) }
        return SubscriptionStatusResponse(
            status = stripeCustomer?.subscriptionStatus?.name,
            currentPeriodEnd = stripeCustomer?.currentPeriodEnd,
            interval = stripeCustomer?.priceId?.let { resolveInterval(it, stripeProperties) },
        )
    }
}

private fun resolveInterval(
    priceId: String,
    props: StripeProperties,
): String? =
    when (priceId) {
        props.priceIdMonthly -> "MONTHLY"
        props.priceIdYearly -> "YEARLY"
        else -> null
    }

data class StripeConfigResponse(
    val publishableKey: String,
)

data class CreateSubscriptionRequest(
    val interval: String,
)

data class SubscriptionSetupResponse(
    val clientSecret: String,
    val subscriptionId: String,
)

data class SubscriptionStatusResponse(
    val status: String?,
    val currentPeriodEnd: Long?,
    val interval: String?,
)
