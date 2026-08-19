package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceJpaRepository : JpaRepository<InvoiceEntity, Long> {
    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<InvoiceEntity>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): InvoiceEntity?
}
