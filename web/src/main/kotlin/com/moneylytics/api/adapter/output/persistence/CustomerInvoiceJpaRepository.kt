package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface CustomerInvoiceJpaRepository : JpaRepository<CustomerInvoiceEntity, Long> {
    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<CustomerInvoiceEntity>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): CustomerInvoiceEntity?

    fun findByStripeInvoiceId(stripeInvoiceId: String): CustomerInvoiceEntity?
}
