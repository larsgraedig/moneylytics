package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.domain.StripeCustomer
import com.moneylytics.api.domain.SubscriptionStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

internal fun CustomerStripeEntity.toDomain() =
    StripeCustomer(
        id = id!!,
        userId = userId,
        stripeCustomerId = stripeCustomerId,
        stripeSubscriptionId = stripeSubscriptionId,
        subscriptionStatus = subscriptionStatus?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() },
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        priceId = priceId,
    )

@Component
class CustomerStripePersistenceAdapter(
    private val jpaRepository: CustomerStripeJpaRepository,
) : StripeCustomerRepository {
    override fun findByUserId(userId: Long): StripeCustomer? = jpaRepository.findByUserId(userId)?.toDomain()

    override fun findByStripeCustomerId(stripeCustomerId: String): StripeCustomer? =
        jpaRepository.findByStripeCustomerId(stripeCustomerId)?.toDomain()

    override fun save(stripeCustomer: StripeCustomer): StripeCustomer {
        val entity =
            CustomerStripeEntity(
                userId = stripeCustomer.userId,
                stripeCustomerId = stripeCustomer.stripeCustomerId,
                stripeSubscriptionId = stripeCustomer.stripeSubscriptionId,
                subscriptionStatus = stripeCustomer.subscriptionStatus?.name,
                currentPeriodStart = stripeCustomer.currentPeriodStart,
                currentPeriodEnd = stripeCustomer.currentPeriodEnd,
                priceId = stripeCustomer.priceId,
            )
        return jpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun updateSubscription(
        stripeCustomerId: String,
        subscriptionId: String,
        status: SubscriptionStatus,
        currentPeriodStart: Long?,
        currentPeriodEnd: Long?,
        priceId: String?,
    ) {
        val entity = jpaRepository.findByStripeCustomerId(stripeCustomerId) ?: return
        entity.stripeSubscriptionId = subscriptionId
        entity.subscriptionStatus = status.name
        entity.currentPeriodStart = currentPeriodStart
        entity.currentPeriodEnd = currentPeriodEnd
        entity.priceId = priceId
        jpaRepository.save(entity)
    }

    @Transactional
    override fun updateStatus(
        stripeCustomerId: String,
        status: SubscriptionStatus,
    ) {
        val entity = jpaRepository.findByStripeCustomerId(stripeCustomerId) ?: return
        entity.subscriptionStatus = status.name
        jpaRepository.save(entity)
    }
}
