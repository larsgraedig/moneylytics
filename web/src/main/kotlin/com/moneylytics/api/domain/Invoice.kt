package com.moneylytics.api.domain

import java.time.LocalDateTime

data class Invoice(
    val id: Long,
    val userId: Long,
    val stripeInvoiceId: String?,
    val amountCents: Int,
    val currency: String,
    val status: String,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val hasPdf: Boolean,
    val issuedAt: LocalDateTime,
)
