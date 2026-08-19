package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.domain.StripeCustomer
import com.moneylytics.api.domain.SubscriptionStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

internal fun StripeCustomerEntity.toDomain() =
    StripeCustomer(
        id = id!!,
        userId = userId,
        stripeCustomerId = stripeCustomerId,
        stripeSubscriptionId = stripeSubscriptionId,
        subscriptionStatus = subscriptionStatus?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() },
        currentPeriodEnd = currentPeriodEnd,
        priceId = priceId,
    )

@Component
class StripeCustomerPersistenceAdapter(
    private val jpaRepository: StripeCustomerJpaRepository,
) : StripeCustomerRepository {
    override fun findByUserId(userId: Long): StripeCustomer? = jpaRepository.findByUserId(userId)?.toDomain()

    override fun findByStripeCustomerId(stripeCustomerId: String): StripeCustomer? =
        jpaRepository.findByStripeCustomerId(stripeCustomerId)?.toDomain()

    override fun save(stripeCustomer: StripeCustomer): StripeCustomer {
        val entity =
            StripeCustomerEntity(
                userId = stripeCustomer.userId,
                stripeCustomerId = stripeCustomer.stripeCustomerId,
                stripeSubscriptionId = stripeCustomer.stripeSubscriptionId,
                subscriptionStatus = stripeCustomer.subscriptionStatus?.name,
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
        currentPeriodEnd: Long?,
        priceId: String?,
    ) {
        val entity = jpaRepository.findByStripeCustomerId(stripeCustomerId) ?: return
        entity.stripeSubscriptionId = subscriptionId
        entity.subscriptionStatus = status.name
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
