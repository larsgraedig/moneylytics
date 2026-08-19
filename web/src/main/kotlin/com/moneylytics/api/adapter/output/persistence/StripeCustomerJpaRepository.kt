package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface StripeCustomerJpaRepository : JpaRepository<StripeCustomerEntity, Long> {
    fun findByUserId(userId: Long): StripeCustomerEntity?

    fun findByStripeCustomerId(stripeCustomerId: String): StripeCustomerEntity?
}
