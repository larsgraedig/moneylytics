package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CancelSubscriptionUseCase
import com.moneylytics.api.application.port.input.CreateSubscriptionUseCase
import com.moneylytics.api.application.port.input.GetSubscriptionStatusUseCase
import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.application.port.output.StripeGateway
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.BillingInterval
import com.moneylytics.api.domain.StripeCustomer
import com.moneylytics.api.domain.SubscriptionSetupResult
import com.moneylytics.api.domain.SubscriptionStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class NoActiveSubscriptionException : RuntimeException("No active subscription found")

private val logger = KotlinLogging.logger {}

@Service
class SubscriptionService(
    private val stripeGateway: StripeGateway,
    private val stripeCustomerRepository: StripeCustomerRepository,
    private val userRepository: UserRepository,
) : CreateSubscriptionUseCase,
    CancelSubscriptionUseCase,
    GetSubscriptionStatusUseCase {
    @Transactional
    override fun createSubscription(
        userId: Long,
        interval: BillingInterval,
    ): SubscriptionSetupResult {
        val user = userRepository.findById(userId) ?: error("User $userId not found")

        val existing = stripeCustomerRepository.findByUserId(userId)
        val stripeCustomerId =
            if (existing != null) {
                existing.stripeCustomerId
            } else {
                val id = stripeGateway.createCustomer(user.externalId)
                stripeCustomerRepository.save(
                    StripeCustomer(
                        id = 0,
                        userId = userId,
                        stripeCustomerId = id,
                        stripeSubscriptionId = null,
                        subscriptionStatus = null,
                        currentPeriodStart = null,
                        currentPeriodEnd = null,
                        priceId = null,
                    ),
                )
                id
            }

        val result = stripeGateway.createSubscription(stripeCustomerId, interval)
        stripeCustomerRepository.updateSubscription(
            stripeCustomerId = stripeCustomerId,
            subscriptionId = result.subscriptionId,
            status = SubscriptionStatus.INCOMPLETE,
            currentPeriodStart = result.currentPeriodStart,
            currentPeriodEnd = result.currentPeriodEnd,
            priceId = result.priceId,
        )
        logger.info { "Created Stripe subscription ${result.subscriptionId} for user $userId" }
        return result
    }

    @Transactional
    override fun cancelSubscription(userId: Long) {
        val stripeCustomer =
            stripeCustomerRepository.findByUserId(userId)
                ?: throw NoActiveSubscriptionException()
        val subscriptionId =
            stripeCustomer.stripeSubscriptionId
                ?: throw NoActiveSubscriptionException()

        stripeGateway.cancelSubscription(subscriptionId)
        stripeCustomerRepository.updateStatus(stripeCustomer.stripeCustomerId, SubscriptionStatus.CANCELING)
        logger.info { "Cancelled subscription $subscriptionId for user $userId (at period end)" }
    }

    override fun getSubscriptionStatus(userId: Long): StripeCustomer? = stripeCustomerRepository.findByUserId(userId)
}
