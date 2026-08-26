package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CustomerStripeJpaRepository : JpaRepository<CustomerStripeEntity, Long> {
    fun findByUserId(userId: Long): CustomerStripeEntity?

    fun findByStripeCustomerId(stripeCustomerId: String): CustomerStripeEntity?
}
