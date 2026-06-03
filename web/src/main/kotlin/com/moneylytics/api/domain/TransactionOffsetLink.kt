package com.moneylytics.api.domain

import java.math.BigDecimal

data class TransactionOffsetLink(
    val id: Long,
    val linkedTransactionId: Long,
    val linkedTransactionAmount: BigDecimal,
    val partialAmount: BigDecimal?,
) {
    private val offsetAmount: BigDecimal
        get() = partialAmount ?: linkedTransactionAmount.abs()

    val contribution: BigDecimal
        get() = if (linkedTransactionAmount >= BigDecimal.ZERO) offsetAmount else -offsetAmount
}
